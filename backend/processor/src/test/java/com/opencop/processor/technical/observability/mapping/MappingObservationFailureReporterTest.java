// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ObjLongConsumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MappingObservationFailureReporterTest {

  private final AtomicLong nanos = new AtomicLong();

  @Test
  void emitsAtMostOncePerIntervalAndSummarizesSuppressedFailures() {
    var emissions = new ArrayList<String>();
    var reporter =
        reporter((exceptionClass, suppressed) -> emissions.add(exceptionClass + ":" + suppressed));

    reporter.report(IllegalStateException.class);
    reporter.report(IllegalArgumentException.class);
    reporter.report(IllegalArgumentException.class);
    assertThat(emissions).containsExactly("IllegalStateException:0");

    nanos.set(Duration.ofMinutes(6).toNanos());
    reporter.report(NullPointerException.class);
    assertThat(emissions).containsExactly("IllegalStateException:0", "NullPointerException:2");
  }

  @Test
  void throwingSinkNeverEscapesAndReportingResumesNextInterval() {
    var calls = new AtomicInteger();
    var reporter =
        reporter(
            (_, _) -> {
              if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("broken sink");
              }
            });

    assertThatCode(() -> reporter.report(IllegalStateException.class)).doesNotThrowAnyException();
    reporter.report(IllegalStateException.class);
    assertThat(calls).hasValue(1);

    nanos.set(Duration.ofMinutes(6).toNanos());
    assertThatCode(() -> reporter.report(IllegalArgumentException.class))
        .doesNotThrowAnyException();
    assertThat(calls).hasValue(2);
  }

  @Test
  void defaultSinkLogsBoundedWarnWithExceptionClassAndSuppressedCount() {
    var logger = (Logger) LoggerFactory.getLogger(MappingObservationFailureReporter.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var reporter = new MappingObservationFailureReporter(Duration.ofMinutes(5));
      reporter.report(IllegalStateException.class);
      var message = appender.list.getFirst().getFormattedMessage();
      assertThat(message)
          .contains("REALTIME_MAPPING_OBSERVABILITY_FAILED")
          .contains("exceptionClass=IllegalStateException")
          .contains("suppressedSinceLastReport=0");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void concurrentReportsEmitAtMostOncePerWindow() {
    var calls = new AtomicInteger();
    var reporter = reporter((_, _) -> calls.incrementAndGet());

    IntStream.range(0, 100).parallel().forEach(_ -> reporter.report(IllegalStateException.class));

    assertThat(calls).hasValue(1);
  }

  @Test
  void sinkFailureAtRolloverLosesOnlyThatSummaryAndRecovers() {
    var emissions = new ArrayList<String>();
    var failNext = new AtomicBoolean(false);
    var reporter =
        reporter(
            (exceptionClass, suppressed) -> {
              if (failNext.getAndSet(false)) {
                throw new IllegalStateException("sink down");
              }
              emissions.add(exceptionClass + ":" + suppressed);
            });

    reporter.report(IllegalStateException.class);
    reporter.report(IllegalStateException.class);
    reporter.report(IllegalStateException.class);
    assertThat(emissions).containsExactly("IllegalStateException:0");

    nanos.set(Duration.ofMinutes(6).toNanos());
    failNext.set(true);
    reporter.report(IllegalArgumentException.class);
    reporter.report(IllegalArgumentException.class);
    assertThat(emissions).containsExactly("IllegalStateException:0");

    nanos.set(Duration.ofMinutes(12).toNanos());
    reporter.report(NullPointerException.class);
    assertThat(emissions).containsExactly("IllegalStateException:0", "NullPointerException:1");
  }

  private MappingObservationFailureReporter reporter(ObjLongConsumer<String> sink) {
    return new MappingObservationFailureReporter(
        new GlobalMappingWarningLimiter(1, Duration.ofMinutes(5), nanos::get), sink);
  }
}
