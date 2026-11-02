// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterObservability {

  static final String RECORDS_METRIC = "opencop_processor_dlt_records_total";
  static final String FAILURES_METRIC = "opencop_processor_dlt_failures_total";
  static final String INDEXING_FAILURES_METRIC = "opencop_processor_dlt_indexing_failures_total";
  static final String INDEXING_LAG_METRIC = "opencop_processor_dlt_indexing_lag_ms";
  static final String LAST_INDEXED_TIME_METRIC = "opencop_processor_dlt_last_indexed_time_epoch_ms";
  static final String BLOB_UPLOAD_LATENCY_METRIC = "opencop_processor_dlt_blob_upload_latency";
  static final String BLOB_UPLOAD_FAILURES_METRIC =
      "opencop_processor_dlt_blob_upload_failures_total";
  static final String BLOB_UPLOAD_BYTES_METRIC = "opencop_processor_dlt_blob_upload_bytes";
  static final String BLOB_OBJECTS_METRIC = "opencop_processor_dlt_blob_objects_total";
  static final String DB_UPSERT_FAILURES_METRIC = "opencop_processor_dlt_db_upsert_failures_total";
  static final String CLEANUP_RUNS_METRIC = "opencop_processor_dlt_cleanup_runs_total";
  static final String OLDEST_RETAINED_AGE_METRIC =
      "opencop_processor_dlt_oldest_retained_age_seconds";
  static final String UNKNOWN = "unknown";
  public static final String BYOD = "byod";
  static final String TAG_TOPIC_OR_SOURCE_CLASS = "topic_or_source_class";
  static final String TAG_OUTCOME = "outcome";
  static final String TAG_CAUSE = "cause";

  private final ProcessorMetrics metrics;
  private final Clock clock;
  private final Map<String, AtomicLong> lastIndexedEpochMs = new ConcurrentHashMap<>();
  private final AtomicLong oldestRetainedAgeSeconds = new AtomicLong(0);

  public DeadLetterObservability(ProcessorMetrics metrics) {
    this.metrics = metrics;
    this.clock = Clock.systemUTC();
  }

  public void recordSeen(String topicOrSourceClass) {
    incrementRecord(topicOrSourceClass, "seen");
  }

  public void recordIndexed(String topicOrSourceClass, long recordTimestampMs) {
    incrementRecord(topicOrSourceClass, "indexed");
    long nowMs = clock.millis();
    if (recordTimestampMs >= 0) {
      recordIndexingLag(topicOrSourceClass, Math.max(0, nowMs - recordTimestampMs));
    }
    setLastIndexed(topicOrSourceClass, nowMs);
  }

  public void recordSkipped(String topicOrSourceClass) {
    incrementRecord(topicOrSourceClass, "skipped");
  }

  public void recordFailure(String topicOrSourceClass, FailureCause cause) {
    metrics.incrementCounter(
        FAILURES_METRIC,
        Tags.of(TAG_TOPIC_OR_SOURCE_CLASS, normalize(topicOrSourceClass), TAG_CAUSE, cause.tag()));
  }

  public void recordIndexingFailure(String topicOrSourceClass, IndexingFailureCause cause) {
    metrics.incrementCounter(
        INDEXING_FAILURES_METRIC,
        Tags.of(TAG_TOPIC_OR_SOURCE_CLASS, normalize(topicOrSourceClass), TAG_CAUSE, cause.tag()));
  }

  public void recordBlobUploadSuccess(
      String topicOrSourceClass, long payloadSizeBytes, Duration latency) {
    var tags = topicTags(topicOrSourceClass);
    metrics.recordTimer(BLOB_UPLOAD_LATENCY_METRIC, tags, latency);
    metrics.recordSummary(BLOB_UPLOAD_BYTES_METRIC, tags, payloadSizeBytes);
    metrics.incrementCounter(BLOB_OBJECTS_METRIC, tags);
  }

  public void recordBlobUploadFailure(String topicOrSourceClass, Duration latency) {
    var tags = topicTags(topicOrSourceClass);
    metrics.recordTimer(BLOB_UPLOAD_LATENCY_METRIC, tags, latency);
    metrics.incrementCounter(BLOB_UPLOAD_FAILURES_METRIC, tags);
    recordFailure(topicOrSourceClass, FailureCause.BLOB);
  }

  public void recordDbUpsertFailure(String topicOrSourceClass) {
    metrics.incrementCounter(DB_UPSERT_FAILURES_METRIC, topicTags(topicOrSourceClass));
    recordFailure(topicOrSourceClass, FailureCause.DB);
  }

  public void recordCleanupSuccess(Duration oldestRetainedAge) {
    metrics.incrementCounter(CLEANUP_RUNS_METRIC, Tags.of(TAG_OUTCOME, "success"));
    metrics.registerGauge(OLDEST_RETAINED_AGE_METRIC, Tags.empty(), oldestRetainedAgeSeconds);
    oldestRetainedAgeSeconds.set(Math.max(0, oldestRetainedAge.toSeconds()));
  }

  public void recordCleanupFailure() {
    metrics.incrementCounter(CLEANUP_RUNS_METRIC, Tags.of(TAG_OUTCOME, "failure"));
  }

  private void incrementRecord(String topicOrSourceClass, String outcome) {
    metrics.incrementCounter(
        RECORDS_METRIC,
        Tags.of(TAG_TOPIC_OR_SOURCE_CLASS, normalize(topicOrSourceClass), TAG_OUTCOME, outcome));
  }

  private void recordIndexingLag(String topicOrSourceClass, long lagMs) {
    metrics.recordSummary(INDEXING_LAG_METRIC, topicTags(topicOrSourceClass), lagMs);
  }

  private void setLastIndexed(String topicOrSourceClass, long indexedAtEpochMs) {
    String normalized = normalize(topicOrSourceClass);
    var gauge =
        lastIndexedEpochMs.computeIfAbsent(
            normalized,
            key -> {
              var value = new AtomicLong(0);
              metrics.registerGauge(LAST_INDEXED_TIME_METRIC, topicTags(key), value);
              return value;
            });
    gauge.set(indexedAtEpochMs);
  }

  private static Tags topicTags(String topicOrSourceClass) {
    return Tags.of(TAG_TOPIC_OR_SOURCE_CLASS, normalize(topicOrSourceClass));
  }

  static String normalize(String topicOrSourceClass) {
    return topicOrSourceClass == null || topicOrSourceClass.isBlank()
        ? UNKNOWN
        : topicOrSourceClass;
  }

  public enum FailureCause {
    BLOB("blob"),
    DB("db"),
    PARSE("parse");

    private final String tag;

    FailureCause(String tag) {
      this.tag = tag;
    }

    String tag() {
      return tag;
    }
  }

  public enum IndexingFailureCause {
    RETRY_EXHAUSTED("retry_exhausted");

    private final String tag;

    IndexingFailureCause(String tag) {
      this.tag = tag;
    }

    String tag() {
      return tag;
    }
  }
}
