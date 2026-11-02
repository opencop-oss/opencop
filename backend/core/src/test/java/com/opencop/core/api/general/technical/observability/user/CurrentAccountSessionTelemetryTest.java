// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.observability.user;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CurrentAccountSessionTelemetryTest {

  @Test
  void invalidationReasonsAreAllowlistedAndUseOnlyTheFixedReasonTag() {
    assertEquals(
        Set.of("inactive", "stale_generation", "missing_account"),
        Arrays.stream(CurrentAccountAccessDenialReason.values())
            .map(CurrentAccountAccessDenialReason::value)
            .collect(Collectors.toSet()));

    var registry = new SimpleMeterRegistry();
    try {
      var telemetry = new CurrentAccountSessionTelemetry(registry);

      for (var reason : CurrentAccountAccessDenialReason.values()) {
        telemetry.recordInvalidation(reason);
        Counter counter =
            registry
                .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
                .tag("reason", reason.value())
                .counter();
        assertNotNull(counter);
        assertEquals(1.0d, counter.count());
        assertEquals(
            Set.of("reason"),
            counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()));
      }
    } finally {
      registry.close();
    }
  }

  @Test
  void meterFailureDoesNotEscapeTheTelemetryBoundary() {
    var registry = mock(MeterRegistry.class);
    when(registry.counter(anyString(), any(String[].class)))
        .thenThrow(new IllegalStateException("meter unavailable"));
    var telemetry = new CurrentAccountSessionTelemetry(registry);

    assertDoesNotThrow(
        () -> telemetry.recordInvalidation(CurrentAccountAccessDenialReason.INACTIVE));
  }
}
