// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.HistoricalJobTerminalDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.context.Context;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HistoricalJobAdmissionLifecycleTest {

  private static final Instant NOW = Instant.parse("2026-04-07T12:00:00Z");
  private static final Duration TTL = Duration.ofHours(24);

  private final HistoricalJobStateGateway jobStateGateway = mock(HistoricalJobStateGateway.class);
  private final HistoricalJobAdmissionCoordinator admissionCoordinator =
      mock(HistoricalJobAdmissionCoordinator.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoricalObservability historicalObservability =
      new HistoricalObservability(meterRegistry);
  private final List<UUID> cancelledTrackedJobs = new ArrayList<>();
  private final List<HistoricalTracksJobSubmission> submittedJobs = new ArrayList<>();
  private final List<String> lifecycleEvents = new ArrayList<>();
  private final AtomicReference<UUID> cancelledReplacementRequestId = new AtomicReference<>();
  private final Set<UUID> jobsCancelledBeforeStart = new HashSet<>();
  private final HistoricalJobAdmissionLifecycle lifecycle =
      new HistoricalJobAdmissionLifecycle(
          jobStateGateway,
          admissionCoordinator,
          historicalObservability,
          ignored -> false,
          cancelledTrackedJobs::add,
          (job, ignored) -> submittedJobs.add(job),
          (job, replacementRequestId) -> {
            cancelledReplacementRequestId.set(replacementRequestId);
            return jobsCancelledBeforeStart.contains(job.jobId());
          },
          (eventName, ignored) -> lifecycleEvents.add(eventName));

  @Test
  void recordRegistrationDecision_whenReplacementQueued_recordsEventsAndRequestsCancellation() {
    var registeredJob = job();
    var runningJob = job();
    var decision =
        new HistoricalJobAdmissionDecision(
            HistoricalJobAdmissionDecision.Action.QUEUE_ONLY,
            "QUEUED_REPLACEMENT",
            List.of(),
            List.of(cancellation(runningJob.jobId(), "replacement")),
            true,
            true,
            false);
    when(jobStateGateway.findByJobId(runningJob.jobId()))
        .thenReturn(Optional.of(running(runningJob)));
    when(jobStateGateway.markCancellationRequested(runningJob.jobId(), "replacement"))
        .thenReturn(true);

    lifecycle.recordRegistrationDecision(decision, registeredJob, Context.current());

    verify(jobStateGateway).markCancellationRequested(runningJob.jobId(), "replacement");
    assertEquals(List.of(runningJob.jobId()), cancelledTrackedJobs);
    assertEquals(
        List.of("history.job.replacement_queued", "history.job.replacement_superseded"),
        lifecycleEvents);
  }

  @Test
  void recordQueuedAdmission_whenDecisionHasNoQueueRole_marksDefaultQueuedAndRecordsMetric() {
    var job = job();
    var decision = HistoricalJobAdmissionDecision.startNow();
    when(jobStateGateway.markQueued(job.jobId())).thenReturn(true);

    boolean recorded = lifecycle.recordQueuedAdmission(decision, job, Duration.ofMillis(5));

    assertTrue(recorded);
    verify(jobStateGateway).markQueued(job.jobId());
    verify(jobStateGateway, never()).markQueued(job.jobId(), null);
    var queuedCounter =
        meterRegistry
            .find("opencop_history_job_stage_total")
            .tags("stage", JobStage.QUEUED.name(), "outcome", "success")
            .counter();
    assertEquals(1.0d, queuedCounter.count());
  }

  @Test
  void recordQueuedAdmission_whenDecisionHasQueueRole_marksQueuedWithRole() {
    var job = job();
    var decision = HistoricalJobAdmissionDecision.queueOnly("QUEUED_REPLACEMENT", true, false);
    when(jobStateGateway.markQueued(job.jobId(), "QUEUED_REPLACEMENT")).thenReturn(true);

    boolean recorded = lifecycle.recordQueuedAdmission(decision, job, Duration.ofMillis(5));

    assertTrue(recorded);
    verify(jobStateGateway).markQueued(job.jobId(), "QUEUED_REPLACEMENT");
    verify(jobStateGateway, never()).markQueued(job.jobId());
  }

  @Test
  void recordQueuedAdmission_whenMarkQueuedFails_cleansUpRegistrationAndReturnsFalse() {
    var job = job();
    var decision = HistoricalJobAdmissionDecision.startNow();
    when(jobStateGateway.markQueued(job.jobId())).thenReturn(false);
    when(admissionCoordinator.cleanupAfterRegistrationFailure(job.jobId()))
        .thenReturn(HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false));

    boolean recorded = lifecycle.recordQueuedAdmission(decision, job, Duration.ofMillis(5));

    assertFalse(recorded);
    verify(admissionCoordinator).cleanupAfterRegistrationFailure(job.jobId());
  }

  @Test
  void onTerminalDecision_whenQueuedJobsStart_recordsReplacementStartedAndSubmitsJob() {
    var replacement = job();
    var decision = HistoricalJobAdmissionDecision.noop(List.of(replacement), List.of(), true);
    when(jobStateGateway.findByJobId(replacement.jobId()))
        .thenReturn(Optional.of(queued(replacement)));

    lifecycle.onTerminalDecision(
        new HistoricalJobTerminalDecision(UUID.randomUUID(), replacement.requestId(), decision));

    assertEquals(List.of("history.job.replacement_started"), lifecycleEvents);
    assertEquals(List.of(replacement), submittedJobs);
  }

  @Test
  void onTerminalDecision_whenQueuedJobIsCancelledBeforeStart_recordsExitAndStartsNextJob() {
    var cancelled = job();
    var next = job();
    var replacementRequestId = UUID.randomUUID();
    jobsCancelledBeforeStart.add(cancelled.jobId());
    when(admissionCoordinator.recordExecutionExit(cancelled.jobId()))
        .thenReturn(HistoricalJobAdmissionDecision.noop(List.of(next), List.of(), false));
    when(jobStateGateway.findByJobId(next.jobId())).thenReturn(Optional.of(queued(next)));

    lifecycle.onTerminalDecision(
        new HistoricalJobTerminalDecision(
            UUID.randomUUID(),
            replacementRequestId,
            HistoricalJobAdmissionDecision.noop(List.of(cancelled), List.of(), false)));

    assertEquals(replacementRequestId, cancelledReplacementRequestId.get());
    verify(admissionCoordinator).recordExecutionExit(cancelled.jobId());
    assertEquals(List.of(next), submittedJobs);
  }

  @Test
  void onExecutionExit_whenUserJobIsDeliveryPending_doesNotReleaseAdmission() {
    var completedUserJob = job();
    when(jobStateGateway.findByJobId(completedUserJob.jobId()))
        .thenReturn(Optional.of(deliveryPending(completedUserJob)));

    lifecycle.onExecutionExit(completedUserJob);

    verify(admissionCoordinator, never()).recordExecutionExit(completedUserJob.jobId());
  }

  private static HistoricalJobAdmissionDecision.HistoricalJobCancellation cancellation(
      UUID jobId, String reason) {
    return new HistoricalJobAdmissionDecision.HistoricalJobCancellation(jobId, reason);
  }

  private static HistoricalJobState queued(HistoricalTracksJobSubmission job) {
    return HistoricalJobState.received(job, NOW, TTL).queued(NOW, TTL);
  }

  private static HistoricalJobState running(HistoricalTracksJobSubmission job) {
    return queued(job).running(NOW, TTL, "worker");
  }

  private static HistoricalJobState deliveryPending(HistoricalTracksJobSubmission job) {
    return queued(job).deliveryPending(NOW, TTL);
  }

  private static HistoricalTracksJobSubmission job() {
    return HistoricalTracksJobSubmission.create(
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
        List.of());
  }
}
