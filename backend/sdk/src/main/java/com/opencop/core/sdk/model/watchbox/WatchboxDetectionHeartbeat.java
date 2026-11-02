// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.watchbox;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One Processor instance's watchbox-detection liveness observation. {@code lastEvaluatedAt} is
 * carried as information only - it is the last time records were actually folded, and it must never
 * gate health, because zero detections for weeks is correct behaviour for a quiet watchbox.
 *
 * <p>{@code retainedRetry} is null while no partition of this instance's watchbox listener is
 * wedged in retained retry-in-place. It exists because listener evidence is instance-wide: at
 * concurrency &gt; 1 a single wedged child keeps failing invisibly while the sibling's evidence
 * keeps this row fresh, so the wedge facts have to travel on the row itself for the read side to
 * see them.
 */
public record WatchboxDetectionHeartbeat(
    String instanceId,
    Instant listenerStartedAt,
    Instant lastEvaluatedAt,
    int assignedPartitions,
    long heartbeatSequence,
    @Nullable RetainedRetry retainedRetry) {

  public WatchboxDetectionHeartbeat {
    Objects.requireNonNull(instanceId, "instanceId");
    Objects.requireNonNull(listenerStartedAt, "listenerStartedAt");
    if (assignedPartitions < 0) {
      throw new IllegalArgumentException("assignedPartitions must be nonnegative");
    }
    if (heartbeatSequence < 0) {
      throw new IllegalArgumentException("heartbeatSequence must be nonnegative");
    }
  }

  /** Heartbeat with no retained retry-in-place episode in progress. */
  public WatchboxDetectionHeartbeat(
      String instanceId,
      Instant listenerStartedAt,
      Instant lastEvaluatedAt,
      int assignedPartitions,
      long heartbeatSequence) {
    this(
        instanceId,
        listenerStartedAt,
        lastEvaluatedAt,
        assignedPartitions,
        heartbeatSequence,
        null);
  }

  /**
   * Facts about the listener partitions currently wedged in retained retry-in-place. {@code
   * partitions} counts every wedged partition; {@code reason}, {@code since}, and {@code attempts}
   * describe the oldest active episode, so operators see how long the worst wedge has lasted.
   */
  public record RetainedRetry(int partitions, Reason reason, Instant since, long attempts) {

    public RetainedRetry {
      if (partitions < 1) {
        throw new IllegalArgumentException("partitions must be positive");
      }
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(since, "since");
      if (attempts < 1) {
        throw new IllegalArgumentException("attempts must be positive");
      }
    }

    /**
     * Coarse cause vocabulary for a retained retry-in-place wedge, fixed at two values so the
     * heartbeat row and the {@code opencop.watchbox.detection.retained.retry.partitions} gauges
     * stay bounded. Names are persisted verbatim in {@code
     * watchbox.watchbox_detection_heartbeat.retained_retry_reason}, whose CHECK constraint must be
     * extended together with this enum.
     */
    public enum Reason {
      /**
       * The wedged batch keeps failing with a database-proven Watchbox generation change: a
       * tracking reset (or rapid config churn) invalidated the cached runtime generation faster
       * than the listener converges on the new one. Replay is expected to succeed once
       * configuration stabilizes. To investigate: the heartbeat row's {@code retained_retry_*}
       * columns, the {@code opencop.watchbox.detection.retained.retry.partitions} gauges, and the
       * Processor's watchbox retained-retry log stream.
       */
      GENERATION_CHANGED,

      /**
       * The wedged batch keeps failing with a transient infrastructure condition (database outage,
       * lost connections, failed transaction acquisition); retrying in place preserves the batch
       * until the infrastructure returns. To investigate: the heartbeat row's {@code
       * retained_retry_*} columns, the {@code opencop.watchbox.detection.retained.retry.partitions}
       * gauges, and the Processor's watchbox retained-retry log stream.
       */
      INFRASTRUCTURE
    }
  }
}
