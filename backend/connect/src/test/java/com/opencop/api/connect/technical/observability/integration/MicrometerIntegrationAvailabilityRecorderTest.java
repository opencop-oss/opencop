// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.integration;

import static com.opencop.api.connect.technical.observability.integration.MicrometerIntegrationAvailabilityRecorder.TRANSPORT_TAG;
import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MicrometerIntegrationAvailabilityRecorderTest {

  @Test
  void registersEveryTransportSeriesAtZeroBeforeAnyOnset() {
    var registry = new SimpleMeterRegistry();

    new MicrometerIntegrationAvailabilityRecorder(registry);

    var counters =
        registry
            .get(MicrometerIntegrationAvailabilityRecorder.INTEGRATION_UNAVAILABLE_TOTAL)
            .counters();
    assertThat(counters)
        .extracting(counter -> counter.getId().getTag(TRANSPORT_TAG))
        .containsExactlyInAnyOrder(
            Arrays.stream(TransportKind.values()).map(Enum::name).toArray(String[]::new));
    assertThat(counters).allSatisfy(counter -> assertThat(counter.count()).isZero());
  }

  @Test
  void countsUnavailableIntegrationsByTransportOnly() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerIntegrationAvailabilityRecorder(registry);

    recorder.recordUnavailable(TransportKind.TCP);
    recorder.recordUnavailable(TransportKind.TCP);
    recorder.recordUnavailable(TransportKind.UDP);

    var tcp =
        registry
            .get(MicrometerIntegrationAvailabilityRecorder.INTEGRATION_UNAVAILABLE_TOTAL)
            .tag(TRANSPORT_TAG, TransportKind.TCP.name())
            .counter();
    assertThat(tcp.count()).isEqualTo(2.0d);
    assertThat(tcp.getId().getTags())
        .extracting(tag -> tag.getKey() + "=" + tag.getValue())
        .containsExactly(TRANSPORT_TAG + "=" + TransportKind.TCP.name());
    assertThat(
            registry
                .get(MicrometerIntegrationAvailabilityRecorder.INTEGRATION_UNAVAILABLE_TOTAL)
                .tag(TRANSPORT_TAG, TransportKind.UDP.name())
                .counter()
                .count())
        .isEqualTo(1.0d);
  }
}
