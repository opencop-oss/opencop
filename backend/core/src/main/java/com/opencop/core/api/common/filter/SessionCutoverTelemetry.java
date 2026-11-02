// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionCutoverTelemetry {

  public static final String INVALID_SESSION_RECOVERIES_TOTAL =
      "opencop_bff_invalid_session_recoveries_total";
  public static final String LEGACY_CSRF_COOKIE_OBSERVATIONS_TOTAL =
      "opencop_bff_legacy_csrf_cookie_observations_total";

  private static final Logger log = LoggerFactory.getLogger(SessionCutoverTelemetry.class);

  private final MeterRegistry meterRegistry;
  private final AtomicBoolean failureWarningEmitted = new AtomicBoolean();

  public SessionCutoverTelemetry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordInvalidSessionRecovery() {
    increment(INVALID_SESSION_RECOVERIES_TOTAL);
  }

  public void recordLegacyCsrfCookieObservation() {
    increment(LEGACY_CSRF_COOKIE_OBSERVATIONS_TOTAL);
  }

  private void increment(String metricName) {
    try {
      meterRegistry.counter(metricName).increment();
    } catch (RuntimeException exception) {
      if (log.isWarnEnabled() && failureWarningEmitted.compareAndSet(false, true)) {
        log.warn(
            "BFF_SESSION_CUTOVER_TELEMETRY_FAILED metric={} exception_class={}",
            metricName,
            exception.getClass().getSimpleName());
      }
    }
  }
}
