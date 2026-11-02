// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway.history;

import com.opencop.core.api.event.ports.gateway.history.HistoricalTracksAdmissionGateway;
import com.opencop.core.sdk.model.history.HistoricalJobCancellationReasons;
import com.opencop.core.sdk.technical.gateway.history.HistoricalJobControlKeyLayout;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisHistoricalTracksAdmissionGateway implements HistoricalTracksAdmissionGateway {

  private static final String FIELD_REQUESTED_BY = "requestedBy";
  private static final String FIELD_STATUS = "status";
  private static final String FIELD_REQUEST_ID = "requestId";
  private static final String FIELD_STAGE = "stage";
  private static final String FIELD_FAILURE_REASON = "failureReason";
  private static final String FIELD_UPDATED_AT = "updatedAt";
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_FAILED = "FAILED";
  private static final String STATUS_CANCELLED = "CANCELLED";
  private static final String CANCELLATION_REQUESTED_STAGE = "CANCELLATION_REQUESTED";

  private final StringRedisTemplate redisTemplate;
  private final HistoricalJobControlKeyLayout keyLayout;
  private final RedisScript<Long> cancelJobScript;

  /**
   * Creates a standalone adapter for direct test construction. Production callers use the
   * constructor accepting the topology-selected key layout.
   */
  public RedisHistoricalTracksAdmissionGateway(
      StringRedisTemplate redisTemplate, CoreHistoricalRedisScripts scripts) {
    this(redisTemplate, HistoricalJobControlKeyLayout.STANDALONE, scripts);
  }

  @Autowired
  public RedisHistoricalTracksAdmissionGateway(
      StringRedisTemplate redisTemplate,
      HistoricalJobControlKeyLayout keyLayout,
      CoreHistoricalRedisScripts scripts) {
    this.redisTemplate = redisTemplate;
    this.keyLayout = Objects.requireNonNull(keyLayout, "keyLayout");
    this.cancelJobScript = scripts.cancelJobIfActiveAndOwned();
  }

  public int requestCancellationForActiveJobs(UUID requestedBy, UUID replacementRequestId) {
    Objects.requireNonNull(requestedBy, "requestedBy");
    Objects.requireNonNull(replacementRequestId, "replacementRequestId");
    String cancellationReason =
        HistoricalJobCancellationReasons.supersededByRequest(replacementRequestId);
    String now = Instant.now().toString();
    int changedJobs = 0;
    String userIndexKey = keyLayout.userJobIndex(requestedBy);
    Set<String> indexedJobIds = redisTemplate.opsForZSet().range(userIndexKey, 0, -1);
    if (indexedJobIds == null || indexedJobIds.isEmpty()) {
      return 0;
    }
    for (String jobId : indexedJobIds) {
      UUID parsedJobId;
      try {
        parsedJobId = UUID.fromString(jobId);
      } catch (IllegalArgumentException ignored) {
        redisTemplate.opsForZSet().remove(userIndexKey, jobId);
        continue;
      }
      String jobHashKey = keyLayout.jobState(parsedJobId);
      if (!Boolean.TRUE.equals(redisTemplate.hasKey(jobHashKey))) {
        redisTemplate.opsForZSet().remove(userIndexKey, jobId);
        continue;
      }
      Long changed =
          redisTemplate.execute(
              cancelJobScript,
              List.of(jobHashKey),
              FIELD_REQUESTED_BY,
              requestedBy.toString(),
              FIELD_STATUS,
              STATUS_COMPLETED,
              STATUS_FAILED,
              STATUS_CANCELLED,
              FIELD_REQUEST_ID,
              replacementRequestId.toString(),
              FIELD_STAGE,
              CANCELLATION_REQUESTED_STAGE,
              FIELD_FAILURE_REASON,
              cancellationReason,
              FIELD_UPDATED_AT,
              now);
      if (changed != null) {
        changedJobs += changed.intValue();
      }
    }
    return changedJobs;
  }
}
