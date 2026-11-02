// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static com.opencop.core.sdk.model.SubjectType.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.event.ports.gateway.history.HistoricalTracksAdmissionGateway;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksAckStatus;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = EventWebTestApplication.class)
class HistoricalTracksEndpointIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440201");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440202");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440203");
  private static final UUID SOURCE_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440204");
  private static final UUID SOURCE_ID_3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440205");
  private static final UUID SOURCE_ID_4 = UUID.fromString("550e8400-e29b-41d4-a716-446655440206");
  private static final UUID SOURCE_ID_5 = UUID.fromString("550e8400-e29b-41d4-a716-446655440207");
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);

  @MockitoBean(name = "sourceAccessRepository")
  private AccessRepository<ServerRole> sourceAccess;

  @MockitoBean(name = "layerAccessRepository")
  private AccessRepository<LayerRole> layerAccess;

  @MockitoBean private JobPublisher jobPublisher;

  @MockitoBean private HistoricalTracksAdmissionGateway historicalTracksAdmissionGateway;

  @Autowired private tools.jackson.databind.ObjectMapper json;

  @BeforeEach
  void setUpAccess() {
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID))
        .thenReturn(ServerRole.VIEWER);
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID_2))
        .thenReturn(ServerRole.VIEWER);
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID_3))
        .thenReturn(ServerRole.VIEWER);
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID_4))
        .thenReturn(ServerRole.VIEWER);
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID_5))
        .thenReturn(ServerRole.VIEWER);
    org.mockito.Mockito.when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.MEMBER);
  }

  @Test
  void submitHistoricalTracks_returnsAcceptedAck() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(validRequestJson()))
            .andExpect(status().isAccepted())
            .andReturn();

    var response = readResponse(result, HistoricalTracksAck.class);
    assertEquals(HistoricalTracksAckStatus.ACCEPTED, response.status());
    assertEquals(2, response.subjects().size());
    var firstSubject = response.subjects().getFirst();
    assertEquals(SOURCE, firstSubject.subjectType());
    assertEquals(SOURCE_ID, firstSubject.subjectId());
    assertNotNull(firstSubject.jobId());

    var submissions = captureScheduledBatchAndRunCallback(1);
    assertEquals(response.requestId(), submissions.getFirst().requestId());
    assertEquals(HistoricalTracksJobSubmission.TOPIC, submissions.getFirst().topic());
    var publishedSubjects = submissions.getFirst().subjects();
    // Each acked subject carries the job id of the child subject entry in the payload.
    var jobIdBySubject =
        publishedSubjects.stream()
            .collect(
                Collectors.toMap(
                    HistoricalTracksSubject::subjectId, HistoricalTracksSubject::jobId));
    for (var subject : response.subjects()) {
      assertEquals(SOURCE, subject.subjectType());
      assertEquals(jobIdBySubject.get(subject.subjectId()), subject.jobId());
    }
    org.mockito.Mockito.verify(historicalTracksAdmissionGateway)
        .requestCancellationForActiveJobs(USER_ID, response.requestId());
  }

  @Test
  void submitHistoricalTracks_whenMalformedJson_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content("{"))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("INVALID_REQUEST", error.code());
  }

  @Test
  void submitHistoricalTracks_whenUnauthorized_returnsForbidden() throws Exception {
    org.mockito.Mockito.when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(null);

    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(singleSourceRequestJson()))
            .andExpect(status().isForbidden())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("User is not authorized to access subject " + SOURCE_ID, error.message());
  }

  @Test
  void submitHistoricalTracks_whenRangeExceedsLimit_returnsUnprocessableEntity() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(overRangeRequestJson()))
            .andExpect(status().isUnprocessableEntity())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(
        "Historical request exceeds the maximum allowed range of 30 days", error.message());
  }

  @Test
  void submitHistoricalTracks_whenOneSourceAccepted_requestsCancellationAndReturns202()
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(singleSourceRequestJson()))
            .andExpect(status().isAccepted())
            .andReturn();

    var response = readResponse(result, HistoricalTracksAck.class);
    assertEquals(HistoricalTracksAckStatus.ACCEPTED, response.status());
    captureScheduledBatchAndRunCallback(1);
    org.mockito.Mockito.verify(historicalTracksAdmissionGateway)
        .requestCancellationForActiveJobs(USER_ID, response.requestId());
  }

  @Test
  void submitHistoricalTracks_whenFiveSourcesAccepted_returns202AndSchedulesFiveJobs()
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/history/tracks")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType("application/json")
                    .content(fiveSourceRequestJson()))
            .andExpect(status().isAccepted())
            .andReturn();

    var response = readResponse(result, HistoricalTracksAck.class);
    assertEquals(HistoricalTracksAckStatus.ACCEPTED, response.status());

    var submissions = captureScheduledBatchAndRunCallback(1);
    var requestIds =
        submissions.stream()
            .map(HistoricalTracksJobSubmission::requestId)
            .collect(Collectors.toSet());
    assertEquals(1, requestIds.size());
    assertTrue(requestIds.contains(response.requestId()));
    org.mockito.Mockito.verify(historicalTracksAdmissionGateway)
        .requestCancellationForActiveJobs(USER_ID, response.requestId());
  }

  private List<HistoricalTracksJobSubmission> captureScheduledBatchAndRunCallback(
      int expectedSize) {
    var submissionCaptor = ArgumentCaptor.forClass(List.class);
    var callbackCaptor = ArgumentCaptor.forClass(Runnable.class);
    org.mockito.Mockito.verify(jobPublisher)
        .scheduleBatchAfterCommit(submissionCaptor.capture(), callbackCaptor.capture());
    var submissions =
        submissionCaptor.getValue().stream()
            .map(HistoricalTracksJobSubmission.class::cast)
            .toList();
    assertEquals(expectedSize, submissions.size());
    callbackCaptor.getValue().run();
    return submissions;
  }

  private String validRequestJson() throws Exception {
    return json.writeValueAsString(
        java.util.Map.of(
            "start",
            "2026-04-06T10:00:00Z",
            "end",
            "2026-04-06T12:00:00Z",
            "filters",
            java.util.List.of(),
            "sources",
            java.util.List.of(
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID),
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID_2))));
  }

  private String singleSourceRequestJson() throws Exception {
    return json.writeValueAsString(
        java.util.Map.of(
            "start",
            "2026-04-06T10:00:00Z",
            "end",
            "2026-04-06T11:00:00Z",
            "filters",
            java.util.List.of(),
            "sources",
            java.util.List.of(java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID))));
  }

  private String overRangeRequestJson() throws Exception {
    return json.writeValueAsString(
        java.util.Map.of(
            "start",
            "2026-04-01T00:00:00Z",
            "end",
            "2026-05-02T00:00:01Z",
            "filters",
            java.util.List.of(),
            "sources",
            java.util.List.of(java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID))));
  }

  private String fiveSourceRequestJson() throws Exception {
    return json.writeValueAsString(
        java.util.Map.of(
            "start",
            "2026-04-06T10:00:00Z",
            "end",
            "2026-04-06T11:00:00Z",
            "filters",
            java.util.List.of(),
            "sources",
            java.util.List.of(
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID),
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID_2),
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID_3),
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID_4),
                java.util.Map.of("subjectType", "SOURCE", "subjectId", SOURCE_ID_5))));
  }
}
