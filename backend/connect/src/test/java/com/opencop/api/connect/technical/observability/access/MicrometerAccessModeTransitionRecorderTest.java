// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.observability.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder.Cause;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerAccessModeTransitionRecorderTest {

  @Test
  void transitionMetricUsesOnlyBoundedContextLabels() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerAccessModeTransitionRecorder(registry);

    recorder.record(
        TransportKind.TCP, AccessMode.READ, AccessMode.WRITE, Cause.DESIRED_STATE_RECONCILIATION);

    var counter = registry.get(MicrometerAccessModeTransitionRecorder.TRANSITIONS_TOTAL).counter();
    assertThat(counter.count()).isEqualTo(1.0d);
    assertThat(counter.getId().getTags())
        .extracting(tag -> tag.getKey() + "=" + tag.getValue())
        .containsExactlyInAnyOrder(
            "transport=TCP", "old_mode=R", "new_mode=W", "cause=desired_state_reconciliation");
    assertThat(counter.getId().getTags()).noneMatch(tag -> tag.getKey().contains("source"));
  }

  @Test
  void runtimeStartUsesBoundedNoneLabelForMissingOldMode() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerAccessModeTransitionRecorder(registry);

    recorder.record(TransportKind.TCP, null, AccessMode.READ_WRITE, Cause.RUNTIME_START);

    var counter =
        registry
            .get(MicrometerAccessModeTransitionRecorder.TRANSITIONS_TOTAL)
            .tags("old_mode", "NONE", "new_mode", "RW", "cause", "runtime_start")
            .counter();
    assertThat(counter.count()).isEqualTo(1.0d);
  }

  @Test
  void incompatibilityMetricUsesOnlyBoundedContextLabels() {
    var registry = new SimpleMeterRegistry();
    var recorder = new MicrometerAccessModeTransitionRecorder(registry);

    recorder.recordIncompatibility(TransportKind.UDP, AccessMode.READ_WRITE);

    var counter =
        registry
            .get(MicrometerAccessModeTransitionRecorder.INCOMPATIBILITIES_TOTAL)
            .tags("transport", "UDP", "mode", "RW", "cause", "incompatible_transport_access")
            .counter();
    assertThat(counter.count()).isEqualTo(1.0d);
    assertThat(counter.getId().getTags()).noneMatch(tag -> tag.getKey().contains("source"));
  }
}
