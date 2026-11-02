// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubOnConnectBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubReceiveIdleMessageBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubReceiveIdleReconnectBehavior;
import com.opencop.core.sdk.testing.integration.connection.behavior.capability.StubRecurringBehavior;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorTestKitTest {

  @Test
  void compilesAFocusedStubThroughTheRealRegistryWithoutSpringOrMocks() {
    var schema =
        new TestConnectionSchema(
            UUID.fromString("631dd473-8aa7-45e7-b9fe-87379ce03b80"),
            2,
            "Example",
            "Example integration");
    var behavior = new StubOnConnectBehavior(schema, "presence", (_, _) -> new byte[] {4, 2});
    var registry = ConnectionBehaviorRegistryFixture.registryFor(schema, behavior);

    var plan = registry.planFor(schema, NoConnectionSettings.INSTANCE);

    assertThat(plan.onConnect()).extracting(action -> action.name()).containsExactly("presence");
  }

  @Test
  void providesFocusedStubsForEveryTimedTriggerCapability() {
    var schema =
        new TestConnectionSchema(
            UUID.fromString("40e49e91-d18e-4c9e-8ff2-f268a927ee45"),
            1,
            "Timed",
            "Timed test integration");
    var events = new LifecycleEventRecorder();
    var factory = new RecordingConnectionMessageFactory("message", new byte[] {1}, events);
    var recurring = new StubRecurringBehavior(schema, "recurring", factory, Duration.ofSeconds(5));
    var idleMessage =
        new StubReceiveIdleMessageBehavior(schema, "idle-message", factory, Duration.ofSeconds(10));
    var idleReconnect =
        new StubReceiveIdleReconnectBehavior(schema, "idle-reconnect", Duration.ofSeconds(30));
    var registry =
        ConnectionBehaviorRegistryFixture.registryFor(
            schema, recurring, idleMessage, idleReconnect);

    var plan = registry.planFor(schema, NoConnectionSettings.INSTANCE);

    assertThat(plan.recurring())
        .singleElement()
        .extracting(action -> action.delay())
        .isEqualTo(Duration.ofSeconds(5));
    assertThat(plan.idleMessage())
        .get()
        .extracting(action -> action.threshold())
        .isEqualTo(Duration.ofSeconds(10));
    assertThat(plan.reconnectAfterIdle()).contains(Duration.ofSeconds(30));
  }
}
