// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.runtimefeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MicrometerWatchboxTrackingResetTelemetryTest {

  @Test
  void recordResetAfterCommit_whenTransactionCommits_emitsEvidenceOnlyAfterCommit() {
    var registry = new SimpleMeterRegistry();
    var telemetry = new MicrometerWatchboxTrackingResetTelemetry(registry);
    var logger = (Logger) LoggerFactory.getLogger(MicrometerWatchboxTrackingResetTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    TransactionSynchronizationManager.initSynchronization();
    try {
      telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 3);

      assertThat(registry.find(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter())
          .isNull();
      assertThat(appender.list).isEmpty();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      assertThat(
              registry.get(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter().count())
          .isOne();
      assertThat(
              registry
                  .get(MicrometerWatchboxTrackingResetTelemetry.CONFIG_EPOCHS_BUMPED_TOTAL)
                  .counter()
                  .count())
          .isEqualTo(3);
      assertThat(appender.list)
          .singleElement()
          .extracting(ILoggingEvent::getFormattedMessage)
          .asString()
          .contains("WATCHBOX_TRACKING_RESET")
          .contains("trigger=runtime_feature_reenable")
          .contains("audience_mode=ALL")
          .contains("active_config_epochs_bumped=3")
          .contains("epoch_scoped_rows_orphaned=transition_state,detections,source_status")
          .contains("churn_freezes_rearmed=true");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void recordResetAfterCommit_whenTransactionsCommit_clampsNegativeEpochCounts() {
    var registry = new SimpleMeterRegistry();
    var telemetry = new MicrometerWatchboxTrackingResetTelemetry(registry);

    commitAfter(() -> telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 0));
    commitAfter(() -> telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), -7));

    assertThat(
            registry.get(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter().count())
        .isEqualTo(2);
    assertThat(
            registry
                .get(MicrometerWatchboxTrackingResetTelemetry.CONFIG_EPOCHS_BUMPED_TOTAL)
                .counter()
                .count())
        .isZero();
  }

  @Test
  void recordResetAfterCommit_whenCalledWithoutTransaction_throws() {
    var telemetry = new MicrometerWatchboxTrackingResetTelemetry(new SimpleMeterRegistry());

    assertThatThrownBy(() -> telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 3))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active transaction synchronization");
  }

  @Test
  void recordResetAfterCommit_whenTransactionRollsBack_emitsNoEvidence() {
    var registry = new SimpleMeterRegistry();
    var telemetry = new MicrometerWatchboxTrackingResetTelemetry(registry);
    var logger = (Logger) LoggerFactory.getLogger(MicrometerWatchboxTrackingResetTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    TransactionSynchronizationManager.initSynchronization();
    try {
      telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 3);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

      assertThat(registry.find(MicrometerWatchboxTrackingResetTelemetry.RESETS_TOTAL).counter())
          .isNull();
      assertThat(appender.list).isEmpty();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void recordResetAfterCommit_whenRegistryFails_warnsOnceAndNeverPropagates() {
    var registry = mock(MeterRegistry.class);
    when(registry.counter(anyString()))
        .thenThrow(new IllegalStateException("registry unavailable"));
    var telemetry = new MicrometerWatchboxTrackingResetTelemetry(registry);
    var logger = (Logger) LoggerFactory.getLogger(MicrometerWatchboxTrackingResetTelemetry.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      assertThatCode(
              () ->
                  commitAfter(
                      () -> telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 3)))
          .doesNotThrowAnyException();
      assertThatCode(
              () ->
                  commitAfter(
                      () -> telemetry.recordResetAfterCommit(new RuntimeFeatureAudience.All(), 4)))
          .doesNotThrowAnyException();

      assertThat(appender.list)
          .filteredOn(
              event ->
                  event.getFormattedMessage().contains("WATCHBOX_TRACKING_RESET_TELEMETRY_FAILED"))
          .singleElement()
          .extracting(ILoggingEvent::getFormattedMessage)
          .asString()
          .contains("WATCHBOX_TRACKING_RESET_TELEMETRY_FAILED")
          .contains("exception_class=IllegalStateException");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static void commitAfter(Runnable action) {
    TransactionSynchronizationManager.initSynchronization();
    try {
      action.run();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
