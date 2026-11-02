// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.dlt;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadLetterObservabilityTest {

  @Test
  void cleanupMetricsRecordRunOutcomeAndOldestRetainedAge() {
    var registry = new SimpleMeterRegistry();
    var observability = new DeadLetterObservability(new ProcessorMetrics(registry));

    observability.recordCleanupSuccess(Duration.ofHours(36));
    observability.recordCleanupFailure();

    assertThat(
            registry
                .counter(DeadLetterObservability.CLEANUP_RUNS_METRIC, "outcome", "success")
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .counter(DeadLetterObservability.CLEANUP_RUNS_METRIC, "outcome", "failure")
                .count())
        .isEqualTo(1.0);
    assertThat(registry.find(DeadLetterObservability.OLDEST_RETAINED_AGE_METRIC).gauge().value())
        .isEqualTo(Duration.ofHours(36).toSeconds());
  }

  @Test
  void indexingFailureMetricIsSeparateFromRecordSkips() {
    var registry = new SimpleMeterRegistry();
    var observability = new DeadLetterObservability(new ProcessorMetrics(registry));

    observability.recordSkipped("opencop.geo.validated.dlt");
    observability.recordIndexingFailure(
        "opencop.geo.validated.dlt", DeadLetterObservability.IndexingFailureCause.RETRY_EXHAUSTED);

    assertThat(
            registry
                .counter(
                    DeadLetterObservability.RECORDS_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    "opencop.geo.validated.dlt",
                    DeadLetterObservability.TAG_OUTCOME,
                    "skipped")
                .count())
        .isEqualTo(1.0);
    assertThat(
            registry
                .counter(
                    DeadLetterObservability.INDEXING_FAILURES_METRIC,
                    DeadLetterObservability.TAG_TOPIC_OR_SOURCE_CLASS,
                    "opencop.geo.validated.dlt",
                    DeadLetterObservability.TAG_CAUSE,
                    "retry_exhausted")
                .count())
        .isEqualTo(1.0);
  }
}
