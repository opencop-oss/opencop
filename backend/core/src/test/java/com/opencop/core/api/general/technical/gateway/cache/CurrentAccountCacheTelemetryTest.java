// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.CacheOutcome;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.InvalidValueReason;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.MutationOutcome;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry.MutationStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;

class CurrentAccountCacheTelemetryTest {

  private static final UUID USER_ID = UUID.fromString("7dd355ee-1aa6-4cc0-939b-b9898ef9178d");
  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final CurrentAccountCacheTelemetry telemetry = new CurrentAccountCacheTelemetry(registry);
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(CurrentAccountCacheTelemetry.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
    registry.close();
  }

  @Test
  void sustainedReadAndPopulateFailures_incrementCountersButEmitTwoSafeWarnings() {
    var exception = new RedisConnectionFailureException("Redis unavailable for " + USER_ID);

    for (int attempt = 0; attempt < 20; attempt++) {
      telemetry.recordCacheFailure(CacheOutcome.READ_FAILURE, exception);
      telemetry.recordCacheFailure(CacheOutcome.POPULATE_FAILURE, exception);
    }

    assertCacheCounter("read_failure", "none", 20.0d);
    assertCacheCounter("populate_failure", "none", 20.0d);
    assertEquals(2, appender.list.size());
    assertSafeWarning(appender.list.get(0));
    assertSafeWarning(appender.list.get(1));
  }

  @Test
  void invalidValueReasons_areAllowlistedAndUseFixedMetricTags() {
    assertEquals(
        Set.of(
            "unreadable_json",
            "not_object",
            "missing_field",
            "wrong_type",
            "unsupported_version",
            "user_id_mismatch",
            "negative_value"),
        Arrays.stream(InvalidValueReason.values())
            .map(InvalidValueReason::value)
            .collect(Collectors.toSet()));

    for (InvalidValueReason reason : InvalidValueReason.values()) {
      telemetry.recordInvalidValue(reason);
      assertCacheCounter("invalid_value", reason.value(), 1.0d);
    }

    assertEquals(InvalidValueReason.values().length, appender.list.size());
    appender.list.forEach(CurrentAccountCacheTelemetryTest::assertSafeWarning);
  }

  @Test
  void mutationFailures_distinguishRollbackFromCommittedRepairAndRemainBounded() {
    var exception = new RedisConnectionFailureException("Redis unavailable for " + USER_ID);

    telemetry.recordMutationSuccess(MutationStage.REQUIRED_PRECOMMIT_EVICT);
    telemetry.recordMutationSuccess(MutationStage.BEST_EFFORT_POSTCOMMIT_EVICT);
    for (int attempt = 0; attempt < 20; attempt++) {
      telemetry.recordMutationFailure(
          MutationStage.REQUIRED_PRECOMMIT_EVICT, MutationOutcome.ROLLED_BACK, exception);
      telemetry.recordMutationFailure(
          MutationStage.BEST_EFFORT_POSTCOMMIT_EVICT,
          MutationOutcome.COMMITTED_REPAIR_FAILED,
          exception);
    }

    assertMutationCounter("required_precommit_evict", "succeeded", 1.0d);
    assertMutationCounter("best_effort_postcommit_evict", "succeeded", 1.0d);
    assertMutationCounter("required_precommit_evict", "rolled_back", 20.0d);
    assertMutationCounter("best_effort_postcommit_evict", "committed_repair_failed", 20.0d);
    assertEquals(2, appender.list.size());
    appender.list.forEach(CurrentAccountCacheTelemetryTest::assertSafeWarning);
  }

  @Test
  void warningIncludesCurrentTraceIdWhenAvailable() {
    var context =
        SpanContext.create(
            TRACE_ID, "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());

    try (var ignored = Span.wrap(context).makeCurrent()) {
      telemetry.recordCacheFailure(
          CacheOutcome.READ_FAILURE, new RedisConnectionFailureException("Redis unavailable"));
    }

    assertEquals(1, appender.list.size());
    assertTrue(appender.list.getFirst().getFormattedMessage().contains("trace_id=" + TRACE_ID));
  }

  @Test
  void meterRegistryFailuresNeverEscapeAnyRecordingPathAndEmitOneSafeWarning() {
    MeterRegistry failingRegistry = mock(MeterRegistry.class);
    when(failingRegistry.counter(anyString(), any(String[].class)))
        .thenThrow(new IllegalStateException("meter unavailable for " + USER_ID));
    var failingTelemetry = new CurrentAccountCacheTelemetry(failingRegistry);
    var redisFailure = new RedisConnectionFailureException("Redis unavailable for " + USER_ID);

    assertDoesNotThrow(() -> failingTelemetry.recordCacheOutcome(CacheOutcome.HIT));
    assertDoesNotThrow(
        () -> failingTelemetry.recordCacheFailure(CacheOutcome.READ_FAILURE, redisFailure));
    assertDoesNotThrow(() -> failingTelemetry.recordInvalidValue(InvalidValueReason.MISSING_FIELD));
    assertDoesNotThrow(
        () ->
            failingTelemetry.recordInvalidValue(InvalidValueReason.UNREADABLE_JSON, redisFailure));
    assertDoesNotThrow(
        () -> failingTelemetry.recordMutationSuccess(MutationStage.REQUIRED_PRECOMMIT_EVICT));
    assertDoesNotThrow(
        () ->
            failingTelemetry.recordMutationFailure(
                MutationStage.BEST_EFFORT_POSTCOMMIT_EVICT,
                MutationOutcome.COMMITTED_REPAIR_FAILED,
                redisFailure));

    assertEquals(1, appender.list.size());
    var warning = appender.list.getFirst();
    assertNull(warning.getThrowableProxy());
    assertFalse(warning.getFormattedMessage().contains(USER_ID.toString()));
    assertFalse(warning.getFormattedMessage().contains("meter unavailable"));
    assertTrue(
        warning
            .getFormattedMessage()
            .contains(
                "CURRENT_ACCOUNT_CACHE_TELEMETRY_FAILED exception_class=IllegalStateException"));
  }

  private void assertCacheCounter(String outcome, String reason, double expectedCount) {
    Counter counter =
        registry
            .find(CurrentAccountCacheTelemetry.CACHE_OUTCOMES_TOTAL)
            .tags("outcome", outcome, "reason", reason)
            .counter();
    assertNotNull(counter);
    assertEquals(expectedCount, counter.count());
    assertEquals(
        Set.of("outcome", "reason"),
        counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()));
  }

  private void assertMutationCounter(String stage, String outcome, double expectedCount) {
    Counter counter =
        registry
            .find(CurrentAccountCacheTelemetry.CACHE_MUTATIONS_TOTAL)
            .tags("stage", stage, "outcome", outcome)
            .counter();
    assertNotNull(counter);
    assertEquals(expectedCount, counter.count());
    assertEquals(
        Set.of("outcome", "stage"),
        counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()));
  }

  private static void assertSafeWarning(ILoggingEvent event) {
    var message = event.getFormattedMessage();
    assertNull(event.getThrowableProxy());
    assertFalse(message.contains(USER_ID.toString()));
    assertFalse(message.contains("Redis unavailable"));
    assertFalse(message.contains("raw-cache-value"));
    assertTrue(message.contains("exception_class="));
    assertTrue(message.contains("trace_id="));
  }
}
