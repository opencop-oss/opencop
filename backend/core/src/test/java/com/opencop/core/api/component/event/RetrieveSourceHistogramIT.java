// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.event.business.model.HistogramMetric;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.iam.source.LayerInfo;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = EventWebTestApplication.class)
class RetrieveSourceHistogramIT extends AbstractWebIT {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440110");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440111");
  private static final Instant FROM = Instant.parse("2026-03-12T10:00:17Z");
  private static final Instant TO = Instant.parse("2026-03-12T10:03:02Z");
  private static final Instant EFFECTIVE_FROM = Instant.parse("2026-03-12T10:00:00Z");
  private static final Instant EFFECTIVE_TO = Instant.parse("2026-03-12T10:04:00Z");
  private static final int PLANNED_BUCKET_COUNT = 4;
  private static final Instant FIRST_EVENT_TIME = Instant.parse("2026-03-12T10:00:30Z");
  private static final Instant SECOND_EVENT_TIME = Instant.parse("2026-03-12T10:01:20Z");
  private static final Instant THIRD_EVENT_TIME = Instant.parse("2026-03-12T10:02:50Z");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440112");
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);
  private static final Authentication ADMIN_AUTH = adminAuthentication(USER_ID);

  private EventFixtures eventFixtures;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private UserSpecificManager userSpecificManager;

  @BeforeEach
  void setUpData() {
    eventFixtures = new EventFixtures(jdbcTemplate, objectMapper);
    eventFixtures.reset();
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());
  }

  @Test
  void viewerCanGetHistogramForAuthorizedSource() throws Exception {
    eventFixtures.insertFeature(
        UUID.randomUUID(), "a", SOURCE_ID, FIRST_EVENT_TIME.plusSeconds(30), FIRST_EVENT_TIME);
    eventFixtures.insertFeature(
        UUID.randomUUID(), "b", SOURCE_ID, SECOND_EVENT_TIME.plusSeconds(30), SECOND_EVENT_TIME);
    eventFixtures.insertFeature(
        UUID.randomUUID(), "c", SOURCE_ID, THIRD_EVENT_TIME.plusSeconds(30), THIRD_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, SourceHistogramResponse.class);
    assertEquals(FROM, response.requestedFrom());
    assertEquals(TO, response.requestedTo());
    assertEquals(EFFECTIVE_FROM, response.effectiveFrom());
    assertEquals(EFFECTIVE_TO, response.effectiveTo());
    assertEquals(HistoricalResolution.MINUTE_1, response.resolution());
    assertEquals(PLANNED_BUCKET_COUNT, response.chunks());
    assertEquals(60_000, response.bucketWidthMillis());
    assertEquals(PLANNED_BUCKET_COUNT, response.buckets().size());
    assertEquals(2, response.buckets().get(0).values().size());
    assertEquals(HistogramMetric.ACTIVE, response.buckets().get(0).values().get(0).metric());
    assertEquals(HistogramMetric.UPDATED, response.buckets().get(0).values().get(1).metric());
    assertEquals(1, response.buckets().get(0).values().get(0).count());
    assertEquals(1, response.buckets().get(0).values().get(1).count());
    assertEquals(SOURCE_ID, response.buckets().get(0).values().get(0).key());
    assertEquals(1, response.buckets().get(1).values().get(1).count());
    assertEquals(1, response.buckets().get(2).values().get(1).count());
    assertEquals(EFFECTIVE_FROM, response.buckets().get(0).start());
    assertEquals(EFFECTIVE_TO, response.buckets().get(3).end());
  }

  @Test
  void memberCanGetHistogramForAuthorizedLayer() throws Exception {
    eventFixtures.insertFeature(
        UUID.randomUUID(), "a", LAYER_ID, FIRST_EVENT_TIME.plusSeconds(30), FIRST_EVENT_TIME);
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());
    when(userSpecificManager.getUserLayers(USER_ID))
        .thenReturn(
            List.of(
                new LayerInfo(
                    LAYER_ID,
                    "Operations",
                    LayerRole.MEMBER,
                    LayerAccessScope.PRIVATE,
                    FROM,
                    true)));

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", LAYER_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, SourceHistogramResponse.class);
    assertEquals(1, response.buckets().get(0).values().get(0).count());
    assertEquals(1, response.buckets().get(0).values().get(1).count());
    assertEquals(1, response.buckets().get(1).values().get(0).count());
    assertEquals(0, response.buckets().get(1).values().get(1).count());
  }

  @Test
  void globalAdminCanGetHistogramWithoutMembership() throws Exception {
    eventFixtures.insertFeature(
        UUID.randomUUID(), "a", SOURCE_ID, FIRST_EVENT_TIME.plusSeconds(30), FIRST_EVENT_TIME);
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, SourceHistogramResponse.class);
    assertEquals(PLANNED_BUCKET_COUNT, response.buckets().size());
  }

  @Test
  void getHistogram_withBbox_filtersEventsToRequestedBounds() throws Exception {
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        "inside",
        SOURCE_ID,
        FIRST_EVENT_TIME.plusSeconds(30),
        FIRST_EVENT_TIME,
        -75.1652,
        39.9526);
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        "outside",
        SOURCE_ID,
        SECOND_EVENT_TIME.plusSeconds(30),
        SECOND_EVENT_TIME,
        -80.0,
        25.0);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString())
                    .queryParam("bbox", "-75.3,39.8,-75.0,40.1"))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, SourceHistogramResponse.class);
    assertEquals(1, response.buckets().get(0).values().get(0).count());
    assertEquals(1, response.buckets().get(0).values().get(1).count());
    assertEquals(1, response.buckets().get(1).values().get(0).count());
    assertEquals(0, response.buckets().get(1).values().get(1).count());
  }

  @Test
  void getHistogram_whenBboxIsInvalid_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString())
                    .queryParam("bbox", "-75.3,39.8,-75.0"))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("'bbox' must be formatted as minLon,minLat,maxLon,maxLat", error.message());
  }

  @Test
  void getHistogram_whenBboxLatitudeIsOutOfRange_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString())
                    .queryParam("bbox", "-75.3,95,-75.0,40.1"))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("'bbox' latitude values must be between -90 and 90", error.message());
  }

  @Test
  void getHistogram_whenBboxCrossesAntimeridian_splitsIntoTwoEnvelopes() throws Exception {
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        "inside-east",
        SOURCE_ID,
        FIRST_EVENT_TIME.plusSeconds(30),
        FIRST_EVENT_TIME,
        175.0,
        15.0);
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        "inside-west",
        SOURCE_ID,
        SECOND_EVENT_TIME.plusSeconds(30),
        SECOND_EVENT_TIME,
        -175.0,
        15.0);
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        "outside",
        SOURCE_ID,
        THIRD_EVENT_TIME.plusSeconds(30),
        THIRD_EVENT_TIME,
        0.0,
        15.0);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString())
                    .queryParam("bbox", "170,10,-170,20"))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, SourceHistogramResponse.class);
    assertEquals(1, response.buckets().get(0).values().get(1).count());
    assertEquals(1, response.buckets().get(1).values().get(1).count());
    assertEquals(0, response.buckets().get(2).values().get(1).count());
  }

  @Test
  void getHistogram_whenNoAccess_returnsForbidden() throws Exception {
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isForbidden())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().contains("not authorized"));
  }

  @Test
  void getHistogram_whenRangeIsInvalid_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", TO.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("'from' must be before 'to'", error.message());
  }

  @Test
  void getHistogram_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/v1/events/{sourceId}/histogram", SOURCE_ID)
                .with(csrf())
                .queryParam("from", FROM.toString())
                .queryParam("to", TO.toString()))
        .andExpect(status().isUnauthorized());
  }

  private static UsernamePasswordAuthenticationToken adminAuthentication(UUID userId) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, true);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));
  }
}
