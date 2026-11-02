// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.opencop.core.sdk.model.SubjectType;
import com.opencop.core.sdk.model.history.HistoricalResolution;
import com.opencop.core.sdk.model.job.JobIssuer;
import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkCacheStatus;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkWindowPlan;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkPayloadCacheGateway;
import com.opencop.processor.ports.technical.gateway.history.HistoricalChunkStateGateway;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkState;
import com.opencop.processor.technical.gateway.history.model.chunk.HistoricalChunkStateStatus;
import com.opencop.processor.technical.observability.history.HistoricalObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoricalChunkCacheAnalyzerTest {
  private static final String CHUNK_CACHE_TOTAL = "opencop_history_chunk_cache_total";
  private static final String RESULT_TAG = "result";
  private static final String HIT_RESULT = "hit";
  private static final String MISS_RESULT = "miss";
  private static final String REMOVED_CACHE_TAG_TYPE = "Cache" + "TagValue";

  private final HistoricalCacheIdentity cacheIdentity =
      new HistoricalCacheIdentity(new tools.jackson.databind.ObjectMapper());
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final HistoricalObservability historicalObservability =
      new HistoricalObservability(meterRegistry);

  private final StubPayloadCacheGateway payloadCacheGateway = new StubPayloadCacheGateway();
  private final StubChunkStateGateway chunkStateGateway = new StubChunkStateGateway();

  private final HistoricalChunkCacheAnalyzer analyzer =
      new HistoricalChunkCacheAnalyzer(
          payloadCacheGateway, chunkStateGateway, cacheIdentity, historicalObservability);

  // ---------------------------------------------------------------------------
  // Happy path: single-part chunk with its base key present → cache hit
  // ---------------------------------------------------------------------------

  @Test
  void returnsHitWhenAllPartsPresent() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:02:00Z"), Instant.parse("2026-04-06T10:03:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());

    // partCount=1 stored in state; base key present in cache
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 1));
    payloadCacheGateway.markPresent(payloadKey);

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.CACHED,
        analyzed.entries().getFirst().cacheStatus(),
        "Single-part chunk with base key present must be a cache hit");
    assertEquals(
        1.0d,
        meterRegistry
            .find("opencop_history_chunk_cache_total")
            .tags("result", "hit")
            .counter()
            .count());
  }

  // ---------------------------------------------------------------------------
  // Negative path: base key absent → cache miss regardless of state
  // ---------------------------------------------------------------------------

  @Test
  void returnsMissWhenBaseKeyAbsent() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:03:00Z"), Instant.parse("2026-04-06T10:04:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    // No keys present in cache; state says partCount=1
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());
    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 1));
    // payloadCacheGateway has no entry → base key absent

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.MISSING,
        analyzed.entries().getFirst().cacheStatus(),
        "Chunk whose base key is absent must be a cache miss");
  }

  // ---------------------------------------------------------------------------
  // Negative path: 3-part chunk with middle part absent → cache miss
  // ---------------------------------------------------------------------------

  @Test
  void returnsMissWhen3PartChunkHasPart1Absent() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:04:00Z"), Instant.parse("2026-04-06T10:05:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());

    // State records partCount=3; parts 0 and 2 are present but part 1 is absent
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 3));
    payloadCacheGateway.markPresent(cacheIdentity.payloadPartKey(payloadKey, 0)); // base key
    // part 1 deliberately absent
    payloadCacheGateway.markPresent(cacheIdentity.payloadPartKey(payloadKey, 2));

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.MISSING,
        analyzed.entries().getFirst().cacheStatus(),
        "3-part chunk with part 1 absent must be treated as a cache miss");
    assertEquals(
        1.0d,
        meterRegistry.find(CHUNK_CACHE_TOTAL).tags(RESULT_TAG, MISS_RESULT).counter().count());
  }

  @Test
  void recordsOneResultTagForCachedAndMissingEntries() {
    var metadata = metadata();
    var cachedEntry =
        bareEntry(0, Instant.parse("2026-04-06T10:05:00Z"), Instant.parse("2026-04-06T10:06:00Z"));
    var missingEntry =
        bareEntry(1, Instant.parse("2026-04-06T10:06:00Z"), Instant.parse("2026-04-06T10:07:00Z"));
    var plan =
        HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(cachedEntry, missingEntry)));

    String cachedPayloadKey = cacheIdentity.payloadKey(metadata, cachedEntry.from());
    String cachedStateKey = cacheIdentity.chunkStateKey(metadata, cachedEntry.from());
    chunkStateGateway.store(
        cachedStateKey, stateWith(plan.jobId(), cachedEntry, cachedPayloadKey, cachedStateKey, 1));
    payloadCacheGateway.markPresent(cachedPayloadKey);

    String missingPayloadKey = cacheIdentity.payloadKey(metadata, missingEntry.from());
    String missingStateKey = cacheIdentity.chunkStateKey(metadata, missingEntry.from());
    chunkStateGateway.store(
        missingStateKey,
        stateWith(plan.jobId(), missingEntry, missingPayloadKey, missingStateKey, 1));

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(HistoricalChunkCacheStatus.CACHED, analyzed.entries().get(0).cacheStatus());
    assertEquals(HistoricalChunkCacheStatus.MISSING, analyzed.entries().get(1).cacheStatus());
    assertEquals(
        1.0d, meterRegistry.find(CHUNK_CACHE_TOTAL).tags(RESULT_TAG, HIT_RESULT).counter().count());
    assertEquals(
        1.0d,
        meterRegistry.find(CHUNK_CACHE_TOTAL).tags(RESULT_TAG, MISS_RESULT).counter().count());
    assertEquals(
        2,
        meterRegistry.find(CHUNK_CACHE_TOTAL).meters().stream()
            .filter(meter -> meter.getId().getTags().size() == 1)
            .filter(meter -> meter.getId().getTag(RESULT_TAG) != null)
            .count());
    assertEquals(
        0,
        HistoricalObservability.class.getDeclaredClasses().length == 0
            ? 0
            : List.of(HistoricalObservability.class.getDeclaredClasses()).stream()
                .filter(type -> type.getSimpleName().equals(REMOVED_CACHE_TAG_TYPE))
                .count());
  }

  @Test
  void returnsMissWhenBaseKeyTtlBelowStaleThreshold() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:07:00Z"), Instant.parse("2026-04-06T10:08:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 1));
    payloadCacheGateway.markPresentWithTtl(payloadKey, Duration.ofMinutes(4));

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.MISSING,
        analyzed.entries().getFirst().cacheStatus(),
        "Chunk whose cached payload TTL is below the stale threshold must be a cache miss");
  }

  @Test
  void returnsHitWhenBaseKeyTtlAtOrAboveStaleThreshold() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:08:00Z"), Instant.parse("2026-04-06T10:09:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 1));
    payloadCacheGateway.markPresentWithTtl(payloadKey, Duration.ofMinutes(5));

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(HistoricalChunkCacheStatus.CACHED, analyzed.entries().getFirst().cacheStatus());
  }

  @Test
  void returnsMissWhenAnyPartTtlBelowStaleThreshold() {
    var metadata = metadata();
    var entry =
        bareEntry(0, Instant.parse("2026-04-06T10:09:00Z"), Instant.parse("2026-04-06T10:10:00Z"));
    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan(List.of(entry)));

    String payloadKey = cacheIdentity.payloadKey(metadata, entry.from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, entry.from());
    chunkStateGateway.store(stateKey, stateWith(plan.jobId(), entry, payloadKey, stateKey, 3));
    payloadCacheGateway.markPresent(payloadKey);
    payloadCacheGateway.markPresent(cacheIdentity.payloadPartKey(payloadKey, 0));
    payloadCacheGateway.markPresentWithTtl(
        cacheIdentity.payloadPartKey(payloadKey, 1), Duration.ofMinutes(2));
    payloadCacheGateway.markPresent(cacheIdentity.payloadPartKey(payloadKey, 2));

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.MISSING,
        analyzed.entries().getFirst().cacheStatus(),
        "Multi-part chunk with any near-expiry part must be a cache miss");
  }

  @Test
  void analyzeLaneUsesMetadataForCacheKeyConstruction() {
    var metadata = metadata();
    var windowPlan =
        new HistoricalChunkWindowPlan(
            metadata.requestedFrom(),
            metadata.requestedTo(),
            1,
            metadata.resolution(),
            List.of(
                bareEntry(
                    0,
                    Instant.parse("2026-04-06T10:02:00Z"),
                    Instant.parse("2026-04-06T10:03:00Z"))));

    var plan = HistoricalChunkPlan.forSubject(metadata, windowPlan);

    String payloadKey = cacheIdentity.payloadKey(metadata, plan.entries().getFirst().from());
    String stateKey = cacheIdentity.chunkStateKey(metadata, plan.entries().getFirst().from());

    chunkStateGateway.store(
        stateKey, stateWith(plan.jobId(), plan.entries().getFirst(), payloadKey, stateKey, 1));
    payloadCacheGateway.markPresent(payloadKey);

    HistoricalChunkPlan analyzed = analyzer.analyze(plan, metadata);

    assertEquals(
        HistoricalChunkCacheStatus.CACHED,
        analyzed.entries().getFirst().cacheStatus(),
        "Metadata-based analysis must resolve cache identity from lane metadata");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static HistoricalChunkPlanEntry bareEntry(int index, Instant from, Instant to) {
    return new HistoricalChunkPlanEntry(index, "chunk-" + index, null, null, null, from, to);
  }

  private static HistoricalChunkWindowPlan windowPlan(List<HistoricalChunkPlanEntry> entries) {
    return new HistoricalChunkWindowPlan(
        entries.getFirst().from(),
        entries.getLast().to(),
        entries.size(),
        HistoricalResolution.MINUTE_1,
        entries);
  }

  private static HistoricalSubjectExecutionMeta metadata() {
    return new HistoricalSubjectExecutionMeta(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440131"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440132"),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        JobIssuer.user(UUID.fromString("550e8400-e29b-41d4-a716-446655440120")),
        UUID.fromString("550e8400-e29b-41d4-a716-446655440120"),
        Instant.parse("2026-04-07T10:00:00Z"),
        Instant.parse("2026-04-06T10:02:17Z"),
        Instant.parse("2026-04-06T12:13:02Z"),
        Instant.parse("2026-04-06T10:02:00Z"),
        Instant.parse("2026-04-06T12:14:00Z"),
        HistoricalResolution.MINUTE_1,
        132,
        List.of(),
        SubjectType.SOURCE,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440122"),
        new HistoricalChunkWindowPlan(
            Instant.parse("2026-04-06T10:02:00Z"),
            Instant.parse("2026-04-06T12:14:00Z"),
            1,
            HistoricalResolution.MINUTE_1,
            List.of()));
  }

  private static HistoricalChunkState stateWith(
      UUID jobId,
      HistoricalChunkPlanEntry entry,
      String payloadKey,
      String chunkStateKey,
      int partCount) {
    Instant now = Instant.parse("2026-04-06T10:00:00Z");
    return HistoricalChunkState.initialize(
        jobId,
        entry.chunkIndex(),
        entry.chunkKey(),
        payloadKey,
        chunkStateKey,
        partCount,
        HistoricalChunkStateStatus.CACHED,
        entry.from(),
        entry.to() != null ? entry.to() : entry.from().plusSeconds(60),
        now,
        Duration.ofHours(24));
  }

  // ---------------------------------------------------------------------------
  // Stubs
  // ---------------------------------------------------------------------------

  private static final class StubPayloadCacheGateway implements HistoricalChunkPayloadCacheGateway {
    private final Map<String, Optional<Duration>> present = new HashMap<>();

    void markPresent(String key) {
      present.put(key, Optional.empty());
    }

    void markPresentWithTtl(String key, Duration remainingTtl) {
      present.put(key, Optional.of(remainingTtl));
    }

    @Override
    public Map<String, Optional<Duration>> remainingPayloadTtl(Set<String> payloadKeys) {
      Map<String, Optional<Duration>> result = new LinkedHashMap<>();
      for (String key : payloadKeys) {
        if (present.containsKey(key)) {
          result.put(key, present.get(key));
        }
      }
      return result;
    }

    @Override
    public void put(String payloadKey, byte[] payloadBytes, Duration ttl) {}

    @Override
    public Optional<byte[]> find(String payloadKey) {
      return Optional.empty();
    }

    @Override
    public java.util.Map<String, Optional<byte[]>> findAll(List<String> payloadKeys) {
      return Map.of();
    }
  }

  private static final class StubChunkStateGateway implements HistoricalChunkStateGateway {
    private final Map<String, HistoricalChunkState> states = new HashMap<>();

    void store(String chunkStateKey, HistoricalChunkState state) {
      states.put(chunkStateKey, state);
    }

    @Override
    public void initialize(HistoricalChunkPlan plan) {}

    @Override
    public Optional<HistoricalChunkState> findByChunkStateKey(String chunkStateKey) {
      return Optional.ofNullable(states.get(chunkStateKey));
    }

    @Override
    public void transition(String chunkStateKey, HistoricalChunkStateStatus status) {}

    @Override
    public void updatePartCount(String chunkStateKey, int partCount) {}
  }
}
