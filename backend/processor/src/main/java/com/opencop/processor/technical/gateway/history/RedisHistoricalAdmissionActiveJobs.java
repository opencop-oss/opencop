// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.history;

import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import com.opencop.processor.ports.technical.gateway.history.HistoricalJobStateGateway;
import com.opencop.processor.technical.gateway.history.model.job.HistoricalJobState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
final class RedisHistoricalAdmissionActiveJobs {

  private static final Logger log =
      LoggerFactory.getLogger(RedisHistoricalAdmissionActiveJobs.class);

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;

  RedisHistoricalAdmissionActiveJobs(
      StringRedisTemplate redisTemplate, HistoricalJobControlKeyLayout keyLayout) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
  }

  List<Entry> scanEntries(
      HistoricalJobStateGateway jobStateGateway, Predicate<UUID> isLocallyTracked) {
    Objects.requireNonNull(jobStateGateway, "jobStateGateway");
    Objects.requireNonNull(isLocallyTracked, "isLocallyTracked");

    List<Entry> entries = new ArrayList<>();
    try (var keys =
        redisTemplate.scan(
            ScanOptions.scanOptions()
                .match(keyLayout.activeRunningScanPattern())
                .count(100)
                .build())) {
      while (keys.hasNext()) {
        collectEntriesFromActiveSet(jobStateGateway, isLocallyTracked, keys.next(), entries);
      }
    }
    return List.copyOf(entries);
  }

  private void collectEntriesFromActiveSet(
      HistoricalJobStateGateway jobStateGateway,
      Predicate<UUID> isLocallyTracked,
      String activeRunningKey,
      List<Entry> entries) {
    var runningJobIds = redisTemplate.opsForSet().members(activeRunningKey);
    if (runningJobIds == null || runningJobIds.isEmpty()) {
      return;
    }
    for (String runningJobId : runningJobIds) {
      UUID jobId = parseUuid(runningJobId);
      if (jobId == null) {
        removeMalformedMember(activeRunningKey, runningJobId);
        continue;
      }
      if (isLocallyTracked.test(jobId)) {
        continue;
      }
      readEntry(jobStateGateway, activeRunningKey, jobId).ifPresent(entries::add);
    }
  }

  private void removeMalformedMember(String activeRunningKey, String runningJobId) {
    redisTemplate.opsForSet().remove(activeRunningKey, runningJobId);
    log.warn(
        "Removed malformed historical admission active-running member {} from {}",
        runningJobId,
        activeRunningKey);
  }

  private Optional<Entry> readEntry(
      HistoricalJobStateGateway jobStateGateway, String activeRunningKey, UUID jobId) {
    try {
      return Optional.of(new Entry(jobId, jobStateGateway.findByJobId(jobId)));
    } catch (DataAccessException e) {
      // A failed Redis command does not establish that stored job state is malformed. Keep the
      // broad Spring category: translated cluster/server failures are not all connection timeouts.
      if (keyLayout == HistoricalJobControlKeyLayout.CLUSTER) {
        log.warn(
            "Skipping historical admission state read after Redis data-access failure; retaining "
                + "active-running member {} ({})",
            jobId,
            e.toString());
        return Optional.empty();
      }
      // Preserve standalone's existing recovery behavior; changing its eviction policy is a
      // separate behavior change from enabling cluster admission.
      removeFailedStateMember(activeRunningKey, jobId, e);
    } catch (RuntimeException e) {
      removeFailedStateMember(activeRunningKey, jobId, e);
    }
    return Optional.empty();
  }

  private void removeFailedStateMember(
      String activeRunningKey, UUID jobId, RuntimeException failure) {
    redisTemplate.opsForSet().remove(activeRunningKey, jobId.toString());
    log.warn(
        "Skipping malformed historical job state during admission recovery: {} ({})",
        jobId,
        failure.toString());
  }

  private static UUID parseUuid(String value) {
    // OpenCOP writes UUID job IDs. This guard is defensive against malformed stored members in the
    // active-job set.
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  record Entry(UUID jobId, Optional<HistoricalJobState> state) {}
}
