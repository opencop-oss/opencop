// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.history;

import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import com.opencop.core.sdk.observability.BoundedMetricTagValue;
import com.opencop.core.sdk.observability.history.HistoricalTelemetry.Outcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalObservability {
  private static final String JOB_STAGE_TOTAL = "opencop_history_job_stage_total";
  private static final String JOB_STAGE_DURATION = "opencop_history_job_stage_duration_seconds";
  private static final String CHUNK_COMPUTE_DURATION =
      "opencop_history_chunk_compute_duration_seconds";
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

  private static final String TAG_STAGE = "stage";
  private static final String TAG_OUTCOME = "outcome";
  private static final String TAG_CACHED = "cached";
  private static final String TAG_RESULT = "result";
  private static final String TAG_REASON = "reason";
  private static final String TAG_PATH = "path";
  private static final String TAG_ROW_KIND = "row_kind";

  private static final Set<String> STAGE_VALUES =
      BoundedMetricTagGuard.allowedValues(JobStage.values());
  private static final Set<String> OUTCOME_VALUES =
      BoundedMetricTagGuard.allowedValues(Outcome.values());
  private static final Set<String> CACHED_VALUES =
      BoundedMetricTagGuard.allowedValues(Cached.values());
  private static final Set<String> RESULT_VALUES =
      BoundedMetricTagGuard.allowedValues(CacheResult.values());
  private static final Set<String> REASON_VALUES =
      BoundedMetricTagGuard.allowedValues(CancellationReason.values());
  private static final Set<String> READ_PATH_VALUES =
      BoundedMetricTagGuard.allowedValues(ReadPath.values());
  private static final Set<String> ACTIVE_SNAPSHOT_OUTCOME_VALUES =
      BoundedMetricTagGuard.allowedValues(ActiveSnapshotReadOutcome.values());
  private static final Set<String> ACTIVE_SNAPSHOT_ROW_KIND_VALUES =
      BoundedMetricTagGuard.allowedValues(ActiveSnapshotRowKind.values());

  private final MeterRegistry meterRegistry;

  public HistoricalObservability(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordJobStage(JobStage stage, Outcome outcome, Duration duration) {
    recordJobStage(stage.value(), outcome.value(), duration);
  }

  void recordJobStage(String stage, String outcome, Duration duration) {
    List<Tag> tags =
        List.of(
            Tag.of(TAG_STAGE, requireBounded(TAG_STAGE, STAGE_VALUES, stage)),
            Tag.of(TAG_OUTCOME, requireBounded(TAG_OUTCOME, OUTCOME_VALUES, outcome)));
    meterRegistry.counter(JOB_STAGE_TOTAL, tags).increment();
    meterRegistry.timer(JOB_STAGE_DURATION, tags).record(duration);
  }

  public void recordChunkCache(CacheResult result) {
    recordChunkCache(result.value());
  }

  void recordChunkCache(String result) {
    List<Tag> tags = List.of(Tag.of(TAG_RESULT, requireBounded(TAG_RESULT, RESULT_VALUES, result)));
    meterRegistry.counter(CHUNK_CACHE_TOTAL, tags).increment();
  }

  /**
   * Records the timer for a chunk compute attempt. The timer's {@code count} is the source of truth
   * for the total number of compute attempts — no separate {@code _total} counter is emitted.
   */
  public void recordChunkCompute(Outcome outcome, Cached cached, Duration duration) {
    recordChunkCompute(outcome.value(), cached.value(), duration);
  }

  void recordChunkCompute(String outcome, String cached, Duration duration) {
    List<Tag> tags =
        List.of(
            Tag.of(TAG_OUTCOME, requireBounded(TAG_OUTCOME, OUTCOME_VALUES, outcome)),
            Tag.of(TAG_CACHED, requireBounded(TAG_CACHED, CACHED_VALUES, cached)));
    meterRegistry.timer(CHUNK_COMPUTE_DURATION, tags).record(duration);
  }

  public void recordChunkCancellation(CancellationReason reason, int count) {
    recordChunkCancellation(reason.value(), count);
  }

  void recordChunkCancellation(String reason, int count) {
    List<Tag> tags = List.of(Tag.of(TAG_REASON, requireBounded(TAG_REASON, REASON_VALUES, reason)));
    meterRegistry.counter(CHUNK_CANCELLED_TOTAL, tags).increment(count);
  }

  public void recordHistoricalRead(ReadPath path, int pageCount, int rowCount) {
    recordHistoricalRead(path.value(), pageCount, rowCount);
    if (path == ReadPath.DELTA_EVENT || path == ReadPath.DELTA_STATE) {
      recordHistoricalRead(ReadPath.DELTA.value(), pageCount, rowCount);
    }
  }

  void recordHistoricalRead(String path, int pageCount, int rowCount) {
    List<Tag> tags = List.of(Tag.of(TAG_PATH, requireBounded(TAG_PATH, READ_PATH_VALUES, path)));
    meterRegistry.counter(HISTORICAL_READ_TOTAL, tags).increment();
    meterRegistry.counter(HISTORICAL_READ_PAGES_TOTAL, tags).increment(pageCount);
    meterRegistry.counter(HISTORICAL_READ_ROWS_TOTAL, tags).increment(rowCount);
  }

  public void recordActiveSnapshotReadStarted(ReadPath path) {
    List<Tag> tags =
        List.of(
            Tag.of(TAG_PATH, requireBounded(TAG_PATH, READ_PATH_VALUES, path.value())),
            Tag.of(
                TAG_OUTCOME,
                requireBounded(
                    TAG_OUTCOME,
                    ACTIVE_SNAPSHOT_OUTCOME_VALUES,
                    ActiveSnapshotReadOutcome.STARTED.value())));
    meterRegistry.counter(ACTIVE_SNAPSHOT_READ_TOTAL, tags).increment();
  }

  public void recordActiveSnapshotReadDuration(
      ReadPath path, ActiveSnapshotReadOutcome outcome, Duration duration) {
    List<Tag> tags =
        List.of(
            Tag.of(TAG_PATH, requireBounded(TAG_PATH, READ_PATH_VALUES, path.value())),
            Tag.of(
                TAG_OUTCOME,
                requireBounded(TAG_OUTCOME, ACTIVE_SNAPSHOT_OUTCOME_VALUES, outcome.value())));
    meterRegistry.timer(ACTIVE_SNAPSHOT_READ_DURATION, tags).record(duration);
  }

  public void recordActiveSnapshotRows(ReadPath path, ActiveSnapshotRowKind rowKind, int rowCount) {
    if (rowCount == 0) {
      return;
    }
    List<Tag> tags =
        List.of(
            Tag.of(TAG_PATH, requireBounded(TAG_PATH, READ_PATH_VALUES, path.value())),
            Tag.of(
                TAG_ROW_KIND,
                requireBounded(TAG_ROW_KIND, ACTIVE_SNAPSHOT_ROW_KIND_VALUES, rowKind.value())));
    meterRegistry.counter(ACTIVE_SNAPSHOT_ROWS_TOTAL, tags).increment(rowCount);
  }

  private static String requireBounded(String tagKey, Set<String> allowed, String value) {
    return BoundedMetricTagGuard.requireAllowed(tagKey, value, allowed);
  }

  public enum JobStage implements BoundedMetricTagValue {
    PLANNING,
    QUEUED,
    COMPLETED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    REPLACEMENT_QUEUED,
    REPLACEMENT_SUPERSEDED,
    REPLACEMENT_STARTED,
    INGRESS_REJECTED,
    EXECUTION_FAILED,
    TIMEOUT;

    @Override
    public String value() {
      return name();
    }
  }

  public enum Cached implements BoundedMetricTagValue {
    TRUE("true"),
    FALSE("false");

    private final String value;

    Cached(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }

  public enum CacheResult implements BoundedMetricTagValue {
    HIT("hit"),
    MISS("miss");

    private final String value;

    CacheResult(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }

  public enum CancellationReason implements BoundedMetricTagValue {
    JOB_CANCELLED("job_cancelled");

    private final String value;

    CancellationReason(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }

  public enum ReadPath implements BoundedMetricTagValue {
    ACTIVE_HELPER("ACTIVE_HELPER"),
    ACTIVE_FULL("ACTIVE_FULL"),
    ACTIVE_RESIDUAL("ACTIVE_RESIDUAL"),
    CHECKPOINT("CHECKPOINT"),
    DELTA("DELTA"),
    DELTA_EVENT("DELTA_EVENT"),
    DELTA_STATE("DELTA_STATE");

    private final String value;

    ReadPath(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }

  public enum ActiveSnapshotReadOutcome implements BoundedMetricTagValue {
    STARTED("started"),
    SUCCESS("success"),
    ERROR("error");

    private final String value;

    ActiveSnapshotReadOutcome(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }

  public enum ActiveSnapshotRowKind implements BoundedMetricTagValue {
    STABLE("stable"),
    RESIDUAL("residual");

    private final String value;

    ActiveSnapshotRowKind(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }
}
