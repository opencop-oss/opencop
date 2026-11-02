// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.consumer;

import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class WatchboxConsumer {

  public static final String LISTENER_ID = "watchboxConsumer";

  private static final Logger log = LoggerFactory.getLogger(WatchboxConsumer.class);

  // Bounded sample: infra failures retry in place every few seconds, so the failure line repeats
  // for the whole outage. Operators may read it through docker logs on the box.
  private static final int MAX_LOGGED_FEATURE_IDS = 10;

  /** Cadence of the single-line ERROR summary while a same-class failure streak persists. */
  private static final Duration FAILURE_SUMMARY_INTERVAL = Duration.ofSeconds(60);

  private final WatchboxEventHandler handler;
  private final WatchboxDetectionHeartbeatPublisher heartbeat;
  private final Clock clock;

  // Retained retry-in-place failures repeat every ~5s for the whole outage, so repeats are
  // demoted: the first failure keeps today's single-line ERROR, same-class repeats within the
  // summary interval go to DEBUG, a periodic single-line ERROR summary carries suppressedCount=,
  // and a class change re-emits at ERROR immediately so a newly exposed failure mode is never
  // buried. Stack traces stay DEBUG-only throughout. The streak is thread-confined: retry-in-place
  // re-invokes the listener on the same consumer thread (spring-kafka keys its retry state by
  // thread), so each child owns its streak without coordination, and the state dies with the
  // consumer thread on shutdown. A rebalance keeps the thread (and any streak) alive; a stale
  // streak is harmless — the next failure either matches its class (correctly counted as the same
  // condition persisting) or resets it with a fresh ERROR. An instance field rather than the usual
  // static final: the bean is a singleton, and instance scoping keeps parallel test consumers from
  // sharing streak state through a JVM-wide ThreadLocal.
  private final ThreadLocal<FailureStreak> failureStreak = new ThreadLocal<>();

  public WatchboxConsumer(
      WatchboxEventHandler handler, WatchboxDetectionHeartbeatPublisher heartbeat, Clock clock) {
    this.handler = handler;
    this.heartbeat = heartbeat;
    this.clock = clock;
  }

  // groupId binds explicitly: @KafkaListener defaults idIsGroup=true, which silently makes the
  // listener id the consumer group and leaves the configured groupId ineffective.
  @KafkaListener(
      id = LISTENER_ID,
      groupId = "${opencop.messaging-broker.listeners.watchbox.groupId}",
      topics = "${opencop.messaging-broker.listeners.watchbox.topic}",
      containerFactory = "watchboxFactory")
  public void watchboxEvent(List<Feature> features) {
    long startedAtNanos = System.nanoTime();
    log.debug("Consuming watchbox batch: count={}", features.size());

    try {
      handler.process(features);
      heartbeat.recordBatchProcessed();
      logRecoveredIfSuppressed();
      log.info(
          "Watchbox batch processed successfully: count={}, durationMs={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos));
    } catch (Exception exception) {
      logFailure(features, startedAtNanos, exception);
      throw exception;
    }
  }

  private void logFailure(List<Feature> features, long startedAtNanos, Exception exception) {
    var streak = failureStreak.get();
    var now = clock.instant();
    if (streak == null || !streak.exceptionClass.equals(exception.getClass())) {
      failureStreak.set(new FailureStreak(exception.getClass(), now));
      log.error(
          "Watchbox batch failed: count={}, durationMs={}, featureIds={}, cause={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(features, MAX_LOGGED_FEATURE_IDS),
          exception.toString());
    } else if (!now.isBefore(streak.lastErrorAt.plus(FAILURE_SUMMARY_INTERVAL))) {
      log.error(
          "Watchbox batch still failing: count={}, durationMs={}, featureIds={}, cause={},"
              + " suppressedCount={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(features, MAX_LOGGED_FEATURE_IDS),
          exception,
          streak.suppressedSinceLastError);
      streak.lastErrorAt = now;
      streak.suppressedSinceLastError = 0;
    } else {
      streak.suppressedSinceLastError++;
      streak.suppressedTotal++;
      log.debug(
          "Watchbox batch failed (suppressed repeat): count={}, durationMs={}, featureIds={},"
              + " cause={}",
          features.size(),
          ConsumerLogSupport.elapsedMillisSince(startedAtNanos),
          ConsumerLogSupport.featureIds(features, MAX_LOGGED_FEATURE_IDS),
          exception.toString());
    }
    log.debug("Watchbox batch failure stack", exception);
  }

  private void logRecoveredIfSuppressed() {
    var streak = failureStreak.get();
    if (streak == null) {
      return;
    }
    failureStreak.remove();
    if (streak.suppressedTotal > 0) {
      // Closure for the demoted repeats: without this line, a log stream filtered to INFO and
      // above shows a failure summary followed by silence, which reads as still-failing.
      log.info(
          "Watchbox batch processing recovered: cause={}, suppressedCount={}",
          streak.exceptionClass.getName(),
          streak.suppressedTotal);
    }
  }

  // Confined to a single consumer thread; see the failureStreak field comment.
  private static final class FailureStreak {
    private final Class<? extends Exception> exceptionClass;
    private Instant lastErrorAt;
    private long suppressedSinceLastError;
    private long suppressedTotal;

    private FailureStreak(Class<? extends Exception> exceptionClass, Instant firstFailureAt) {
      this.exceptionClass = exceptionClass;
      this.lastErrorAt = firstFailureAt;
    }
  }
}
