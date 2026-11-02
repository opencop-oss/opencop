// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ActiveSnapshotReadOutcome;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ActiveSnapshotRowKind;
import com.opencop.processor.technical.observability.history.HistoricalObservability.CacheResult;
import com.opencop.processor.technical.observability.history.HistoricalObservability.JobStage;
import com.opencop.processor.technical.observability.history.HistoricalObservability.ReadPath;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HistoricalObservabilityTest {
  private static final String CHUNK_CACHE_TOTAL = "opencop_history_chunk_cache_total";
  private static final String CHUNK_CANCELLED_TOTAL = "opencop_history_chunk_cancelled_total";
  private static final String HISTORICAL_READ_TOTAL = "opencop_history_read_total";
  private static final String HISTORICAL_READ_PAGES_TOTAL = "opencop_history_read_pages_total";
  private static final String HISTORICAL_READ_ROWS_TOTAL = "opencop_history_read_rows_total";
  private static final String ACTIVE_SNAPSHOT_READ_TOTAL =
      "opencop_history_active_snapshot_read_total";
  private static final String ACTIVE_SNAPSHOT_READ_DURATION =
      "opencop_history_active_snapshot_read_duration_seconds";
  private static final String ACTIVE_SNAPSHOT_ROWS_TOTAL =
      "opencop_history_active_snapshot_rows_total";
  private static final String RESULT_TAG = "result";
  private static final String PATH_TAG = "path";
  private static final String OUTCOME_TAG = "outcome";
  private static final String ROW_KIND_TAG = "row_kind";
  private static final String REASON_TAG = "reason";
  private static final String HIT_RESULT = "hit";
  private static final String MISS_RESULT = "miss";
  private static final String JOB_CANCELLED_REASON = "job_cancelled";

  @Test
  void recordJobStage_recordsStageOutcomeTimerWithoutLegacyTransitionTags() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordJobStage("PLANNING", "success", Duration.ofMillis(125));

    var counter =
        registry
            .find("opencop_history_job_stage_total")
            .tags("stage", "PLANNING", "outcome", "success")
            .counter();
    var timer =
        registry
            .find("opencop_history_job_stage_duration_seconds")
            .tags("stage", "PLANNING", "outcome", "success")
            .timer();

    assertNotNull(counter);
    assertNotNull(timer);
    assertEquals(1.0d, counter.count());
    assertEquals(1L, timer.count());
    assertTrue(
        registry.find("opencop_history_job_stage_duration_seconds").meters().stream()
            .flatMap(meter -> meter.getId().getTags().stream())
            .noneMatch(
                tag -> tag.getKey().equals("from_stage") || tag.getKey().equals("to_stage")));
  }

  @Test
  void recordJobStage_whenStageIsUnknown_doesNotRecordAnyJobStageMeter() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    assertThrows(
        IllegalArgumentException.class,
        () -> observability.recordJobStage("UNKNOWN_STAGE", "success", Duration.ofMillis(1)));

    assertTrue(registry.find("opencop_history_job_stage_total").meters().isEmpty());
    assertTrue(registry.find("opencop_history_job_stage_duration_seconds").meters().isEmpty());
  }

  @Test
  void recordJobStage_acceptsReplacementAndCancellationStageValuesWithBoundedTags() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    JobStage[] boundedStages = {
      JobStage.CANCELLATION_REQUESTED,
      JobStage.CANCELLED,
      JobStage.REPLACEMENT_QUEUED,
      JobStage.REPLACEMENT_SUPERSEDED,
      JobStage.REPLACEMENT_STARTED
    };

    for (JobStage stage : boundedStages) {
      observability.recordJobStage(stage, Outcome.SUCCESS, Duration.ofMillis(5));
      var counter =
          registry
              .find("opencop_history_job_stage_total")
              .tags("stage", stage.value(), "outcome", "success")
              .counter();
      var timer =
          registry
              .find("opencop_history_job_stage_duration_seconds")
              .tags("stage", stage.value(), "outcome", "success")
              .timer();
      assertNotNull(counter);
      assertNotNull(timer);
      assertEquals(1.0d, counter.count());
      assertEquals(1L, timer.count());
    }
  }

  @Test
  void recordChunkCache_recordsHitAndMissWithOnlyTheResultTag() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordChunkCache(CacheResult.HIT);
    observability.recordChunkCache(CacheResult.MISS);

    assertEquals(
        1.0d, registry.find(CHUNK_CACHE_TOTAL).tags(RESULT_TAG, HIT_RESULT).counter().count());
    assertEquals(
        1.0d, registry.find(CHUNK_CACHE_TOTAL).tags(RESULT_TAG, MISS_RESULT).counter().count());
    assertTrue(
        registry.find(CHUNK_CACHE_TOTAL).meters().stream()
            .allMatch(
                meter ->
                    meter.getId().getTags().size() == 1
                        && meter.getId().getTag(RESULT_TAG) != null));
  }

  @Test
  void recordChunkCompute_recordsTimer() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordChunkCompute("success", "true", Duration.ofMillis(12));

    var timer =
        registry
            .find("opencop_history_chunk_compute_duration_seconds")
            .tags("outcome", "success", "cached", "true")
            .timer();
    assertNotNull(timer);
    assertEquals(1L, timer.count());
  }

  @Test
  void recordChunkCancellation_recordsBoundedCancellationReasonAndCount() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordChunkCancellation(JOB_CANCELLED_REASON, 2);

    assertEquals(
        2.0d,
        registry
            .find(CHUNK_CANCELLED_TOTAL)
            .tags(REASON_TAG, JOB_CANCELLED_REASON)
            .counter()
            .count());
  }

  @Test
  void recordHistoricalRead_recordsBoundedPathAndPageRowCounters() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordHistoricalRead(ReadPath.ACTIVE_HELPER, 2, 501);
    observability.recordHistoricalRead(ReadPath.DELTA_EVENT, 1, 23);
    observability.recordHistoricalRead(ReadPath.DELTA_STATE, 1, 4);

    assertEquals(
        1.0d,
        registry.find(HISTORICAL_READ_TOTAL).tags(PATH_TAG, "ACTIVE_HELPER").counter().count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(PATH_TAG, "DELTA_EVENT").counter().count());
    assertEquals(
        1.0d, registry.find(HISTORICAL_READ_TOTAL).tags(PATH_TAG, "DELTA_STATE").counter().count());
    assertEquals(
        2.0d, registry.find(HISTORICAL_READ_TOTAL).tags(PATH_TAG, "DELTA").counter().count());
    assertEquals(
        2.0d,
        registry
            .find(HISTORICAL_READ_PAGES_TOTAL)
            .tags(PATH_TAG, "ACTIVE_HELPER")
            .counter()
            .count());
    assertEquals(
        501.0d,
        registry
            .find(HISTORICAL_READ_ROWS_TOTAL)
            .tags(PATH_TAG, "ACTIVE_HELPER")
            .counter()
            .count());
    assertEquals(
        23.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(PATH_TAG, "DELTA_EVENT").counter().count());
    assertEquals(
        4.0d,
        registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(PATH_TAG, "DELTA_STATE").counter().count());
    assertEquals(
        27.0d, registry.find(HISTORICAL_READ_ROWS_TOTAL).tags(PATH_TAG, "DELTA").counter().count());
  }

  @Test
  void recordHistoricalRead_rejectsInvalidPathAndDoesNotCreateMeter() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    assertThrows(
        IllegalArgumentException.class,
        () -> observability.recordHistoricalRead("ACTIVE_HELPER ", 1, 1));

    assertTrue(registry.find(HISTORICAL_READ_TOTAL).meters().isEmpty());
    assertTrue(registry.find(HISTORICAL_READ_PAGES_TOTAL).meters().isEmpty());
    assertTrue(registry.find(HISTORICAL_READ_ROWS_TOTAL).meters().isEmpty());
  }

  @Test
  void recordActiveSnapshotMetrics_recordsStartedCounterTerminalTimerAndRowKindCounters() {
    var registry = new SimpleMeterRegistry();
    var observability = new HistoricalObservability(registry);

    observability.recordActiveSnapshotReadStarted(ReadPath.ACTIVE_HELPER);
    observability.recordActiveSnapshotReadDuration(
        ReadPath.ACTIVE_RESIDUAL, ActiveSnapshotReadOutcome.SUCCESS, Duration.ofMillis(37));
    observability.recordActiveSnapshotRows(
        ReadPath.ACTIVE_RESIDUAL, ActiveSnapshotRowKind.STABLE, 7);
    observability.recordActiveSnapshotRows(
        ReadPath.ACTIVE_RESIDUAL, ActiveSnapshotRowKind.RESIDUAL, 3);

    assertEquals(
        1.0d,
        registry
            .find(ACTIVE_SNAPSHOT_READ_TOTAL)
            .tags(PATH_TAG, "ACTIVE_HELPER", OUTCOME_TAG, "started")
            .counter()
            .count());
    assertEquals(
        1L,
        registry
            .find(ACTIVE_SNAPSHOT_READ_DURATION)
            .tags(PATH_TAG, "ACTIVE_RESIDUAL", OUTCOME_TAG, "success")
            .timer()
            .count());
    assertEquals(
        7.0d,
        registry
            .find(ACTIVE_SNAPSHOT_ROWS_TOTAL)
            .tags(PATH_TAG, "ACTIVE_RESIDUAL", ROW_KIND_TAG, "stable")
            .counter()
            .count());
    assertEquals(
        3.0d,
        registry
            .find(ACTIVE_SNAPSHOT_ROWS_TOTAL)
            .tags(PATH_TAG, "ACTIVE_RESIDUAL", ROW_KIND_TAG, "residual")
            .counter()
            .count());
  }
}
