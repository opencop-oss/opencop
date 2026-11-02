// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.OnConnectBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.ReceiveIdleMessageBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.ReceiveIdleReconnectBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.RecurringBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConnectionBehaviorRegistryTest {

  private static final TestConnectionSchema INTEGRATION =
      new TestConnectionSchema(
          UUID.fromString("88d955d2-8e31-4f63-970a-a5f84e59af85"), 1, "Test", "Test integration");
  private static final ConnectionMessageFactory MESSAGE_FACTORY = (_, _) -> new byte[] {1};
  private static final NoConnectionSettings NO_SETTINGS = NoConnectionSettings.INSTANCE;

  @Test
  void resolvesOnlyTheExactRegisteredIntegrationSchema() {
    var registry = new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of());

    assertThat(
            registry.find(
                new IntegrationConnectionSchemaReference(
                    INTEGRATION.integrationId(), INTEGRATION.schemaVersion())))
        .containsSame(INTEGRATION);
    assertThat(
            registry.find(new IntegrationConnectionSchemaReference(INTEGRATION.integrationId(), 2)))
        .isEmpty();
  }

  @Test
  void compilesApplicableCapabilitiesInOrderAndEvaluatesApplicabilityOnce() {
    var applicabilityCalls = new AtomicInteger();
    var recurring = new OnConnectAndRecurringBehavior(applicabilityCalls);
    var first = new NamedOnConnectBehavior("first", -1);
    var later = new NamedOnConnectBehavior("z-later", 0);
    var additionalRecurring =
        new NamedRecurringBehavior("additional-recurring", 5, Duration.ofSeconds(8));
    var registry =
        new ConnectionBehaviorRegistry(
            List.of(INTEGRATION), List.of(later, additionalRecurring, recurring, first));

    var plan = registry.planFor(INTEGRATION, NO_SETTINGS);

    assertThat(plan.onConnect())
        .extracting(action -> action.name())
        .containsExactly("first", "recurring", "z-later");
    assertThat(plan.recurring())
        .extracting(action -> action.message().name(), action -> action.delay())
        .containsExactly(
            tuple("recurring", Duration.ofSeconds(5)),
            tuple("additional-recurring", Duration.ofSeconds(8)));
    assertThat(applicabilityCalls).hasValue(1);
  }

  @Test
  void rejectsDuplicateRegisteredIntegrationKeys() {
    var duplicate =
        new TestConnectionSchema(
            INTEGRATION.integrationId(), INTEGRATION.schemaVersion(), "Other", "Other metadata");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConnectionBehaviorRegistry(List.of(INTEGRATION, duplicate), List.of()))
        .withMessageContaining("Duplicate connection schema")
        .withMessageContaining(INTEGRATION.integrationId().toString());
  }

  @Test
  void rejectsNonPositiveIntegrationSchemaVersions() {
    // A plugin's own schema type may not validate its version; the registry must.
    var invalid = mock(IntegrationConnectionSchema.class);
    when(invalid.integration()).thenReturn(INTEGRATION.integration());
    when(invalid.schemaVersion()).thenReturn(0);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionBehaviorRegistry(List.of(invalid), List.of()))
        .withMessage("integration.schemaVersion must be positive");
  }

  @Test
  void rejectsContributionsOwnedByAnUnregisteredIntegration() {
    var unknown =
        new TestConnectionSchema(
            UUID.fromString("f7df4880-a1b0-420f-a03c-dba3c6f64e25"),
            1,
            "Unknown",
            "Unknown integration");
    OnConnectBehavior<NoConnectionSettings> orphan =
        new OnConnectBehavior<>() {
          @Override
          public IntegrationConnectionSchema schema() {
            return unknown;
          }

          @Override
          public Class<NoConnectionSettings> settingsType() {
            return NoConnectionSettings.class;
          }

          @Override
          public String name() {
            return "orphan";
          }

          @Override
          public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
            return MESSAGE_FACTORY;
          }
        };

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(orphan)))
        .withMessageContaining("Unregistered connection schema")
        .withMessageContaining(unknown.integration().id().toString());
  }

  @Test
  void rejectsDuplicateBehaviorNamesWithinAnIntegrationSchema() {
    var first = new NamedOnConnectBehavior("duplicate", -1);
    var second = new NamedOnConnectBehavior("duplicate", 1);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(first, second)))
        .withMessageContaining("Duplicate behavior name")
        .withMessageContaining("duplicate");
  }

  @Test
  void rejectsMultipleReceiveIdleMessageCapabilitiesBeforeApplicability() {
    var first = new IdleMessageBehavior("first-idle");
    var second = new IdleMessageBehavior("second-idle");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(first, second)))
        .withMessageContaining("receive-idle message")
        .withMessageContaining(INTEGRATION.integrationId().toString());
  }

  @Test
  void rejectsMultipleReceiveIdleReconnectCapabilitiesBeforeApplicability() {
    var first = new IdleReconnectBehavior("first-reconnect");
    var second = new IdleReconnectBehavior("second-reconnect");

    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(first, second)))
        .withMessageContaining("receive-idle reconnect")
        .withMessageContaining(INTEGRATION.integrationId().toString());
  }

  @Test
  void compilesTheSingularReceiveIdleCapabilities() {
    ReceiveIdleMessageBehavior<NoConnectionSettings> idleMessage =
        new ReceiveIdleMessageBehavior<>() {
          @Override
          public IntegrationConnectionSchema schema() {
            return INTEGRATION;
          }

          @Override
          public Class<NoConnectionSettings> settingsType() {
            return NoConnectionSettings.class;
          }

          @Override
          public String name() {
            return "idle-message";
          }

          @Override
          public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
            return MESSAGE_FACTORY;
          }

          @Override
          public Duration idleThreshold(NoConnectionSettings settings) {
            return Duration.ofSeconds(12);
          }
        };
    ReceiveIdleReconnectBehavior<NoConnectionSettings> reconnect =
        new ReceiveIdleReconnectBehavior<>() {
          @Override
          public IntegrationConnectionSchema schema() {
            return INTEGRATION;
          }

          @Override
          public Class<NoConnectionSettings> settingsType() {
            return NoConnectionSettings.class;
          }

          @Override
          public String name() {
            return "idle-reconnect";
          }

          @Override
          public Duration reconnectAfterIdle(NoConnectionSettings settings) {
            return Duration.ofSeconds(30);
          }
        };
    var registry =
        new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(reconnect, idleMessage));

    var plan = registry.planFor(INTEGRATION, NO_SETTINGS);

    assertThat(plan.idleMessage())
        .hasValueSatisfying(
            action -> {
              assertThat(action.message().name()).isEqualTo("idle-message");
              assertThat(action.threshold()).isEqualTo(Duration.ofSeconds(12));
            });
    assertThat(plan.reconnectAfterIdle()).contains(Duration.ofSeconds(30));
  }

  @Test
  void returnsAnEmptyPlanWithoutResolvingDisabledCapabilityValues() {
    RecurringBehavior<NoConnectionSettings> disabled =
        new RecurringBehavior<>() {
          @Override
          public IntegrationConnectionSchema schema() {
            return INTEGRATION;
          }

          @Override
          public Class<NoConnectionSettings> settingsType() {
            return NoConnectionSettings.class;
          }

          @Override
          public String name() {
            return "disabled";
          }

          @Override
          public boolean isEnabled(NoConnectionSettings settings) {
            return false;
          }

          @Override
          public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
            throw new AssertionError("Disabled capability values must not be resolved");
          }

          @Override
          public Duration delay(NoConnectionSettings settings) {
            throw new AssertionError("Disabled capability values must not be resolved");
          }
        };
    var registry = new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(disabled));

    var plan = registry.planFor(INTEGRATION, NO_SETTINGS);

    assertThat(plan).isSameAs(ConnectionBehaviorPlan.empty());
  }

  @Test
  void rejectsUnknownPlanLookups() {
    var unknown =
        new TestConnectionSchema(
            UUID.fromString("2e60fc7b-b248-48ac-8482-3888f6d47501"),
            1,
            "Unknown",
            "Unknown integration");
    var registry = new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.planFor(unknown, NO_SETTINGS))
        .withMessageContaining("Unknown connection schema")
        .withMessageContaining(unknown.integration().id().toString());
  }

  @Test
  void rejectsInvalidValuesResolvedForAConnection() {
    RecurringBehavior<NoConnectionSettings> invalid =
        new RecurringBehavior<>() {
          @Override
          public IntegrationConnectionSchema schema() {
            return INTEGRATION;
          }

          @Override
          public Class<NoConnectionSettings> settingsType() {
            return NoConnectionSettings.class;
          }

          @Override
          public String name() {
            return "invalid-delay";
          }

          @Override
          public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
            return MESSAGE_FACTORY;
          }

          @Override
          public Duration delay(NoConnectionSettings settings) {
            return Duration.ZERO;
          }
        };
    var registry = new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(invalid));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.planFor(INTEGRATION, NO_SETTINGS))
        .withMessage("delay must be positive");
  }

  @Test
  void rejectsBlankBehaviorNamesAtRegistration() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ConnectionBehaviorRegistry(
                    List.of(INTEGRATION), List.of(new NamedOnConnectBehavior(" ", 0))))
        .withMessage("Behavior name must not be blank");
  }

  @Test
  void resolvesEveryValueFromTheSuppliedSettings() {
    var registry =
        new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(new SettingsDrivenBehavior()));

    var fast = registry.planFor(INTEGRATION, new TestSettings(true, Duration.ofSeconds(5)));
    var slow = registry.planFor(INTEGRATION, new TestSettings(true, Duration.ofSeconds(40)));
    var disabled = registry.planFor(INTEGRATION, new TestSettings(false, Duration.ofSeconds(5)));

    assertThat(fast.recurring().getFirst().delay()).isEqualTo(Duration.ofSeconds(5));
    assertThat(slow.recurring().getFirst().delay()).isEqualTo(Duration.ofSeconds(40));
    assertThat(disabled).isSameAs(ConnectionBehaviorPlan.empty());
  }

  @Test
  void rejectsSettingsOfAnotherTypeThanTheContributionReads() {
    var registry =
        new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of(new SettingsDrivenBehavior()));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> registry.planFor(INTEGRATION, NO_SETTINGS))
        .withMessageContaining("settings-driven")
        .withMessageContaining(TestSettings.class.getName())
        .withMessageContaining(NoConnectionSettings.class.getName());
  }

  @Test
  void requiresSettings() {
    var registry = new ConnectionBehaviorRegistry(List.of(INTEGRATION), List.of());

    assertThatNullPointerException()
        .isThrownBy(() -> registry.planFor(INTEGRATION, null))
        .withMessage("settings");
  }

  @Test
  void exposesOnlyPlanCompilationAsAPublicOperation() {
    assertThat(
            Arrays.stream(ConnectionBehaviorRegistry.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName()))
        .containsExactlyInAnyOrder("planFor", "find");
  }

  private record NamedOnConnectBehavior(String name, int order)
      implements OnConnectBehavior<NoConnectionSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {

      return NoConnectionSettings.class;
    }

    @Override
    public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
      return MESSAGE_FACTORY;
    }
  }

  private record OnConnectAndRecurringBehavior(AtomicInteger applicabilityCalls)
      implements OnConnectBehavior<NoConnectionSettings>, RecurringBehavior<NoConnectionSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {

      return NoConnectionSettings.class;
    }

    @Override
    public String name() {
      return "recurring";
    }

    @Override
    public boolean isEnabled(NoConnectionSettings settings) {
      applicabilityCalls.incrementAndGet();
      return true;
    }

    @Override
    public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
      return MESSAGE_FACTORY;
    }

    @Override
    public Duration delay(NoConnectionSettings settings) {
      return Duration.ofSeconds(5);
    }
  }

  private record NamedRecurringBehavior(String name, int order, Duration delay)
      implements RecurringBehavior<NoConnectionSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {

      return NoConnectionSettings.class;
    }

    @Override
    public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
      return MESSAGE_FACTORY;
    }

    @Override
    public Duration delay(NoConnectionSettings settings) {
      return delay;
    }
  }

  private record IdleMessageBehavior(String name)
      implements ReceiveIdleMessageBehavior<NoConnectionSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {

      return NoConnectionSettings.class;
    }

    @Override
    public boolean isEnabled(NoConnectionSettings settings) {
      throw new AssertionError("Applicability must not be evaluated during registry construction");
    }

    @Override
    public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
      return MESSAGE_FACTORY;
    }

    @Override
    public Duration idleThreshold(NoConnectionSettings settings) {
      return Duration.ofSeconds(10);
    }
  }

  private record IdleReconnectBehavior(String name)
      implements ReceiveIdleReconnectBehavior<NoConnectionSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<NoConnectionSettings> settingsType() {

      return NoConnectionSettings.class;
    }

    @Override
    public boolean isEnabled(NoConnectionSettings settings) {
      throw new AssertionError("Applicability must not be evaluated during registry construction");
    }

    @Override
    public Duration reconnectAfterIdle(NoConnectionSettings settings) {
      return Duration.ofSeconds(30);
    }
  }

  private record TestSettings(boolean enabled, Duration delay) {}

  private record SettingsDrivenBehavior() implements RecurringBehavior<TestSettings> {

    @Override
    public IntegrationConnectionSchema schema() {
      return INTEGRATION;
    }

    @Override
    public Class<TestSettings> settingsType() {
      return TestSettings.class;
    }

    @Override
    public String name() {
      return "settings-driven";
    }

    @Override
    public boolean isEnabled(TestSettings settings) {
      return settings.enabled();
    }

    @Override
    public ConnectionMessageFactory messageFactory(TestSettings settings) {
      return MESSAGE_FACTORY;
    }

    @Override
    public Duration delay(TestSettings settings) {
      return settings.delay();
    }
  }
}
