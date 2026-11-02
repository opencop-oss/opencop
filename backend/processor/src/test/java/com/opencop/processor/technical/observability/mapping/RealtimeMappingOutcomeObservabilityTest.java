// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.observability.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.mapper.MappedRecord;
import com.opencop.core.sdk.model.mapper.MappingError;
import com.opencop.core.sdk.model.mapper.MappingResponse;
import com.opencop.core.sdk.model.mapper.MappingWarning;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnostic;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.MappingDiagnosticSeverity;
import com.opencop.processor.business.model.mapping.MappingResourceKind;
import com.opencop.processor.business.model.mapping.MappingResourceRef;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;
import com.opencop.processor.business.model.mapping.RealtimeMappingEntry;
import com.opencop.processor.business.policy.mapping.RealtimeMappingOutcomeClassifier;
import com.opencop.processor.technical.spring.config.RealtimeMappingOutcomeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RealtimeMappingOutcomeObservabilityTest {

  private static final UUID MAPPER_ID = UUID.fromString("d4dfa598-f66c-46da-a0ce-71854b6f5871");

  @Test
  void recordsExactSharedMetricsForBothIngressesAndOnlyProblemsGetResourceTags() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(100));
    var byod = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var raw = attempt(RealtimeMappingEntry.RAW_REALTIME, UUID.randomUUID());

    observer.observe(byod, emitted());
    observer.observe(raw, failed("ON_MISSING_ERROR", "secret raw error text"));

    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.OUTCOME_METRIC)
                .tags(
                    "ingress",
                    "byod",
                    "outcome",
                    "emitted",
                    "reason",
                    "none",
                    "decoder_kind",
                    "java")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.OUTCOME_METRIC)
                .tags(
                    "ingress",
                    "raw_realtime",
                    "outcome",
                    "failed_all_with_errors",
                    "reason",
                    "on_missing_error",
                    "decoder_kind",
                    "dynamic")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.PROBLEM_METRIC)
                .tags(
                    "ingress",
                    "raw_realtime",
                    "resource_kind",
                    "data_source",
                    "resource_id",
                    raw.resource().id().toString(),
                    "outcome",
                    "failed_all_with_errors")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(registry.find(RealtimeMappingOutcomeObservability.PROBLEM_METRIC).meters())
        .hasSize(1);
  }

  @Test
  void overflowCollapsesByAdmittedTagAndCountsEveryObservation() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(1));
    observer.observe(
        attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()), failed("PAYLOAD_ERROR", "x"));
    var overflowAttempt = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    observer.observe(overflowAttempt, failed("PAYLOAD_ERROR", "x"));
    observer.observe(overflowAttempt, failed("PAYLOAD_ERROR", "x"));

    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.PROBLEM_METRIC)
                .tags("resource_id", "other")
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(registry.get(RealtimeMappingOutcomeObservability.OVERFLOW_METRIC).counter().count())
        .isEqualTo(2);
  }

  @Test
  void logsArePayloadFreeAndUseTotalWarnings() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(100));
    var logger = (Logger) LoggerFactory.getLogger(RealtimeMappingOutcomeObservability.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      observer.observe(
          attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()),
          failedWithNestedWarnings("CUSTOM_FREE_FORM_CODE", "sensitive customer payload"));
      var message = appender.list.getFirst().getFormattedMessage();
      assertThat(message)
          .contains("REALTIME_MAPPING_PROBLEM")
          .contains("reason=other_mapping_error")
          .contains("warningCount=2")
          .contains("payloadLogged=false")
          .doesNotContain("CUSTOM_FREE_FORM_CODE")
          .doesNotContain("sensitive customer payload");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void acceptedDecoderWarningsGetABoundedWarningAndDistinctAggregateMetric() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(100));
    var attempt = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var logger = (Logger) LoggerFactory.getLogger(RealtimeMappingOutcomeObservability.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      observer.observe(attempt, acceptedWarning());
      observer.observe(attempt, acceptedWarning());

      assertThat(appender.list).hasSize(1);
      var message = appender.list.getFirst().getFormattedMessage();
      assertThat(message)
          .contains("REALTIME_MAPPING_PROBLEM")
          .contains("outcome=accepted_no_output")
          .contains("reason=accepted_decoder_warning")
          .contains("payloadLogged=false");
      assertThat(
              registry
                  .get(RealtimeMappingOutcomeObservability.OUTCOME_METRIC)
                  .tags(
                      "ingress",
                      "byod",
                      "outcome",
                      "accepted_no_output",
                      "reason",
                      "accepted_decoder_warning",
                      "decoder_kind",
                      "java")
                  .counter()
                  .count())
          .isEqualTo(2);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void disabledObserverRecordsNothingAndWarmHappyPathDoesNotFormatIdsOrGrowMeters() {
    var registry = new SimpleMeterRegistry();
    var disabled = properties();
    disabled.setEnabled(false);
    observer(registry, disabled, new ProblemResourceTagLimiter(100))
        .observe(attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()), emitted());
    assertThat(registry.getMeters()).isEmpty();

    var conversions = new AtomicInteger();
    var limiter =
        new ProblemResourceTagLimiter(
            100,
            id -> {
              conversions.incrementAndGet();
              return id.toString();
            });
    var enabled = observer(registry, properties(), limiter);
    var byod = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var raw = attempt(RealtimeMappingEntry.RAW_REALTIME, UUID.randomUUID());
    var emitted = emitted();
    enabled.observe(byod, emitted);
    enabled.observe(raw, emitted);
    var metersAfterWarmup = registry.getMeters().size();

    assertTimeout(
        Duration.ofSeconds(5),
        () -> {
          for (var i = 0; i < 100_000; i++) {
            enabled.observe((i & 1) == 0 ? byod : raw, emitted);
          }
        });
    assertThat(registry.getMeters()).hasSize(metersAfterWarmup);
    assertThat(conversions).hasValue(0);
    assertThat(limiter.admittedCount()).isZero();
  }

  @Test
  void failureReporterAndThrowingSinkNeverEscape() {
    var calls = new AtomicInteger();
    var reporter =
        new MappingObservationFailureReporter(
            new GlobalMappingWarningLimiter(1, Duration.ofMinutes(5)),
            (_, _) -> {
              calls.incrementAndGet();
              throw new IllegalStateException("broken sink");
            });

    assertThatCode(() -> reporter.report(IllegalArgumentException.class))
        .doesNotThrowAnyException();
    assertThatCode(() -> reporter.report(IllegalStateException.class)).doesNotThrowAnyException();
    assertThat(calls).hasValue(1);
  }

  @Test
  void instrumentationCollaboratorFailureDoesNotEscapeAndIsReportedOnce() {
    var calls = new AtomicInteger();
    var properties = properties();
    var reporter =
        new MappingObservationFailureReporter(
            new GlobalMappingWarningLimiter(1, Duration.ofMinutes(5)),
            (_, _) -> calls.incrementAndGet());
    var observer =
        new RealtimeMappingOutcomeObservability(
            mock(MeterRegistry.class),
            properties,
            new RealtimeMappingOutcomeClassifier(),
            new ProblemResourceTagLimiter(100),
            new RealtimeMappingProblemLogLimiter(
                properties.getWarningStateMax(),
                properties.getWarningSummaryInterval(),
                new GlobalMappingWarningLimiter(
                    properties.getWarningGlobalMaxPerInterval(),
                    properties.getWarningSummaryInterval())),
            reporter);

    assertThatCode(
            () ->
                observer.observe(attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()), emitted()))
        .doesNotThrowAnyException();
    assertThat(calls).hasValue(1);
  }

  @Test
  void observerRemainsNoThrowWhenInjectedFailureSinkThrows() {
    var properties = properties();
    var observer =
        new RealtimeMappingOutcomeObservability(
            new SimpleMeterRegistry(),
            properties,
            new RealtimeMappingOutcomeClassifier(),
            new ProblemResourceTagLimiter(100),
            new RealtimeMappingProblemLogLimiter(
                properties.getWarningStateMax(),
                properties.getWarningSummaryInterval(),
                new GlobalMappingWarningLimiter(
                    properties.getWarningGlobalMaxPerInterval(),
                    properties.getWarningSummaryInterval())),
            _ -> {
              throw new IllegalStateException("broken failure sink");
            });

    assertThatCode(
            () -> observer.observe(attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()), null))
        .doesNotThrowAnyException();
  }

  @Test
  void observeThrownCountsAggregateAndResourceProblemWithBoundedTags() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(100));
    var attempt = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());

    observer.observeThrown(attempt);

    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.OUTCOME_METRIC)
                .tags(
                    "ingress",
                    "byod",
                    "outcome",
                    "failed_thrown",
                    "reason",
                    "thrown",
                    "decoder_kind",
                    "unknown")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(
            registry
                .get(RealtimeMappingOutcomeObservability.PROBLEM_METRIC)
                .tags(
                    "ingress",
                    "byod",
                    "resource_kind",
                    "data_source",
                    "resource_id",
                    attempt.resource().id().toString(),
                    "outcome",
                    "failed_thrown")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void observeThrownIsDisabledAndFailOpenLikeObserve() {
    var registry = new SimpleMeterRegistry();
    var disabled = properties();
    disabled.setEnabled(false);
    observer(registry, disabled, new ProblemResourceTagLimiter(100))
        .observeThrown(attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID()));
    assertThat(registry.getMeters()).isEmpty();

    var calls = new AtomicInteger();
    var properties = properties();
    var failing =
        new RealtimeMappingOutcomeObservability(
            mock(MeterRegistry.class),
            properties,
            new RealtimeMappingOutcomeClassifier(),
            new ProblemResourceTagLimiter(100),
            new RealtimeMappingProblemLogLimiter(
                properties.getWarningStateMax(),
                properties.getWarningSummaryInterval(),
                new GlobalMappingWarningLimiter(
                    properties.getWarningGlobalMaxPerInterval(),
                    properties.getWarningSummaryInterval())),
            new MappingObservationFailureReporter(
                new GlobalMappingWarningLimiter(1, Duration.ofMinutes(5)),
                (_, _) -> calls.incrementAndGet()));
    assertThatCode(
            () -> failing.observeThrown(attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID())))
        .doesNotThrowAnyException();
    assertThat(calls).hasValue(1);
  }

  @Test
  void thrownFailuresInstallNoWarnStateAndHealthyFollowUpEmitsNoRecovery() {
    var registry = new SimpleMeterRegistry();
    var observer = observer(registry, properties(), new ProblemResourceTagLimiter(100));
    var attempt = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var logger = (Logger) LoggerFactory.getLogger(RealtimeMappingOutcomeObservability.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      observer.observeThrown(attempt);
      observer.observe(attempt, emitted());
      assertThat(appender.list).isEmpty();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void globallyThrottledWarningsRetryAndEmitThrottledSummary() {
    var registry = new SimpleMeterRegistry();
    var nanos = new AtomicLong();
    var global = new GlobalMappingWarningLimiter(1, Duration.ofMinutes(5), nanos::get);
    var properties = properties();
    var observer =
        new RealtimeMappingOutcomeObservability(
            registry,
            properties,
            new RealtimeMappingOutcomeClassifier(),
            new ProblemResourceTagLimiter(100),
            new RealtimeMappingProblemLogLimiter(
                properties.getWarningStateMax(), Duration.ofMinutes(5), global, nanos::get),
            new MappingObservationFailureReporter(properties.getWarningSummaryInterval()));
    var admitted = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var throttled = attempt(RealtimeMappingEntry.BYOD, UUID.randomUUID());
    var logger = (Logger) LoggerFactory.getLogger(RealtimeMappingOutcomeObservability.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      observer.observe(admitted, failed("ON_MISSING_ERROR", "x"));
      observer.observe(throttled, failed("ON_MISSING_ERROR", "x"));
      assertThat(appender.list).hasSize(1);

      nanos.set(Duration.ofMinutes(6).toNanos());
      observer.observe(throttled, failed("ON_MISSING_ERROR", "x"));

      var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(messages).hasSize(3);
      assertThat(messages.get(1))
          .contains("REALTIME_MAPPING_WARNING_THROTTLED")
          .contains("suppressedCount=1");
      assertThat(messages.get(2))
          .contains("REALTIME_MAPPING_PROBLEM")
          .contains("resourceId=" + throttled.resource().id());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static RealtimeMappingOutcomeObservability observer(
      SimpleMeterRegistry registry,
      RealtimeMappingOutcomeProperties properties,
      ProblemResourceTagLimiter limiter) {
    return new RealtimeMappingOutcomeObservability(
        registry,
        properties,
        new RealtimeMappingOutcomeClassifier(),
        limiter,
        new RealtimeMappingProblemLogLimiter(
            properties.getWarningStateMax(),
            properties.getWarningSummaryInterval(),
            new GlobalMappingWarningLimiter(
                properties.getWarningGlobalMaxPerInterval(),
                properties.getWarningSummaryInterval())),
        new MappingObservationFailureReporter(properties.getWarningSummaryInterval()));
  }

  private static RealtimeMappingOutcomeProperties properties() {
    var properties = new RealtimeMappingOutcomeProperties();
    properties.setWarningSummaryInterval(Duration.ofMinutes(5));
    return properties;
  }

  private static RealtimeMappingAttempt attempt(RealtimeMappingEntry ingress, UUID resourceId) {
    return new RealtimeMappingAttempt(
        ingress, new MappingResourceRef(MappingResourceKind.DATA_SOURCE, resourceId), 2, 17);
  }

  private static DecodedMappingResult emitted() {
    return DecodedMappingResult.fromFeatures(MAPPER_ID, 1, "java", List.of(new Feature()));
  }

  private static DecodedMappingResult acceptedWarning() {
    return DecodedMappingResult.acceptedNoOutput(
        MAPPER_ID,
        1,
        "FIXTURE",
        List.of(
            new MappingDiagnostic(
                "UNSUPPORTED_TYPE",
                MappingDiagnosticSeverity.WARNING,
                "Unsupported input type",
                java.util.Map.of())));
  }

  private static DecodedMappingResult failed(String code, String message) {
    var response =
        new MappingResponse(
            MAPPER_ID,
            null,
            List.of(),
            List.of(new MappingError(null, code, message, List.of(), null)),
            List.of());
    return DecodedMappingResult.fromMappingResponse(MAPPER_ID, 4, "dynamic", response);
  }

  private static DecodedMappingResult failedWithNestedWarnings(String code, String message) {
    var topLevelWarning = new MappingWarning("top", "ignored", List.of(), null);
    var nestedWarning = new MappingWarning("nested", "ignored", List.of(), 0);
    var response =
        new MappingResponse(
            MAPPER_ID,
            null,
            List.of(new MappedRecord(0, List.of(), List.of(nestedWarning))),
            List.of(new MappingError(null, code, message, List.of(), null)),
            List.of(topLevelWarning));
    return DecodedMappingResult.fromMappingResponse(MAPPER_ID, 4, "dynamic", response);
  }
}
