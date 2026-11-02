// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.integration;

import com.opencop.api.connect.ports.gateway.observability.integration.IntegrationAvailabilityRecorder;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Counts unavailable-integration onsets by transport alone: integration, schema version, and source
 * identities are unbounded, so they go to the onset log line instead of metric labels.
 *
 * <p>Every transport series is registered at zero on construction. Onsets cluster at startup,
 * before the first scrape, and a series that first appears already above zero is invisible to
 * {@code increase()} and {@code rate()}.
 */
public final class MicrometerIntegrationAvailabilityRecorder
    implements IntegrationAvailabilityRecorder {

  public static final String INTEGRATION_UNAVAILABLE_TOTAL =
      "opencop_connect_integration_unavailable_total";
  public static final String TRANSPORT_TAG = "transport";

  private final Map<TransportKind, Counter> counters = new EnumMap<>(TransportKind.class);

  public MicrometerIntegrationAvailabilityRecorder(MeterRegistry meterRegistry) {
    Objects.requireNonNull(meterRegistry, "meterRegistry");
    for (var transport : TransportKind.values()) {
      counters.put(
          transport,
          meterRegistry.counter(INTEGRATION_UNAVAILABLE_TOTAL, TRANSPORT_TAG, transport.name()));
    }
  }

  @Override
  public void recordUnavailable(TransportKind transport) {
    counters.get(transport).increment();
  }
}
