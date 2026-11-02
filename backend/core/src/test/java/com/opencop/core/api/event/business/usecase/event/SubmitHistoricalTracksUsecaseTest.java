// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.usecase.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.business.model.HistoricalPreflightPlan;
import com.opencop.core.api.event.business.policy.HistoricalTracksAuthorizationPolicy;
import com.opencop.core.api.event.business.policy.HistoricalTracksPreflightPlanner;
import com.opencop.core.api.event.business.policy.HistoricalTracksRequestPolicy;
import com.opencop.core.api.event.ports.gateway.history.HistoricalTracksAdmissionGateway;
import com.opencop.core.api.event.technical.observability.history.HistoricalSubmissionTracing;
import com.opencop.core.api.general.ports.gateway.job.JobPublisher;
import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.history.HistoricalSubjectAck;
import com.opencop.core.sdk.model.history.HistoricalSubjectRequest;
import com.opencop.core.sdk.model.history.HistoricalTracksAck;
import com.opencop.core.sdk.model.history.HistoricalTracksAckStatus;
import com.opencop.core.sdk.model.history.HistoricalTracksRequest;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.iam.AccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class SubmitHistoricalTracksUsecaseTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID REQUEST_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final UUID SOURCE_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440123");
  private static final UUID SOURCE_ID_3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440133");
  private static final UUID LAYER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");
  private static final UUID LAYER_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440124");
  private static final UUID LAYER_ID_3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440125");
  private static final UUID JOB_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440140");
  private static final UUID JOB_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440141");
  private static final UUID JOB_ID_3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");
  private static final UUID JOB_ID_4 = UUID.fromString("550e8400-e29b-41d4-a716-446655440143");
  private static final UUID JOB_ID_5 = UUID.fromString("550e8400-e29b-41d4-a716-446655440144");

  private HistoricalTracksRequestPolicy requestPolicy;
  private HistoricalTracksAuthorizationPolicy authorizationPolicy;
  private HistoricalTracksPreflightPlanner planner;
  private HistoricalTracksAdmissionGateway admissionGateway;
  private PlatformTransactionManager transactionManager;
  private JobPublisher jobPublisher;
  private HistoricalTracksJobSubmissionFactory submissionFactory;
  private HistoricalTracksAckFactory ackFactory;
  private Tracer tracer;
  private Span span;
  private SubmitHistoricalTracksUsecase usecase;

  @SuppressWarnings("unchecked")
  private static List<HistoricalTracksJobSubmission> capturedSubmissions(
      ArgumentCaptor<List> captor) {
    return (List<HistoricalTracksJobSubmission>) captor.getValue();
  }

  @BeforeEach
  void setUp() {
    requestPolicy = mock(HistoricalTracksRequestPolicy.class);
    authorizationPolicy = mock(HistoricalTracksAuthorizationPolicy.class);
    planner = mock(HistoricalTracksPreflightPlanner.class);
    admissionGateway = mock(HistoricalTracksAdmissionGateway.class);
    transactionManager = mock(PlatformTransactionManager.class);
    jobPublisher = mock(JobPublisher.class);
    submissionFactory = mock(HistoricalTracksJobSubmissionFactory.class);
    ackFactory = mock(HistoricalTracksAckFactory.class);
    tracer = mock(Tracer.class);
    var spanBuilder = mock(SpanBuilder.class);
    span = mock(Span.class);
    when(tracer.spanBuilder("history.api.submit")).thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    doReturn(span).when(span).setAttribute(any(String.class), any(String.class));
    doReturn(span).when(span).setStatus(any(), any(String.class));
    TransactionStatus transactionStatus = new SimpleTransactionStatus();
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

    usecase =
        new SubmitHistoricalTracksUsecase(
            requestPolicy,
            authorizationPolicy,
            planner,
            admissionGateway,
            transactionManager,
            jobPublisher,
            submissionFactory,
            ackFactory,
            new HistoricalSubmissionTracing(tracer));
  }

  @Test
  void submit_whenRequestIsAccepted_schedulesOneSubmissionAndReturnsAck() {
    var request = validRequest();
    var plan = plan();
    var authorizedSubjects = request.sources();
    var submission =
        submission(
            List.of(
                historicalSubject(SubjectType.SOURCE, SOURCE_ID, JOB_ID),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID_2, JOB_ID_2)));
    var expectedAck =
        ack(
            REQUEST_ID,
            List.of(JOB_ID, JOB_ID_2),
            List.of(SOURCE_ID, SOURCE_ID_2),
            List.of(SubjectType.SOURCE, SubjectType.SOURCE));

    when(planner.plan(request.start(), request.end())).thenReturn(plan);
    when(authorizationPolicy.authorizeSubjects(any(), eq(request.sources())))
        .thenReturn(authorizedSubjects);
    when(submissionFactory.create(
            any(), eq(request), eq(plan), eq(authorizedSubjects), any(), any()))
        .thenReturn(submission);
    when(ackFactory.accepted(any(), any())).thenReturn(expectedAck);

    var response = usecase.submit(principal(false), request);

    assertSame(expectedAck, response);
    verify(requestPolicy).validateShape(request);
    verify(requestPolicy).rejectUnsupportedFeatures(request);
    verify(requestPolicy).validateGuardrails(request, plan);
    verify(transactionManager).commit(any());

    var submissionCaptor = ArgumentCaptor.forClass(List.class);
    verify(jobPublisher, times(1)).scheduleBatchAfterCommit(submissionCaptor.capture(), any());
    var submitted = capturedSubmissions(submissionCaptor);
    assertEquals(1, submitted.size());
    assertEquals(2, submitted.getFirst().subjects().size());
    assertEquals(
        List.of(SOURCE_ID, SOURCE_ID_2),
        submitted.getFirst().subjects().stream().map(HistoricalTracksSubject::subjectId).toList());
    assertEquals(
        List.of(SubjectType.SOURCE, SubjectType.SOURCE),
        submitted.getFirst().subjects().stream()
            .map(HistoricalTracksSubject::subjectType)
            .toList());
    assertEquals(JOB_ID, submitted.getFirst().subjects().get(0).jobId());
    assertEquals(JOB_ID_2, submitted.getFirst().subjects().get(1).jobId());
    verify(ackFactory).accepted(any(), eq(submitted));
  }

  @Test
  void
      submit_whenRequestContainsMixedSubjects_schedulesOneSubmissionContainingAllSubjectsInRequestOrder() {
    var request = mixedSubjectRequest();
    var plan = plan();
    var requestSubjects = request.sources();
    var submission =
        submission(
            List.of(
                historicalSubject(SubjectType.LAYER, LAYER_ID, JOB_ID),
                historicalSubject(SubjectType.LAYER, LAYER_ID_2, JOB_ID_2),
                historicalSubject(SubjectType.LAYER, LAYER_ID_3, JOB_ID_3),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID, JOB_ID_4),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID_3, JOB_ID_5)));
    var expectedAck =
        ack(
            REQUEST_ID,
            List.of(JOB_ID, JOB_ID_2, JOB_ID_3, JOB_ID_4, JOB_ID_5),
            requestSubjects.stream().map(HistoricalSubjectRequest::subjectId).toList(),
            requestSubjects.stream().map(HistoricalSubjectRequest::subjectType).toList());

    when(planner.plan(request.start(), request.end())).thenReturn(plan);
    when(authorizationPolicy.authorizeSubjects(any(), eq(request.sources())))
        .thenReturn(requestSubjects);
    when(submissionFactory.create(any(), eq(request), eq(plan), eq(requestSubjects), any(), any()))
        .thenReturn(submission);
    when(ackFactory.accepted(any(), any())).thenReturn(expectedAck);

    usecase.submit(principal(false), request);

    var submissionCaptor = ArgumentCaptor.forClass(List.class);
    verify(jobPublisher, times(1)).scheduleBatchAfterCommit(submissionCaptor.capture(), any());
    var submitted = capturedSubmissions(submissionCaptor);
    assertEquals(1, submitted.size());
    var published = submitted.getFirst();
    assertEquals(
        1, submitted.stream().map(HistoricalTracksJobSubmission::requestId).distinct().count());
    assertEquals(5, published.subjects().size());
    assertEquals(
        requestSubjects.stream().map(HistoricalSubjectRequest::subjectId).toList(),
        published.subjects().stream().map(HistoricalTracksSubject::subjectId).toList());
    assertEquals(
        requestSubjects.stream().map(HistoricalSubjectRequest::subjectType).toList(),
        published.subjects().stream().map(HistoricalTracksSubject::subjectType).toList());
  }

  @Test
  void
      submit_whenSingleRequestSubjectIsUnauthorizedWithRealAuthorizationPolicy_failsBeforeScheduling() {
    var request = validRequest();
    var sourceAccess = mock(AccessRepository.class);
    var layerAccess = mock(AccessRepository.class);
    var realAuthorizationPolicy =
        new HistoricalTracksAuthorizationPolicy(sourceAccess, layerAccess);
    var realPolicyUsecase =
        new SubmitHistoricalTracksUsecase(
            requestPolicy,
            realAuthorizationPolicy,
            planner,
            admissionGateway,
            transactionManager,
            jobPublisher,
            submissionFactory,
            ackFactory,
            new HistoricalSubmissionTracing(tracer));

    when(planner.plan(request.start(), request.end())).thenReturn(plan());
    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> realPolicyUsecase.submit(principal(false), request));

    assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    verify(jobPublisher, never()).scheduleBatchAfterCommit(any(), any());
    verify(admissionGateway, never()).requestCancellationForActiveJobs(any(), any());
  }

  @Test
  void
      submit_whenRequestContainsAuthorizedLayerAndSourceSubjects_usesSingleSubmissionWithRealAuthorizationPolicy() {
    var request = mixedSubjectRequest();
    var sourceAccess = mock(AccessRepository.class);
    var layerAccess = mock(AccessRepository.class);
    var realAuthorizationPolicy =
        new HistoricalTracksAuthorizationPolicy(sourceAccess, layerAccess);
    var realPolicyUsecase =
        new SubmitHistoricalTracksUsecase(
            requestPolicy,
            realAuthorizationPolicy,
            planner,
            admissionGateway,
            transactionManager,
            jobPublisher,
            submissionFactory,
            ackFactory,
            new HistoricalSubmissionTracing(tracer));
    when(sourceAccess.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);
    when(sourceAccess.userRole(USER_ID, SOURCE_ID_3)).thenReturn(ServerRole.VIEWER);
    when(layerAccess.userRole(USER_ID, LAYER_ID)).thenReturn(LayerRole.MEMBER);
    when(layerAccess.userRole(USER_ID, LAYER_ID_2)).thenReturn(LayerRole.MEMBER);
    when(layerAccess.userRole(USER_ID, LAYER_ID_3)).thenReturn(LayerRole.MEMBER);
    var authorizedSubjects = request.sources();
    var expectedSubmission =
        submission(
            List.of(
                historicalSubject(SubjectType.LAYER, LAYER_ID, JOB_ID),
                historicalSubject(SubjectType.LAYER, LAYER_ID_2, JOB_ID_2),
                historicalSubject(SubjectType.LAYER, LAYER_ID_3, JOB_ID_3),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID, JOB_ID_4),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID_3, JOB_ID_5)));
    when(planner.plan(request.start(), request.end())).thenReturn(plan());
    when(ackFactory.accepted(any(), any()))
        .thenReturn(
            ack(
                REQUEST_ID,
                List.of(JOB_ID, JOB_ID_2, JOB_ID_3, JOB_ID_4, JOB_ID_5),
                List.of(LAYER_ID, LAYER_ID_2, LAYER_ID_3, SOURCE_ID, SOURCE_ID_3),
                List.of(
                    SubjectType.LAYER,
                    SubjectType.LAYER,
                    SubjectType.LAYER,
                    SubjectType.SOURCE,
                    SubjectType.SOURCE)));
    when(submissionFactory.create(
            any(), eq(request), eq(plan()), eq(authorizedSubjects), any(), any()))
        .thenReturn(expectedSubmission);

    realPolicyUsecase.submit(principal(false), request);

    var submissionCaptor = ArgumentCaptor.forClass(List.class);
    verify(jobPublisher).scheduleBatchAfterCommit(submissionCaptor.capture(), any());
    var submitted = capturedSubmissions(submissionCaptor);
    assertEquals(1, submitted.size());
    assertEquals(5, submitted.getFirst().subjects().size());
    assertEquals(
        request.sources().stream().map(HistoricalSubjectRequest::subjectId).toList(),
        submitted.getFirst().subjects().stream().map(HistoricalTracksSubject::subjectId).toList());
  }

  @Test
  void submit_whenPrincipalIsNull_throwsBeforeSchedulingJobs() {
    assertThrows(NullPointerException.class, () -> usecase.submit(null, validRequest()));

    verify(jobPublisher, never()).scheduleBatchAfterCommit(any(), any());
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  void submit_whenRequestPolicyRejects_recordsErrorSpanAndSchedulesNoJob() {
    var request = validRequest();
    doThrow(
            new GlobalOpenCopException(
                HistoricalErrorCode.HISTORICAL_SOURCES_REQUIRED.name(),
                "sources must not be empty",
                HttpStatus.BAD_REQUEST))
        .when(requestPolicy)
        .validateShape(request);

    assertThrows(GlobalOpenCopException.class, () -> usecase.submit(principal(false), request));

    verify(jobPublisher, never()).scheduleBatchAfterCommit(any(), any());
    verify(transactionManager, never()).getTransaction(any());
    verify(span).setStatus(StatusCode.ERROR, "error");
    verify(span).end();
  }

  @Test
  void submit_whenCommitThrows_doesNotRequestPredecessorCancellation() {
    stubAcceptedSubmit();
    doThrow(new IllegalStateException("commit failed")).when(transactionManager).commit(any());

    assertThrows(
        IllegalStateException.class, () -> usecase.submit(principal(false), validRequest()));

    verify(admissionGateway, never()).requestCancellationForActiveJobs(eq(USER_ID), any());
  }

  @Test
  void submit_requestsPredecessorCancellationOnlyAfterBatchSuccessCallbackRuns() {
    stubAcceptedSubmit();
    when(admissionGateway.requestCancellationForActiveJobs(any(), any())).thenReturn(2);
    AtomicInteger cancellationCount = new AtomicInteger();
    doAnswer(
            invocation -> {
              List<JobSubmission> submissions = invocation.getArgument(0);
              Runnable callback = invocation.getArgument(1);
              assertEquals(1, submissions.size());
              cancellationCount.set(0);
              callback.run();
              return null;
            })
        .when(jobPublisher)
        .scheduleBatchAfterCommit(any(), any());
    doAnswer(
            invocation -> {
              cancellationCount.incrementAndGet();
              return 2;
            })
        .when(admissionGateway)
        .requestCancellationForActiveJobs(any(), any());

    var response = usecase.submit(principal(false), validRequest());

    assertEquals(HistoricalTracksAckStatus.ACCEPTED, response.status());
    assertEquals(1, cancellationCount.get());
  }

  @Test
  void submit_recordsApiSubmitSpanWithSubjectTypeForAcceptedSourceSubjects() {
    stubAcceptedSubmit();

    usecase.submit(principal(false), validRequest());

    verify(tracer).spanBuilder("history.api.submit");
    var attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    assertEquals(
        SubjectType.SOURCE.name(),
        attributesCaptor.getValue().get(AttributeKey.stringKey("subjectType")));
    verify(span, never()).setAttribute(eq("userId"), any(String.class));
    verify(span).end();
  }

  @Test
  void submit_recordsApiSubmitSpanWithSubjectTypeForLayerFirstMixedSubjects() {
    stubAcceptedMixedSubmit();

    usecase.submit(principal(false), mixedSubjectRequest());

    verify(tracer).spanBuilder("history.api.submit");
    var attributesCaptor = ArgumentCaptor.forClass(Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    assertEquals(
        SubjectType.LAYER.name(),
        attributesCaptor.getValue().get(AttributeKey.stringKey("subjectType")));
    verify(span, never()).setAttribute(eq("userId"), any(String.class));
    verify(span).end();
  }

  private void stubAcceptedSubmit() {
    var request = validRequest();
    var plan = plan();
    var authorizedSubjects = request.sources();
    var submission =
        submission(
            List.of(
                historicalSubject(SubjectType.SOURCE, SOURCE_ID, JOB_ID),
                historicalSubject(SubjectType.SOURCE, SOURCE_ID_2, JOB_ID_2)));
    when(planner.plan(request.start(), request.end())).thenReturn(plan);
    when(authorizationPolicy.authorizeSubjects(any(), eq(request.sources())))
        .thenReturn(authorizedSubjects);
    when(submissionFactory.create(
            any(), eq(request), eq(plan), eq(authorizedSubjects), any(), any()))
        .thenReturn(submission);
    when(ackFactory.accepted(any(), any()))
        .thenReturn(
            ack(
                REQUEST_ID,
                List.of(JOB_ID, JOB_ID_2),
                List.of(SOURCE_ID, SOURCE_ID_2),
                List.of(SubjectType.SOURCE, SubjectType.SOURCE)));
  }

  private void stubAcceptedMixedSubmit() {
    var request = mixedSubjectRequest();
    var plan = plan();
    when(planner.plan(request.start(), request.end())).thenReturn(plan);
    when(authorizationPolicy.authorizeSubjects(any(), eq(request.sources())))
        .thenReturn(request.sources());
    when(submissionFactory.create(
            any(), eq(request), eq(plan), eq(request.sources()), any(), any()))
        .thenReturn(
            submission(
                List.of(
                    historicalSubject(SubjectType.LAYER, LAYER_ID, JOB_ID),
                    historicalSubject(SubjectType.LAYER, LAYER_ID_2, JOB_ID_2),
                    historicalSubject(SubjectType.LAYER, LAYER_ID_3, JOB_ID_3),
                    historicalSubject(SubjectType.SOURCE, SOURCE_ID, JOB_ID_4),
                    historicalSubject(SubjectType.SOURCE, SOURCE_ID_3, JOB_ID_5))));
    when(ackFactory.accepted(any(), any()))
        .thenReturn(
            ack(
                REQUEST_ID,
                List.of(JOB_ID, JOB_ID_2, JOB_ID_3, JOB_ID_4, JOB_ID_5),
                List.of(LAYER_ID, LAYER_ID_2, LAYER_ID_3, SOURCE_ID, SOURCE_ID_3),
                List.of(
                    SubjectType.LAYER,
                    SubjectType.LAYER,
                    SubjectType.LAYER,
                    SubjectType.SOURCE,
                    SubjectType.SOURCE)));
  }

  private static HistoricalTracksRequest validRequest() {
    return new HistoricalTracksRequest(
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        List.of(),
        List.of(
            new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID),
            new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID_2)));
  }

  private static HistoricalTracksRequest mixedSubjectRequest() {
    return new HistoricalTracksRequest(
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        List.of(),
        List.of(
            new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID),
            new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID_2),
            new HistoricalSubjectRequest(SubjectType.LAYER, LAYER_ID_3),
            new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID),
            new HistoricalSubjectRequest(SubjectType.SOURCE, SOURCE_ID_3)));
  }

  private static HistoricalPreflightPlan plan() {
    return new HistoricalPreflightPlan(
        HistoricalResolution.MINUTE_1,
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        120);
  }

  private static HistoricalTracksJobSubmission submission(List<HistoricalTracksSubject> subjects) {
    return new HistoricalTracksJobSubmission(
        UUID.randomUUID(),
        REQUEST_ID,
        JobIssuer.user(USER_ID),
        subjects,
        USER_ID,
        Instant.parse("2026-04-06T09:59:59Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T12:00:00Z"),
        HistoricalResolution.MINUTE_1,
        120,
        List.of());
  }

  private static HistoricalTracksSubject historicalSubject(
      SubjectType subjectType, UUID subjectId, UUID jobId) {
    return new HistoricalTracksSubject(jobId, subjectType, subjectId);
  }

  private static HistoricalTracksAck ack(
      UUID requestId, List<UUID> jobIds, List<UUID> subjectIds, List<SubjectType> subjectTypes) {
    var subjects = new ArrayList<HistoricalSubjectAck>(subjectIds.size());
    for (int index = 0; index < subjectIds.size(); index++) {
      subjects.add(
          new HistoricalSubjectAck(
              subjectTypes.get(index), subjectIds.get(index), jobIds.get(index)));
    }
    return new HistoricalTracksAck(requestId, HistoricalTracksAckStatus.ACCEPTED, subjects);
  }

  private static OpenCopPrincipal principal(boolean globalAdmin) {
    return com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
        USER_ID, globalAdmin);
  }
}
