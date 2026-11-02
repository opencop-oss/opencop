// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionCoordinator;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobAdmissionRecoveryState;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.RedisHistoricalJobAdmissionDecisionMapper.ParsedScriptResult;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobAdmissionDecision.HistoricalJobCancellation;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import com.opencop.processor.technical.spring.config.HistoricalExecutionProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisHistoricalJobAdmissionGateway implements HistoricalJobAdmissionCoordinator {
  private final ObjectMapper objectMapper;
  private final RedisHistoricalJobAdmissionScriptGateway scriptGateway;
  private final RedisHistoricalJobAdmissionQueuedPayloads queuedPayloads;
  private final RedisHistoricalJobAdmissionDecisionMapper decisionMapper;
  private final RedisHistoricalAdmissionActiveJobs activeJobs;
  private final RedisHistoricalJobAdmissionMeta meta;

  RedisHistoricalJobAdmissionGateway(
      ObjectMapper objectMapper,
      RedisHistoricalJobAdmissionScriptGateway scriptGateway,
      RedisHistoricalJobAdmissionQueuedPayloads queuedPayloads,
      RedisHistoricalJobAdmissionDecisionMapper decisionMapper,
      RedisHistoricalAdmissionActiveJobs activeJobs,
      RedisHistoricalJobAdmissionMeta meta) {
    this.objectMapper = objectMapper;
    this.scriptGateway = scriptGateway;
    this.queuedPayloads = queuedPayloads;
    this.decisionMapper = decisionMapper;
    this.activeJobs = activeJobs;
    this.meta = meta;
  }

  /**
   * Standalone convenience factory for direct test construction. Cluster fixtures must use the
   * overload accepting the topology-selected key layout.
   */
  public static RedisHistoricalJobAdmissionGateway create(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      HistoricalExecutionProperties properties,
      ProcessorHistoricalRedisScripts scripts) {
    return create(
        redisTemplate, objectMapper, properties, scripts, HistoricalJobControlKeyLayout.STANDALONE);
  }

  public static RedisHistoricalJobAdmissionGateway create(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      HistoricalExecutionProperties properties,
      ProcessorHistoricalRedisScripts scripts,
      HistoricalJobControlKeyLayout keyLayout) {
    var scriptGateway =
        new RedisHistoricalJobAdmissionScriptGateway(redisTemplate, keyLayout, properties, scripts);
    var queuedPayloads =
        new RedisHistoricalJobAdmissionQueuedPayloads(redisTemplate, objectMapper, scriptGateway);
    var decisionMapper = new RedisHistoricalJobAdmissionDecisionMapper();
    var activeJobs = new RedisHistoricalAdmissionActiveJobs(redisTemplate, keyLayout);
    var meta = new RedisHistoricalJobAdmissionMeta(redisTemplate, scriptGateway);
    return new RedisHistoricalJobAdmissionGateway(
        objectMapper, scriptGateway, queuedPayloads, decisionMapper, activeJobs, meta);
  }

  @Override
  public HistoricalJobAdmissionDecision register(HistoricalTracksJobSubmission job) {
    Objects.requireNonNull(job, "job");
    // Cluster registration writes the owner inside Lua with the admission mutation. Standalone
    // retains its existing Java pre-write, so it does not gain a new script-side mutation.
    if (scriptGateway.keyLayout() == HistoricalJobControlKeyLayout.STANDALONE) {
      meta.recordOwner(job.jobId(), job.requestedBy());
    }
    List<Object> result = scriptGateway.register(job, serializePayload(job));
    return toDecision(job.requestedBy(), result);
  }

  @Override
  public HistoricalJobAdmissionDecision recordExecutionExit(UUID jobId) {
    Objects.requireNonNull(jobId, "jobId");
    var userId = meta.findOwner(jobId);
    if (userId == null) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }
    meta.recordOwner(jobId, userId);
    List<Object> result = scriptGateway.recordExecutionExit(userId, jobId);
    return toDecision(userId, result);
  }

  @Override
  public HistoricalJobAdmissionDecision recordTerminal(UUID jobId) {
    Objects.requireNonNull(jobId, "jobId");
    var userId = meta.findOwner(jobId);
    if (userId == null) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }
    meta.recordOwner(jobId, userId);
    List<Object> result = scriptGateway.recordTerminal(userId, jobId);
    return toDecision(userId, result);
  }

  @Override
  public HistoricalJobAdmissionDecision cleanupAfterRegistrationFailure(UUID jobId) {
    Objects.requireNonNull(jobId, "jobId");
    var userId = meta.findOwner(jobId);
    if (userId == null) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }
    List<HistoricalJobCancellation> jobsToCancel = new ArrayList<>();
    List<HistoricalTracksJobSubmission> jobsToStart = new ArrayList<>();
    queuedPayloads.appendPromotedCleanupStart(
        jobsToStart,
        jobsToCancel,
        userId,
        scriptGateway.cleanupAdmissionState(userId, jobId.toString()));
    return HistoricalJobAdmissionDecision.noop(
        List.copyOf(jobsToStart), List.copyOf(jobsToCancel), false);
  }

  @Override
  public boolean supportsStaleAdmissionRecovery() {
    return true;
  }

  @Override
  public List<HistoricalJobAdmissionDecision> recoverTerminalActiveJobs(
      HistoricalJobStateGateway jobStateGateway, Predicate<UUID> isLocallyTracked) {
    Objects.requireNonNull(jobStateGateway, "jobStateGateway");
    Objects.requireNonNull(isLocallyTracked, "isLocallyTracked");
    List<HistoricalJobAdmissionDecision> decisions = new ArrayList<>();
    for (var entry : activeJobs.scanEntries(jobStateGateway, isLocallyTracked)) {
      appendTerminalRelease(decisions, entry);
    }
    return List.copyOf(decisions);
  }

  @Override
  public List<HistoricalJobAdmissionDecision> recoverOrphanedActiveJobs(
      HistoricalJobStateGateway jobStateGateway,
      Predicate<UUID> isLocallyTracked,
      BiPredicate<UUID, HistoricalJobAdmissionRecoveryState> recoverer) {
    Objects.requireNonNull(jobStateGateway, "jobStateGateway");
    Objects.requireNonNull(isLocallyTracked, "isLocallyTracked");
    Objects.requireNonNull(recoverer, "recoverer");

    List<HistoricalJobAdmissionDecision> decisions = new ArrayList<>();
    for (var entry : activeJobs.scanEntries(jobStateGateway, isLocallyTracked)) {
      appendRecoveryRelease(decisions, entry, recoverer);
    }
    return List.copyOf(decisions);
  }

  private HistoricalJobAdmissionDecision toDecision(UUID userId, List<Object> result) {
    ParsedScriptResult parsed = decisionMapper.parse(result);
    if (parsed.isEmpty()) {
      return HistoricalJobAdmissionDecision.noop(List.of(), List.of(), false);
    }
    List<HistoricalJobCancellation> jobsToCancel =
        queuedPayloads.supersededCancellations(userId, parsed.supersededJobIds());
    List<HistoricalTracksJobSubmission> jobsToStart =
        queuedPayloads.restoreJobsToStart(userId, parsed.jobsToStartIds(), jobsToCancel);
    return decisionMapper.toDecision(parsed, jobsToCancel, jobsToStart);
  }

  private void appendTerminalRelease(
      List<HistoricalJobAdmissionDecision> decisions,
      RedisHistoricalAdmissionActiveJobs.Entry entry) {
    if (entry.state().filter(HistoricalJobState::isTerminal).isPresent()) {
      decisions.add(recordTerminal(entry.jobId()));
    }
  }

  private void appendRecoveryRelease(
      List<HistoricalJobAdmissionDecision> decisions,
      RedisHistoricalAdmissionActiveJobs.Entry entry,
      BiPredicate<UUID, HistoricalJobAdmissionRecoveryState> recoverer) {
    if (entry.state().isEmpty()) {
      decisions.add(recordTerminal(entry.jobId()));
      return;
    }
    if (recoverer.test(entry.jobId(), toRecoveryState(entry.state().get()))) {
      decisions.add(recordTerminal(entry.jobId()));
    }
  }

  private static HistoricalJobAdmissionRecoveryState toRecoveryState(HistoricalJobState state) {
    return new HistoricalJobAdmissionRecoveryState(
        state.isTerminal(),
        state.stage(),
        state.startedAt(),
        state.expiresAt(),
        state.failureReason());
  }

  private String serializePayload(HistoricalTracksJobSubmission job) {
    try {
      return objectMapper.writeValueAsString(job);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize historical job payload", e);
    }
  }
}
