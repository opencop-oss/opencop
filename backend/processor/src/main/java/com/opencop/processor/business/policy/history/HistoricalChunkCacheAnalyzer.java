// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import com.opencop.processor.technical.observability.history.HistoricalObservability.CacheResult;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class HistoricalChunkCacheAnalyzer {

  private static final Duration STALE_CACHE_THRESHOLD = Duration.ofMinutes(5);

  private final HistoricalChunkPayloadCacheGateway payloadCacheGateway;
  private final HistoricalChunkStateGateway chunkStateGateway;
  private final HistoricalCacheIdentity cacheIdentity;
  private final HistoricalObservability historicalObservability;

  public HistoricalChunkCacheAnalyzer(
      HistoricalChunkPayloadCacheGateway payloadCacheGateway,
      HistoricalChunkStateGateway chunkStateGateway,
      HistoricalCacheIdentity cacheIdentity,
      HistoricalObservability historicalObservability) {
    this.payloadCacheGateway = payloadCacheGateway;
    this.chunkStateGateway = chunkStateGateway;
    this.cacheIdentity = cacheIdentity;
    this.historicalObservability = historicalObservability;
  }

  public HistoricalChunkPlan analyze(
      HistoricalChunkPlan plan, HistoricalSubjectExecutionMeta laneMetadata) {
    List<HistoricalChunkPlanEntry> analyzedEntries =
        attachCacheKeys(
            plan,
            laneMetadata,
            payloadCacheGateway.remainingPayloadTtl(payloadKeys(plan, laneMetadata)));
    return plan.withEntries(analyzedEntries);
  }

  private List<HistoricalChunkPlanEntry> attachCacheKeys(
      HistoricalChunkPlan plan,
      HistoricalSubjectExecutionMeta laneMetadata,
      Map<String, Optional<Duration>> payloadTtls) {
    return plan.entries().stream()
        .map(
            entry -> {
              String payloadKey = cacheIdentity.payloadKey(laneMetadata, entry.from());
              String chunkStateKey = cacheIdentity.chunkStateKey(laneMetadata, entry.from());
              HistoricalChunkCacheStatus cacheStatus =
                  resolveCacheStatus(payloadKey, chunkStateKey, payloadTtls);
              if (historicalObservability != null) {
                historicalObservability.recordChunkCache(
                    cacheStatus == HistoricalChunkCacheStatus.CACHED
                        ? CacheResult.HIT
                        : CacheResult.MISS);
              }
              return entry.withCacheAnalysis(payloadKey, chunkStateKey, cacheStatus);
            })
        .toList();
  }

  /**
   * A chunk is a cache hit only when every part of its payload is present in Redis with a remaining
   * TTL above the stale threshold. Keys whose TTL is below the threshold are treated as missing so
   * the chunk is recomputed and re-persisted with a fresh TTL.
   */
  private HistoricalChunkCacheStatus resolveCacheStatus(
      String payloadKey, String chunkStateKey, Map<String, Optional<Duration>> payloadTtls) {
    if (!isFresh(payloadKey, payloadTtls)) {
      return HistoricalChunkCacheStatus.MISSING;
    }
    int partCount =
        chunkStateGateway
            .findByChunkStateKey(chunkStateKey)
            .map(state -> Math.max(1, state.partCount()))
            .orElse(1);
    if (partCount <= 1) {
      return HistoricalChunkCacheStatus.CACHED;
    }
    Set<String> partKeys = allPartKeys(payloadKey, partCount);
    Map<String, Optional<Duration>> partTtls = payloadCacheGateway.remainingPayloadTtl(partKeys);
    boolean allPartsFresh = partKeys.stream().allMatch(k -> isFresh(k, partTtls));
    return allPartsFresh ? HistoricalChunkCacheStatus.CACHED : HistoricalChunkCacheStatus.MISSING;
  }

  private static boolean isFresh(String key, Map<String, Optional<Duration>> ttls) {
    if (!ttls.containsKey(key)) {
      return false;
    }
    Optional<Duration> ttl = ttls.get(key);
    if (ttl.isEmpty()) {
      return true;
    }
    return ttl.get().compareTo(STALE_CACHE_THRESHOLD) >= 0;
  }

  private Set<String> allPartKeys(String payloadKey, int partCount) {
    Set<String> keys = new LinkedHashSet<>();
    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      keys.add(cacheIdentity.payloadPartKey(payloadKey, partIndex));
    }
    return keys;
  }

  private LinkedHashSet<String> payloadKeys(
      HistoricalChunkPlan plan, HistoricalSubjectExecutionMeta laneMetadata) {
    return plan.entries().stream()
        .map(entry -> cacheIdentity.payloadKey(laneMetadata, entry.from()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
