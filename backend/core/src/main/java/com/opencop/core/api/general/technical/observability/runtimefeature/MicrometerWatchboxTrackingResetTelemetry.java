// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.runtimefeature;

import com.opencop.core.api.general.ports.gateway.runtimefeature.WatchboxTrackingResetTelemetry;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class MicrometerWatchboxTrackingResetTelemetry implements WatchboxTrackingResetTelemetry {

  public static final String RESETS_TOTAL = "opencop_watchbox_tracking_resets_total";
  public static final String CONFIG_EPOCHS_BUMPED_TOTAL =
      "opencop_watchbox_tracking_config_epochs_bumped_total";

  private static final Logger log =
      LoggerFactory.getLogger(MicrometerWatchboxTrackingResetTelemetry.class);

  private final MeterRegistry meterRegistry;
  private final AtomicBoolean failureWarningEmitted = new AtomicBoolean();

  public MicrometerWatchboxTrackingResetTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  @Override
  public void recordResetAfterCommit(
      RuntimeFeatureAudience audience, int activeConfigEpochsBumped) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "Watchbox tracking reset requires active transaction synchronization");
    }
    String audienceMode = audienceMode(audience);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            log.info(
                "WATCHBOX_TRACKING_RESET trigger=runtime_feature_reenable audience_mode={} active_config_epochs_bumped={} epoch_scoped_rows_orphaned=transition_state,detections,source_status churn_freezes_rearmed=true",
                audienceMode,
                activeConfigEpochsBumped);
            recordReset(activeConfigEpochsBumped);
          }
        });
  }

  private void recordReset(int activeConfigEpochsBumped) {
    try {
      meterRegistry.counter(RESETS_TOTAL).increment();
      meterRegistry
          .counter(CONFIG_EPOCHS_BUMPED_TOTAL)
          .increment(Math.max(0, activeConfigEpochsBumped));
    } catch (RuntimeException exception) {
      warnFailureOnce(exception.getClass().getSimpleName());
    }
  }

  private static String audienceMode(RuntimeFeatureAudience audience) {
    return switch (audience) {
      case RuntimeFeatureAudience.None _ -> "NONE";
      case RuntimeFeatureAudience.All _ -> "ALL";
      case RuntimeFeatureAudience.UserGroups _ -> "USER_GROUPS";
    };
  }

  private void warnFailureOnce(String exceptionClass) {
    try {
      if (log.isWarnEnabled() && failureWarningEmitted.compareAndSet(false, true)) {
        log.warn("WATCHBOX_TRACKING_RESET_TELEMETRY_FAILED exception_class={}", exceptionClass);
      }
    } catch (RuntimeException _) {
      // Telemetry must never alter the runtime-feature transaction.
    }
  }
}
