// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalFilterRequest;
import com.opencop.core.sdk.model.history.HistoricalJobStage;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.HistoricalTracksSubject;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.core.sdk.model.job.JobSubmission;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry;
import com.opencop.core.sdk.observability.history.HistoricalTraceContext;
import com.opencop.processor.business.model.history.HistoricalExecutionContext;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.policy.history.HistoricalChunkPlanner;
import com.opencop.processor.business.policy.history.HistoricalExecutionFailureClassifier;
import com.opencop.processor.business.policy.history.HistoricalJobAdmissionRecoveryPolicy;
import com.opencop.processor.business.usecase.event.JobStatusNotificationPublisher;
import com.opencop.processor.business.usecase.history.HistoricalChunkExecutionCoordinator;
import com.opencop.processor.business.usecase.history.HistoricalJobAdmissionRecoveryFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectJobFactory;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneJobHandler;
import com.opencop.processor.business.usecase.history.HistoricalSubjectLaneScheduler;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalPlanGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalSubjectLaneExecutionGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoryEventPublisher;
import com.opencop.processor.technical.gateway.history.HistoricalJobExecutionSupervisor;
import com.opencop.processor.technical.gateway.history.HistoricalJobIngressRejectionHandler;
import com.opencop.processor.technical.gateway.history.HistoricalJobIngressService;
import com.opencop.processor.technical.gateway.history.HistoricalJobTerminalCoordinator;
import com.opencop.processor.technical.gateway.history.HistoryJobLifecycleEventRecorder;
import com.opencop.processor.technical.gateway.history.cancellation.HistoricalJobCancellationFinalizer;
import com.opencop.processor.technical.gateway.history.execution.HistoricalJobExecutionOutcomeHandler;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalTracksConsumerTest {

  private final RecordingHistoricalJobIngressService ingressService =
      new RecordingHistoricalJobIngressService(RecordingHistoricalJobIngressService.dependencies());
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HistoricalTracksConsumer consumer;

  HistoricalTracksConsumerTest() {
    consumer = new HistoricalTracksConsumer(ingressService, objectMapper, defaultTracer());
  }

  @Test
  void consume_deserializesHistoricalPayload_andHandsOffToIngressService() throws Exception {
    var job =
        HistoricalTracksJobSubmission.create(
            UUID.randomUUID(),
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
            SubjectType.SOURCE,
            UUID.randomUUID(),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            HistoricalResolution.MINUTE_1,
            118,
            List.of(new HistoricalFilterRequest("status", Map.of("value", "active"))));

    consumer.consume(recordFor(job));

    assertEquals(job, ingressService.acceptedJob);
  }

  @Test
  void consume_whenUnifiedPayloadContainsMultipleSubjects_dispatchesToIngressService()
      throws Exception {
    var job =
        HistoricalTracksJobSubmission.create(
            UUID.randomUUID(),
            JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
            List.of(
                new HistoricalTracksSubject(
                    UUID.randomUUID(),
                    SubjectType.SOURCE,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440210")),
                new HistoricalTracksSubject(
                    UUID.randomUUID(),
                    SubjectType.LAYER,
                    UUID.fromString("550e8400-e29b-41d4-a716-446655440211"))),
            UUID.fromString("550e8400-e29b-41d4-a716-446655440310"),
            Instant.parse("2026-04-07T10:00:00Z"),
            Instant.parse("2026-04-06T10:02:17Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:00:00Z"),
            HistoricalResolution.MINUTE_1,
            118,
            List.of());

    consumer.consume(recordFor(job));

    assertEquals(job, ingressService.acceptedJob);
  }

  @Test
  void
      consume_whenLegacySingleSubjectPayloadDispatchesLegacyCompatibleHistoricalSubmissionToIngress()
          throws Exception {
    String legacyPayload =
        """
            {"type":"HISTORICAL_TRACKS","jobId":"550e8400-e29b-41d4-a716-446655440099","requestId":"550e8400-e29b-41d4-a716-446655440200","issuer":{"type":"USER","id":"550e8400-e29b-41d4-a716-446655440201"},"subjectType":"SOURCE","subjectId":"550e8400-e29b-41d4-a716-446655440300","requestedBy":"550e8400-e29b-41d4-a716-446655440301","requestedAt":"2026-05-25T00:00:00Z","requestedFrom":"2026-05-24T00:00:00Z","requestedTo":"2026-05-25T00:00:00Z","effectiveFrom":"2026-05-24T00:00:00Z","effectiveTo":"2026-05-25T00:00:00Z","resolution":"MINUTE_1","plannedChunks":1,"filters":[]}""";

    consumer.consume(recordFor(legacyPayload.getBytes(StandardCharsets.UTF_8)));

    assertNotNull(ingressService.acceptedJob);
    assertEquals(
        1,
        ingressService.acceptedJob.subjects().size(),
        "legacy payload must normalize to one subject");
    assertEquals(
        SubjectType.SOURCE, ingressService.acceptedJob.subjects().getFirst().subjectType());
    assertEquals(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440300"),
        ingressService.acceptedJob.subjects().getFirst().subjectId());
  }

  @Test
  void consume_whenPayloadIsInvalid_throwsAndDoesNotDelegate() {
    assertThrows(IllegalStateException.class, () -> consumer.consume(recordFor("{".getBytes())));

    assertNull(ingressService.acceptedJob);
  }

  @Test
  void consume_whenPayloadIsWrongJobType_throws() throws Exception {
    var unsupportedConsumer =
        new HistoricalTracksConsumer(ingressService, objectMapper, defaultTracer());
    var job =
        new com.opencop.core.sdk.model.job.GeoCacheHydrationJobSubmission(
            UUID.randomUUID(),
            JobIssuer.system("consumer-test"),
            UUID.randomUUID(),
            com.opencop.core.sdk.model.cache.GeoCacheHydrationJob.Mode.LATEST_ACTIVE_ONLY,
            com.opencop.core.sdk.model.cache.GeoCacheHydrationJob.Trigger.MANUAL);

    assertThrows(
        IllegalStateException.class,
        () -> unsupportedConsumer.consume(recordFor(objectMapper.writeValueAsBytes(job))));
    assertNull(ingressService.acceptedJob);
  }

  @Test
  void consume_withValidTraceHeaders_usesSanitizedParentContextAndNoForbiddenAttributes()
      throws Exception {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mockedCurrentSpan("4bf92f3577b34da6a3ce929d0e0e4736", "0102030405060708");
    when(tracer.spanBuilder("history.processor.consume")).thenReturn(spanBuilder);
    when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any(Context.class)))
        .thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    var tracedConsumer = new HistoricalTracksConsumer(ingressService, objectMapper, tracer);
    var job = historicalMultiSubjectSubmission();
    var record =
        recordFor(
            objectMapper.writeValueAsBytes(job),
            new String[][] {
              {"traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"},
              {"tracestate", "vendor=ok"},
              {"x-user-id", "550e8400-e29b-41d4-a716-446655440120"}
            });

    tracedConsumer.consume(record);

    verify(tracer).spanBuilder("history.processor.consume");
    var parentCaptor = org.mockito.ArgumentCaptor.forClass(Context.class);
    verify(spanBuilder).setParent(parentCaptor.capture());
    var parentSpanContext = Span.fromContext(parentCaptor.getValue()).getSpanContext();
    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", parentSpanContext.getTraceId());
    assertEquals("00f067aa0ba902b7", parentSpanContext.getSpanId());
    assertNotNull(ingressService.acceptedJob);
    assertEquals(job, ingressService.acceptedJob);
    assertNotNull(ingressService.acceptedTraceContext);
    assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", traceId(ingressService.acceptedTraceContext));
    assertFalse(ingressService.acceptedTraceContext.containsKey("x-user-id"));

    var attributesCaptor =
        org.mockito.ArgumentCaptor.forClass(io.opentelemetry.api.common.Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    var attributes = attributesCaptor.getValue();
    assertEquals(job.requestId().toString(), attributes.get(AttributeKey.stringKey("requestId")));
    assertEquals(job.jobId().toString(), attributes.get(AttributeKey.stringKey("jobId")));
    assertEquals("SOURCE,LAYER", attributes.get(AttributeKey.stringKey("subjectType")));
    assertNull(attributes.get(AttributeKey.stringKey("subjectId")));
    assertNull(attributes.get(AttributeKey.stringKey("requestedBy")));
    assertNull(attributes.get(AttributeKey.stringKey("latitude")));
    assertNull(attributes.get(AttributeKey.stringKey("longitude")));
  }

  @Test
  void consume_withMalformedOrDisallowedTraceHeaders_dropsProvidedHeaderValues() throws Exception {
    Tracer tracer = mock(Tracer.class);
    SpanBuilder spanBuilder = mock(SpanBuilder.class);
    Span span = mockedCurrentSpan("1234567890abcdef1234567890abcdef", "0102030405060708");
    when(tracer.spanBuilder("history.processor.consume")).thenReturn(spanBuilder);
    when(spanBuilder.setParent(org.mockito.ArgumentMatchers.any(Context.class)))
        .thenReturn(spanBuilder);
    when(spanBuilder.startSpan()).thenReturn(span);
    var tracedConsumer = new HistoricalTracksConsumer(ingressService, objectMapper, tracer);
    var job = historicalSubmission();
    var record =
        recordFor(
            objectMapper.writeValueAsBytes(job),
            new String[][] {
              {"traceparent", "bad"},
              {"tracestate", "a".repeat(513)},
              {"x-user-id", "550e8400-e29b-41d4-a716-446655440120"}
            });

    tracedConsumer.consume(record);

    var parentCaptor = org.mockito.ArgumentCaptor.forClass(Context.class);
    verify(spanBuilder).setParent(parentCaptor.capture());
    assertFalse(Span.fromContext(parentCaptor.getValue()).getSpanContext().isValid());
    assertNotNull(ingressService.acceptedTraceContext);
    assertNotNull(ingressService.acceptedTraceContext.get("traceparent"));
    assertFalse(traceId(ingressService.acceptedTraceContext).equals("bad"));
    assertFalse(ingressService.acceptedTraceContext.containsKey("tracestate"));
    assertFalse(ingressService.acceptedTraceContext.containsKey("x-user-id"));
    var attributesCaptor =
        org.mockito.ArgumentCaptor.forClass(io.opentelemetry.api.common.Attributes.class);
    verify(span).setAllAttributes(attributesCaptor.capture());
    var values =
        attributesCaptor.getValue().asMap().values().stream().map(String::valueOf).toList();
    assertFalse(values.contains("bad"));
    assertFalse(values.contains("a".repeat(513)));
    assertFalse(values.contains("550e8400-e29b-41d4-a716-446655440120"));
  }

  private static String traceId(Map<String, String> traceContext) {
    String traceparent = traceContext.get("traceparent");
    assertNotNull(traceparent);
    String[] parts = traceparent.split("-");
    assertEquals(4, parts.length);
    return parts[1];
  }

  private static Span mockedCurrentSpan(String traceId, String spanId) {
    Span span = mock(Span.class);
    Span realSpan =
        Span.wrap(
            SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault()));
    when(span.getSpanContext()).thenReturn(realSpan.getSpanContext());
    when(span.storeInContext(any(Context.class)))
        .thenAnswer(invocation -> invocation.<Context>getArgument(0).with(realSpan));
    when(span.makeCurrent())
        .thenAnswer(invocation -> span.storeInContext(Context.current()).makeCurrent());
    return span;
  }

  private ConsumerRecord<String, byte[]> recordFor(HistoricalTracksJobSubmission job)
      throws Exception {
    return recordFor(objectMapper.writeValueAsBytes(job));
  }

  private ConsumerRecord<String, byte[]> recordFor(byte[] payload) {
    return recordFor(payload, new String[0][0]);
  }

  private ConsumerRecord<String, byte[]> recordFor(byte[] payload, String[][] headers) {
    var record = new ConsumerRecord<>("historical-topic", 0, 0L, "key", payload);
    var recordHeaders = new RecordHeaders();
    for (String[] header : headers) {
      recordHeaders.add(header[0], header[1].getBytes());
    }
    recordHeaders.forEach(header -> record.headers().add(header));
    return record;
  }

  private static HistoricalTracksJobSubmission historicalSubmission() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        JobIssuer.user(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        SubjectType.SOURCE,
        UUID.fromString("33333333-3333-3333-3333-333333333333"),
        UUID.fromString("44444444-4444-4444-4444-444444444444"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.MINUTE_1,
        60,
        List.of());
  }

  private static HistoricalTracksJobSubmission historicalMultiSubjectSubmission() {
    return HistoricalTracksJobSubmission.create(
        UUID.fromString("11111111-1111-1111-1111-111111111110"),
        JobIssuer.user(UUID.fromString("22222222-2222-2222-2222-222222222222")),
        List.of(
            new HistoricalTracksSubject(
                UUID.fromString("33333333-3333-3333-3333-333333333330"),
                SubjectType.SOURCE,
                UUID.fromString("33333333-3333-3333-3333-333333333331")),
            new HistoricalTracksSubject(
                UUID.fromString("33333333-3333-3333-3333-333333333332"),
                SubjectType.LAYER,
                UUID.fromString("33333333-3333-3333-3333-333333333333"))),
        UUID.fromString("44444444-4444-4444-4444-444444444444"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        Instant.parse("2026-04-06T10:00:00Z"),
        Instant.parse("2026-04-06T11:00:00Z"),
        HistoricalResolution.MINUTE_1,
        60,
        List.of());
  }

  private static Tracer defaultTracer() {
    return GlobalOpenTelemetry.getTracer(HistoricalTelemetry.PROCESSOR_CONSUMER_TRACER_NAME);
  }

  private static final class RecordingHistoricalJobIngressService
      extends HistoricalJobIngressService {

    private HistoricalTracksJobSubmission acceptedJob;
    private Map<String, String> acceptedTraceContext;

    private RecordingHistoricalJobIngressService(IngressDependencies dependencies) {
      super(
          dependencies.jobStateGateway(),
          dependencies.admissionCoordinator(),
          dependencies.historicalObservability(),
          dependencies.scheduler(),
          dependencies.properties(),
          dependencies.terminalCoordinator(),
          dependencies.executionSupervisor(),
          dependencies.cancellationFinalizer(),
          dependencies.executionOutcomeHandler(),
          dependencies.rejectionHandler(),
          new HistoricalJobAdmissionRecoveryFactory(
              dependencies.properties().getAdmissionRecoveryInterval(),
              dependencies.properties().getTtlRefreshInterval(),
              dependencies.properties().getMaxRuntime(),
              dependencies.properties().getJobStateTtl(),
              new HistoricalJobAdmissionRecoveryPolicy()),
          mock(HistoricalSubjectLaneScheduler.class));
    }

    @Override
    public void accept(HistoricalTracksJobSubmission job) {
      this.acceptedJob = job;
      this.acceptedTraceContext = HistoricalTraceContext.captureCurrent();
    }

    private static IngressDependencies dependencies() {
      var jobStateGateway = new NoOpHistoricalJobStateRepository();
      var admissionCoordinator = new NoOpHistoricalJobAdmissionCoordinator();
      var notifications = new NoOpJobStatusNotificationPublisher();
      var historyEventPublisher = new NoOpHistoryEventPublisher();
      var executor = new DirectExecutorService();
      var scheduler = Executors.newSingleThreadScheduledExecutor();
      var properties = new HistoricalExecutionProperties();
      var historicalObservability = new HistoricalObservability(new SimpleMeterRegistry());
      var terminalCoordinator =
          new HistoricalJobTerminalCoordinator(jobStateGateway, admissionCoordinator);
      var executionSupervisor =
          new HistoricalJobExecutionSupervisor(
              jobStateGateway,
              notifications,
              historyEventPublisher,
              executor,
              executor,
              scheduler,
              properties,
              historicalObservability);
      var cancellationFinalizer =
          new HistoricalJobCancellationFinalizer(
              jobStateGateway,
              historyEventPublisher,
              historicalObservability,
              new HistoryJobLifecycleEventRecorder());
      var executionOutcomeHandler =
          new HistoricalJobExecutionOutcomeHandler(
              jobStateGateway,
              mock(HistoricalSubjectLaneJobHandler.class),
              notifications,
              historyEventPublisher,
              historicalObservability,
              new HistoricalExecutionFailureClassifier());
      var rejectionHandler =
          new HistoricalJobIngressRejectionHandler(
              jobStateGateway, notifications, historyEventPublisher, historicalObservability);
      return new IngressDependencies(
          jobStateGateway,
          admissionCoordinator,
          historicalObservability,
          scheduler,
          properties,
          terminalCoordinator,
          executionSupervisor,
          cancellationFinalizer,
          executionOutcomeHandler,
          rejectionHandler);
    }

    private static HistoricalSubjectLaneScheduler historicalSubjectLaneScheduler() {
      return new HistoricalSubjectLaneScheduler(
          noOpLaneExecutionGateway(),
          new HistoricalSubjectJobFactory(),
          new HistoricalChunkPlanner(),
          schedulerProperties());
    }

    private static HistoricalSubjectLaneExecutionGateway noOpLaneExecutionGateway() {
      return (laneMetadata, laneStarted) -> laneStarted.run();
    }

    private static HistoricalExecutionProperties schedulerProperties() {
      var properties = new HistoricalExecutionProperties();
      properties.setMaxConcurrentSubjectsPerJob(1);
      return properties;
    }

    private record IngressDependencies(
        HistoricalJobStateGateway jobStateGateway,
        HistoricalJobAdmissionCoordinator admissionCoordinator,
        HistoricalObservability historicalObservability,
        java.util.concurrent.ScheduledExecutorService scheduler,
        HistoricalExecutionProperties properties,
        HistoricalJobTerminalCoordinator terminalCoordinator,
        HistoricalJobExecutionSupervisor executionSupervisor,
        HistoricalJobCancellationFinalizer cancellationFinalizer,
        HistoricalJobExecutionOutcomeHandler executionOutcomeHandler,
        HistoricalJobIngressRejectionHandler rejectionHandler) {}
  }

  private static final class NoOpHistoricalJobAdmissionCoordinator
      implements HistoricalJobAdmissionCoordinator {

    @Override
    public HistoricalJobAdmissionDecision register(HistoricalTracksJobSubmission job) {
      return HistoricalJobAdmissionDecision.startNow();
    }

    @Override
    public HistoricalJobAdmissionDecision recordExecutionExit(UUID jobId) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }

    @Override
    public HistoricalJobAdmissionDecision recordTerminal(UUID jobId) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }
  }

  private static final class NoOpHistoricalJobStateRepository implements HistoricalJobStateGateway {

    @Override
    public void createReceived(HistoricalTracksJobSubmission job) {}

    @Override
    public boolean markQueued(UUID jobId, String queueRole) {
      return true;
    }

    @Override
    public boolean markRunning(UUID jobId, String workerId) {
      return true;
    }

    @Override
    public boolean markPlanning(UUID jobId) {
      return true;
    }

    @Override
    public boolean markPlanned(UUID jobId) {
      return true;
    }

    @Override
    public boolean markCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveredCompleted(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryPending(UUID jobId) {
      return true;
    }

    @Override
    public boolean markDeliveryFailed(UUID jobId, String failureReason) {
      return true;
    }

    @Override
    public boolean markFailed(UUID jobId, HistoricalJobStage stage, String failureReason) {
      return true;
    }

    @Override
    public boolean markCancellationRequested(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean markCancelled(UUID jobId, String reason) {
      return true;
    }

    @Override
    public boolean refreshActiveTtl(UUID jobId) {
      return true;
    }

    @Override
    public Optional<HistoricalJobState> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }

  private static final class NoOpHistoricalPlanRepository implements HistoricalPlanGateway {

    @Override
    public void save(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkPlan> findByJobId(UUID jobId) {
      return Optional.empty();
    }
  }

  private static final class NoOpHistoricalChunkPayloadCacheGateway
      implements HistoricalChunkPayloadCacheGateway {

    @Override
    public Map<String, Optional<java.time.Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      return Map.of();
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, java.time.Duration ttl) {}

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.empty();
    }

    @Override
    public Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return payloadKeys.stream()
          .collect(
              java.util.stream.Collectors.toMap(
                  key -> key,
                  key -> Optional.<byte[]>empty(),
                  (left, right) -> right,
                  java.util.LinkedHashMap::new));
    }
  }

  private static final class NoOpHistoricalChunkStateGateway
      implements HistoricalChunkStateGateway {

    @Override
    public void initialize(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
      return Optional.empty();
    }

    @Override
    public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {}

    @Override
    public void updatePartCount(String chunkStateKey, int partCount) {}
  }

  private static final class NoOpJobStatusNotificationPublisher
      extends JobStatusNotificationPublisher {

    private NoOpJobStatusNotificationPublisher() {
      super(null, new ObjectMapper());
    }

    @Override
    public void notifySuccess(JobSubmission job) {}

    @Override
    public void notifyFailure(JobSubmission job, Throwable exception) {}
  }

  private static final class NoOpHistoryEventPublisher implements HistoryEventPublisher {

    @Override
    public void publishPlan(
        HistoricalChunkPlan plan, HistoricalExecutionContext executionContext) {}

    @Override
    public void publishProgress(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishExecutionProgress(
        HistoricalChunkPlan plan,
        HistoricalExecutionContext executionContext,
        long computedChunks,
        long failedChunks) {}

    @Override
    public void publishCompleted(
        HistoricalChunkPlan plan,
        HistoricalTracksJobSubmission job,
        long computedChunks,
        long deliveredChunks,
        long failedChunks) {}

    @Override
    public void publishFailed(
        HistoricalTracksJobSubmission job, HistoricalJobStage stage, String failureReason) {}
  }

  private static final class NoOpHistoricalChunkExecutionCoordinator
      extends HistoricalChunkExecutionCoordinator {

    private NoOpHistoricalChunkExecutionCoordinator() {
      super(null, null);
    }
  }

  private static final class NoOpProducer
      implements com.opencop.core.sdk.ports.gateway.broker.MessageBrokerProducer<byte[]> {

    @Override
    public void send(byte[] value, java.util.Map<String, String> headers) {}

    @Override
    public void sendDlt(byte[] value) {}

    @Override
    public void send(byte[] value) {}
  }

  private static final class DirectExecutorService extends AbstractExecutorService {

    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }
}
