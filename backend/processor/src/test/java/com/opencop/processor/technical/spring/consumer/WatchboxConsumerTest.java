// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Pins the failure-line demotion contract: retained retry-in-place repeats the same failure every
 * ~5s for the whole outage, so only the first failure and a periodic summary may reach ERROR, with
 * the repeats demoted to DEBUG and an INFO closure once processing recovers.
 */
class WatchboxConsumerTest {

  private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");

  private final WatchboxEventHandler handler = mock(WatchboxEventHandler.class);
  private final WatchboxDetectionHeartbeatPublisher heartbeat =
      mock(WatchboxDetectionHeartbeatPublisher.class);
  private final MutableClock clock = new MutableClock(NOW);
  private final WatchboxConsumer consumer = new WatchboxConsumer(handler, heartbeat, clock);

  private ListAppender<ILoggingEvent> appender;
  private Level originalLevel;

  @BeforeEach
  void attachAppender() {
    var logger = (Logger) LoggerFactory.getLogger(WatchboxConsumer.class);
    originalLevel = logger.getLevel();
    // The demotion contract is about levels, so DEBUG events must reach the appender to prove
    // repeats were demoted rather than dropped.
    logger.setLevel(Level.DEBUG);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    var logger = (Logger) LoggerFactory.getLogger(WatchboxConsumer.class);
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void firstFailureLogsASingleErrorLineWithTheStackAtDebugOnly() {
    doThrow(infrastructureFailure()).when(handler).process(anyList());

    assertThatThrownBy(() -> consumer.watchboxEvent(batch()))
        .isInstanceOf(DataAccessResourceFailureException.class);

    assertThat(events(Level.ERROR))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage()).startsWith("Watchbox batch failed");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(events(Level.DEBUG))
        .anySatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
  }

  @Test
  void sameClassRepeatsWithinTheSummaryIntervalAreDemotedToDebug() {
    doThrow(infrastructureFailure()).when(handler).process(anyList());

    failOnce();
    clock.advance(Duration.ofSeconds(5));
    failOnce();
    clock.advance(Duration.ofSeconds(5));
    failOnce();

    assertThat(events(Level.ERROR)).hasSize(1);
    assertThat(messages(Level.DEBUG))
        .filteredOn(message -> message.startsWith("Watchbox batch failed (suppressed repeat)"))
        .hasSize(2);
  }

  @Test
  void periodicErrorSummaryCarriesTheSuppressedCount() {
    doThrow(infrastructureFailure()).when(handler).process(anyList());

    failOnce();
    for (var i = 0; i < 3; i++) {
      clock.advance(Duration.ofSeconds(5));
      failOnce();
    }
    clock.advance(Duration.ofSeconds(50));
    failOnce();

    var errors = messages(Level.ERROR);
    assertThat(errors).hasSize(2);
    assertThat(errors.get(1))
        .startsWith("Watchbox batch still failing")
        .contains("suppressedCount=3");
  }

  @Test
  void exceptionClassChangeReemitsAtErrorImmediately() {
    // Log-on-change: a newly exposed failure mode mid-streak must never be buried at DEBUG.
    doThrow(infrastructureFailure()).when(handler).process(anyList());
    failOnce();
    clock.advance(Duration.ofSeconds(5));
    doThrow(new IllegalStateException("newly exposed poison")).when(handler).process(anyList());
    failOnce();

    assertThat(messages(Level.ERROR))
        .hasSize(2)
        .allSatisfy(message -> assertThat(message).startsWith("Watchbox batch failed"));
  }

  @Test
  void successAfterSuppressedRepeatsLogsAnInfoRecoveryLine() {
    doThrow(infrastructureFailure())
        .doThrow(infrastructureFailure())
        .doNothing()
        .when(handler)
        .process(anyList());

    failOnce();
    clock.advance(Duration.ofSeconds(5));
    failOnce();
    consumer.watchboxEvent(batch());

    assertThat(messages(Level.INFO))
        .anySatisfy(
            message ->
                assertThat(message)
                    .startsWith("Watchbox batch processing recovered")
                    .contains("suppressedCount=1"));
  }

  @Test
  void successWithoutSuppressionLogsNoRecoveryLine() {
    // One failure, one ERROR, then success: the ordinary success INFO is closure enough, and a
    // recovery line here would just be noise on every transient blip.
    doThrow(infrastructureFailure()).doNothing().when(handler).process(anyList());

    failOnce();
    consumer.watchboxEvent(batch());

    assertThat(messages(Level.INFO))
        .noneSatisfy(
            message -> assertThat(message).startsWith("Watchbox batch processing recovered"));
    assertThat(messages(Level.INFO))
        .anySatisfy(
            message -> assertThat(message).startsWith("Watchbox batch processed successfully"));
  }

  private void failOnce() {
    assertThatThrownBy(() -> consumer.watchboxEvent(batch())).isInstanceOf(Exception.class);
  }

  private List<ILoggingEvent> events(Level level) {
    return appender.list.stream().filter(event -> event.getLevel() == level).toList();
  }

  private List<String> messages(Level level) {
    return events(level).stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private static List<Feature> batch() {
    var feature = new Feature();
    feature.setId("feature-1");
    return List.of(feature);
  }

  private static DataAccessResourceFailureException infrastructureFailure() {
    return new DataAccessResourceFailureException("simulated database outage");
  }

  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant initial) {
      this.instant = initial;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
