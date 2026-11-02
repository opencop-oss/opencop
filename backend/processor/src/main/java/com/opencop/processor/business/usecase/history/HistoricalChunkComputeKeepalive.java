// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.history;

import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlan;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public final class HistoricalChunkComputeKeepalive {

  static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(30);

  private static final Logger log = LoggerFactory.getLogger(HistoricalChunkComputeKeepalive.class);

  private final ScheduledExecutorService scheduler;
  private final HistoricalChunkDeliveryProgressReporter deliveryProgressReporter;

  public HistoricalChunkComputeKeepalive(
      @Qualifier("historicalJobScheduler") ScheduledExecutorService scheduler,
      HistoricalChunkDeliveryProgressReporter deliveryProgressReporter) {
    this.scheduler = scheduler;
    this.deliveryProgressReporter = deliveryProgressReporter;
  }

  HistoricalChunkExecutionResult execute(
      HistoricalChunkPlan plan,
      HistoricalSubjectExecutionMeta metadata,
      LongSupplier computedChunks,
      LongSupplier failedChunks,
      HistoricalChunkExecutionTelemetry.ChunkOperation operation) {
    ScheduledFuture<?> keepalive = scheduleKeepalive(plan, metadata, computedChunks, failedChunks);
    try {
      return operation.execute();
    } finally {
      if (keepalive != null) {
        keepalive.cancel(false);
      }
    }
  }

  private ScheduledFuture<?> scheduleKeepalive(
      HistoricalChunkPlan plan,
      HistoricalSubjectExecutionMeta metadata,
      LongSupplier computedChunks,
      LongSupplier failedChunks) {
    try {
      long intervalMillis = KEEPALIVE_INTERVAL.toMillis();
      return scheduler.scheduleAtFixedRate(
          () -> publishKeepalive(plan, metadata, computedChunks, failedChunks),
          intervalMillis,
          intervalMillis,
          TimeUnit.MILLISECONDS);
    } catch (RejectedExecutionException e) {
      log.warn(
          "Historical chunk keepalive scheduling rejected for lane job {}",
          metadata.laneJobId(),
          e);
      return null;
    }
  }

  private void publishKeepalive(
      HistoricalChunkPlan plan,
      HistoricalSubjectExecutionMeta metadata,
      LongSupplier computedChunks,
      LongSupplier failedChunks) {
    try {
      deliveryProgressReporter.recordProgress(
          plan, metadata, computedChunks.getAsLong(), failedChunks.getAsLong());
    } catch (RuntimeException e) {
      log.warn(
          "Historical chunk keepalive publication failed for lane job {}", metadata.laneJobId(), e);
    }
  }
}
