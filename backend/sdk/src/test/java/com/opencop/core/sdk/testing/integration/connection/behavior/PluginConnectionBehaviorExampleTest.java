// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.OnConnectBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Copyable example of testing a focused plugin contribution through the real SDK registry. */
class PluginConnectionBehaviorExampleTest {

  @Test
  void compilesThePluginContribution() {
    var schema =
        new TestConnectionSchema(
            UUID.fromString("5f557d03-5d77-41bd-9898-125e293a0de6"),
            1,
            "Example",
            "Example plugin integration");
    var contribution = new ExamplePresenceBehavior(schema);
    var registry = ConnectionBehaviorRegistryFixture.registryFor(schema, contribution);

    var plan = registry.planFor(schema, new ExampleSettings("Alpha"));

    assertThat(plan.onConnect())
        .extracting(action -> action.name())
        .containsExactly("example-presence");
  }

  private record ExampleSettings(String callsign) {}

  private record ExamplePresenceBehavior(IntegrationConnectionSchema schema)
      implements OnConnectBehavior<ExampleSettings> {

    @Override
    public Class<ExampleSettings> settingsType() {
      return ExampleSettings.class;
    }

    @Override
    public String name() {
      return "example-presence";
    }

    @Override
    public ConnectionMessageFactory messageFactory(ExampleSettings settings) {
      return (_, _) -> settings.callsign().getBytes(StandardCharsets.UTF_8);
    }
  }
}
