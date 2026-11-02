// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.broker;

import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry;
import com.opencop.core.sdk.model.watchbox.WatchboxDetectionHeartbeat.RetainedRetry.Reason;
import com.opencop.processor.business.usecase.watchbox.WatchboxGenerationChangedException;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.geojson.Feature;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.stereotype.Component;

/**
 * Turns the watchbox listener's retained retry-in-place episodes into per-partition liveness facts.
 * Heartbeat evidence is instance-wide, so at concurrency &gt; 1 a single wedged child keeps failing
 * invisibly while its sibling's evidence keeps the shared row fresh; spring-kafka's {@link
 * RetryListener#failedDelivery(ConsumerRecords, Exception, int)} fires on every retained attempt
 * with the exact partitions, which is the only hook that sees the wedge with its reason.
 *
 * <p>Two subtleties shape this class. First, {@code failedDelivery} also fires for ordinary
 * poison-ladder retries, so a failure only becomes an episode when {@link
 * RetryInPlaceFailureClassifier} would retain it. Second, episodes resolve only from positive batch
 * success, terminal error-handler completion, or assignment lifecycle evidence. Silence is not
 * evidence that a blocked retry healed.
 *
 * <p>{@code failedDelivery}/{@code recovered} run on consumer threads and {@link #aggregate()} on
 * the heartbeat scheduler thread; all state is guarded by one monitor, which is cheap at this
 * cardinality (bounded by partition count) and cadence (seconds). The WARN stream is rate-limited
 * per partition - episode start, then one summary per {@link #LOG_SUMMARY_INTERVAL} carrying {@code
 * suppressedCount=} - and a single resolved line identifies the exact resolution evidence.
 */
@Component
public class WatchboxRetainedRetryTracker
    implements RetryListener, BatchInterceptor<String, Feature>, ConsumerRebalanceListener {

  public static final String RETAINED_RETRY_PARTITIONS_GAUGE =
      "opencop.watchbox.detection.retained.retry.partitions";

  /** Log hygiene rather than operator policy, so a constant instead of configuration. */
  private static final Duration LOG_SUMMARY_INTERVAL = Duration.ofSeconds(60);

  private static final Logger log = LoggerFactory.getLogger(WatchboxRetainedRetryTracker.class);

  private final String watchboxTopic;
  private final Clock clock;

  private final Object lock = new Object();

  // Guarded by lock. Bounded by the watchbox topic's partition count.
  private final Map<TopicPartition, Episode> episodes = new HashMap<>();

  // One gauge per fixed reason value, registered once and refreshed on every aggregate read, so a
  // total wedge (no listener evidence, heartbeat row ageing out) still shows live values.
  private final AtomicLong generationChangedPartitions = new AtomicLong();
  private final AtomicLong infrastructurePartitions = new AtomicLong();

  public WatchboxRetainedRetryTracker(
      @Value("${opencop.messaging-broker.listeners.watchbox.topic}") String watchboxTopic,
      Clock clock,
      ProcessorMetrics metrics) {
    this.watchboxTopic = Objects.requireNonNull(watchboxTopic, "watchboxTopic");
    this.clock = Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(metrics, "metrics");
    metrics.registerGauge(
        RETAINED_RETRY_PARTITIONS_GAUGE,
        Tags.of("reason", Reason.GENERATION_CHANGED.name()),
        generationChangedPartitions);
    metrics.registerGauge(
        RETAINED_RETRY_PARTITIONS_GAUGE,
        Tags.of("reason", Reason.INFRASTRUCTURE.name()),
        infrastructurePartitions);
  }

  @Override
  public void failedDelivery(
      @NonNull ConsumerRecord<?, ?> record, @Nullable Exception ex, int deliveryAttempt) {
    // Record-level retries never reach this tracker: the watchbox listener is batch mode, and the
    // tracker is registered only on that listener's error handler.
  }

  @Override
  public void failedDelivery(
      @NonNull ConsumerRecords<?, ?> records, @NonNull Exception ex, int deliveryAttempt) {
    if (!RetryInPlaceFailureClassifier.retryInPlace(ex)) {
      // Ordinary poison-ladder retries notify this callback too; only retained episodes are
      // liveness facts - poison batches resolve through the DLT path and its own signals.
      return;
    }
    var reason = reasonFor(ex);
    var now = clock.instant();
    synchronized (lock) {
      for (TopicPartition partition : records.partitions()) {
        if (!watchboxTopic.equals(partition.topic())) {
          // Scoping: a future second retry-in-place listener sharing this handler wiring must not
          // leak its episodes into the watchbox heartbeat.
          continue;
        }
        recordFailure(partition, reason, now);
      }
    }
  }

  @Override
  public void recovered(@NonNull ConsumerRecords<?, ?> records, @NonNull Exception ex) {
    resolve(records.partitions(), Resolution.ERROR_HANDLER_COMPLETION);
  }

  @Override
  public @NonNull ConsumerRecords<String, Feature> intercept(
      @NonNull ConsumerRecords<String, Feature> records,
      @NonNull Consumer<String, Feature> consumer) {
    return records;
  }

  @Override
  public void success(
      @NonNull ConsumerRecords<String, Feature> records,
      @NonNull Consumer<String, Feature> consumer) {
    resolve(records.partitions(), Resolution.BATCH_SUCCESS);
  }

  @Override
  public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
    resolve(partitions, Resolution.ASSIGNMENT_CHANGE);
  }

  @Override
  public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
    resolve(partitions, Resolution.ASSIGNMENT_CHANGE);
  }

  @Override
  public void onPartitionsLost(Collection<TopicPartition> partitions) {
    resolve(partitions, Resolution.ASSIGNMENT_CHANGE);
  }

  private void resolve(Collection<TopicPartition> partitions, Resolution resolution) {
    var now = clock.instant();
    synchronized (lock) {
      for (TopicPartition partition : partitions) {
        if (!watchboxTopic.equals(partition.topic())) {
          continue;
        }
        var episode = episodes.remove(partition);
        if (episode != null) {
          logResolved(partition, episode, resolution, now);
        }
      }
    }
  }

  /**
   * Aggregate of the currently active episodes for the heartbeat row, or null when no partition is
   * wedged: total active partitions plus the reason, start, and attempt count of the oldest wedge.
   * Reading refreshes the per-reason gauges on the heartbeat scheduler's cadence.
   */
  public @Nullable RetainedRetry aggregate() {
    ActiveEpisodes snapshot;
    synchronized (lock) {
      snapshot = snapshotActiveEpisodes();
    }
    generationChangedPartitions.set(snapshot.generationChanged());
    infrastructurePartitions.set(snapshot.infrastructure());
    return snapshot.retainedRetry();
  }

  // Guarded by lock.
  private ActiveEpisodes snapshotActiveEpisodes() {
    long generationChanged = 0;
    long infrastructure = 0;
    Episode oldest = null;
    for (var episode : episodes.values()) {
      switch (episode.reason) {
        case GENERATION_CHANGED -> generationChanged++;
        case INFRASTRUCTURE -> infrastructure++;
      }
      if (oldest == null || episode.firstFailureAt.isBefore(oldest.firstFailureAt)) {
        oldest = episode;
      }
    }
    var retainedRetry =
        oldest == null
            ? null
            : new RetainedRetry(
                episodes.size(), oldest.reason, oldest.firstFailureAt, oldest.attempts);
    return new ActiveEpisodes(generationChanged, infrastructure, retainedRetry);
  }

  private record ActiveEpisodes(
      long generationChanged, long infrastructure, @Nullable RetainedRetry retainedRetry) {}

  // Guarded by lock.
  private void recordFailure(TopicPartition partition, Reason reason, Instant now) {
    var episode = episodes.get(partition);
    if (episode == null) {
      episodes.put(partition, new Episode(reason, now));
      log.warn("Watchbox retained retry started: partition={}, reason={}", partition, reason);
      return;
    }
    episode.attempts++;
    // A retained failure class can change mid-episode (infrastructure outage exposing a pending
    // generation change); the latest reason wins while the episode's timing is preserved.
    episode.reason = reason;
    if (!now.isBefore(episode.lastWarnAt.plus(LOG_SUMMARY_INTERVAL))) {
      log.warn(
          "Watchbox retained retry continuing: partition={}, reason={}, attempts={},"
              + " elapsedSeconds={}, suppressedCount={}",
          partition,
          reason,
          episode.attempts,
          Duration.between(episode.firstFailureAt, now).toSeconds(),
          episode.suppressedSinceLastWarn);
      episode.lastWarnAt = now;
      episode.suppressedSinceLastWarn = 0;
    } else {
      episode.suppressedSinceLastWarn++;
    }
  }

  private static void logResolved(
      TopicPartition partition, Episode episode, Resolution resolution, Instant resolvedAt) {
    log.info(
        "Watchbox retained retry resolved: partition={}, reason={}, resolution={}, attempts={},"
            + " elapsedSeconds={}",
        partition,
        episode.reason,
        resolution.logValue,
        episode.attempts,
        Duration.between(episode.firstFailureAt, resolvedAt).toSeconds());
  }

  private static Reason reasonFor(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause == cause.getCause()) {
        break;
      }
      if (cause instanceof WatchboxGenerationChangedException) {
        return Reason.GENERATION_CHANGED;
      }
    }
    return Reason.INFRASTRUCTURE;
  }

  // Guarded by lock.
  private static final class Episode {
    private final Instant firstFailureAt;
    private Reason reason;
    private long attempts;
    private Instant lastWarnAt;
    private long suppressedSinceLastWarn;

    private Episode(Reason reason, Instant now) {
      this.reason = reason;
      this.firstFailureAt = now;
      this.attempts = 1;
      // The episode-start WARN is the first line of the stream, so the summary clock starts now.
      this.lastWarnAt = now;
    }
  }

  private enum Resolution {
    BATCH_SUCCESS("batch_success"),
    ERROR_HANDLER_COMPLETION("error_handler_completion"),
    ASSIGNMENT_CHANGE("assignment_change");

    private final String logValue;

    Resolution(String logValue) {
      this.logValue = logValue;
    }
  }
}
