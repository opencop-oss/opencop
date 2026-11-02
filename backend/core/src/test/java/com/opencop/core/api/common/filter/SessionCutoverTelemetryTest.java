// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class SessionCutoverTelemetryTest {

  @Test
  void cutoverEventsUseDistinctCountersWithoutSecurityValueTags() {
    var registry = new SimpleMeterRegistry();
    try {
      var telemetry = new SessionCutoverTelemetry(registry);
      telemetry.recordInvalidSessionRecovery();
      telemetry.recordLegacyCsrfCookieObservation();

      assertThat(
              registry
                  .get(SessionCutoverTelemetry.INVALID_SESSION_RECOVERIES_TOTAL)
                  .counter()
                  .getId()
                  .getTags())
          .isEmpty();
      assertThat(
              registry
                  .get(SessionCutoverTelemetry.LEGACY_CSRF_COOKIE_OBSERVATIONS_TOTAL)
                  .counter()
                  .getId()
                  .getTags())
          .isEmpty();
    } finally {
      registry.close();
    }
  }

  @Test
  void telemetryFailureDoesNotChangeCutoverBehavior() {
    var registry = mock(MeterRegistry.class);
    when(registry.counter(SessionCutoverTelemetry.INVALID_SESSION_RECOVERIES_TOTAL))
        .thenThrow(new IllegalStateException("meter unavailable"));
    var telemetry = new SessionCutoverTelemetry(registry);

    assertThatCode(telemetry::recordInvalidSessionRecovery).doesNotThrowAnyException();
  }
}
