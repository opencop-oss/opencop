// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.user;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.observability.BoundedMetricTagGuard;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CurrentAccountSessionTelemetry {

  public static final String SESSION_INVALIDATIONS_TOTAL =
      "opencop_current_account_session_invalidations_total";

  private static final String TAG_REASON = "reason";
  private static final Set<String> REASON_VALUES =
      BoundedMetricTagGuard.allowedValues(CurrentAccountAccessDenialReason.values());
  private static final Logger log = LoggerFactory.getLogger(CurrentAccountSessionTelemetry.class);

  private final MeterRegistry meterRegistry;
  private final AtomicBoolean failureWarningEmitted = new AtomicBoolean();

  public CurrentAccountSessionTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordInvalidation(CurrentAccountAccessDenialReason reason) {
    try {
      meterRegistry
          .counter(
              SESSION_INVALIDATIONS_TOTAL,
              TAG_REASON,
              BoundedMetricTagGuard.requireAllowed(TAG_REASON, reason.value(), REASON_VALUES))
          .increment();
    } catch (RuntimeException exception) {
      warnFailureOnce(exception.getClass().getSimpleName());
    }
  }

  private void warnFailureOnce(String exceptionClass) {
    if (log.isWarnEnabled() && failureWarningEmitted.compareAndSet(false, true)) {
      log.warn("CURRENT_ACCOUNT_SESSION_TELEMETRY_FAILED exception_class={}", exceptionClass);
    }
  }
}
