// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.behavior;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.IdleMessageAction;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.MessageAction;
import com.opencop.core.sdk.business.model.integration.connection.behavior.ConnectionBehaviorPlan.RecurringAction;
import com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.OnConnectBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.ReceiveIdleMessageBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.ReceiveIdleReconnectBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.RecurringBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.message.MessageProducingBehavior;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Validates registered connection behavior beans and compiles their applicable capabilities.
 *
 * <p>The registry is immutable after construction and safe for concurrent plan compilation. It
 * hides contribution collection, ordering, and execution-value assembly behind one lookup.
 * Construction rejects duplicate integration keys, orphan contributions, duplicate names, and
 * multiple receive-idle message or reconnect capabilities for one connection schema. These
 * singular-capability checks happen before contribution enablement is evaluated.
 */
public final class ConnectionBehaviorRegistry {

  private static final Comparator<IndexedContribution> CONTRIBUTION_ORDER =
      Comparator.comparingInt(IndexedContribution::order).thenComparing(IndexedContribution::name);

  private final Map<IntegrationKey, IntegrationConnectionSchema> schemas;
  private final Map<IntegrationKey, List<IndexedContribution>> contributionsBySchema;

  /**
   * Creates an immutable registry from the connection schema and contribution bean lists supplied
   * by dependency injection.
   *
   * @throws IllegalArgumentException when schema ownership, naming, or multiplicity is invalid
   */
  public ConnectionBehaviorRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<? extends ConnectionBehaviorContribution<?>> contributions) {
    Objects.requireNonNull(schemas, "schemas");
    Objects.requireNonNull(contributions, "contributions");

    var registeredSchemas = new HashMap<IntegrationKey, IntegrationConnectionSchema>();
    for (IntegrationConnectionSchema schema : schemas) {
      var key = IntegrationKey.from(schema);
      if (registeredSchemas.putIfAbsent(key, schema) != null) {
        throw new IllegalArgumentException("Duplicate connection schema: " + key);
      }
    }
    this.schemas = Map.copyOf(registeredSchemas);

    var indexed = new HashMap<IntegrationKey, List<IndexedContribution>>();
    var namesByIntegration = new HashMap<IntegrationKey, Set<String>>();
    var idleMessageIntegrations = new HashSet<IntegrationKey>();
    var idleReconnectIntegrations = new HashSet<IntegrationKey>();
    for (ConnectionBehaviorContribution<?> contribution : contributions) {
      Objects.requireNonNull(contribution, "contribution");
      var key = IntegrationKey.from(contribution.schema());
      if (!this.schemas.containsKey(key)) {
        throw new IllegalArgumentException(
            "Unregistered connection schema for contribution: " + key);
      }
      var name = requireName(contribution.name());
      if (!namesByIntegration.computeIfAbsent(key, _ -> new HashSet<>()).add(name)) {
        throw new IllegalArgumentException(
            "Duplicate behavior name '" + name + "' for connection schema: " + key);
      }
      if (contribution instanceof ReceiveIdleMessageBehavior && !idleMessageIntegrations.add(key)) {
        throw new IllegalArgumentException(
            "Multiple receive-idle message capabilities for connection schema: " + key);
      }
      if (contribution instanceof ReceiveIdleReconnectBehavior
          && !idleReconnectIntegrations.add(key)) {
        throw new IllegalArgumentException(
            "Multiple receive-idle reconnect capabilities for connection schema: " + key);
      }
      indexed
          .computeIfAbsent(key, _ -> new ArrayList<>())
          .add(new IndexedContribution(contribution, name, contribution.order()));
    }
    indexed.values().forEach(values -> values.sort(CONTRIBUTION_ORDER));
    var copied = new HashMap<IntegrationKey, List<IndexedContribution>>();
    indexed.forEach((key, values) -> copied.put(key, List.copyOf(values)));
    contributionsBySchema = Map.copyOf(copied);
  }

  /**
   * Compiles all applicable capabilities for one registered connection schema from that
   * connection's effective settings.
   *
   * <p>Applicability is evaluated once per contribution. Applicable contributions are ordered by
   * ascending order then name, and all resolved factories, delays, and thresholds are validated by
   * the returned immutable plan.
   *
   * @throws IllegalArgumentException when the schema is unknown, a contribution reads another
   *     settings type, or a resolved value is invalid
   */
  public ConnectionBehaviorPlan planFor(IntegrationConnectionSchema schema, Object settings) {
    Objects.requireNonNull(settings, "settings");
    var key = IntegrationKey.from(schema);
    if (!schemas.containsKey(key)) {
      throw new IllegalArgumentException("Unknown connection schema: " + key);
    }

    var accumulator = new PlanAccumulator();
    for (IndexedContribution indexed : contributionsBySchema.getOrDefault(key, List.of())) {
      accumulator.add(indexed.name(), indexed.contribution(), settings);
    }
    return accumulator.toPlan();
  }

  /** Resolves one exact registered connection schema from a persisted reference. */
  public Optional<IntegrationConnectionSchema> find(
      IntegrationConnectionSchemaReference reference) {
    Objects.requireNonNull(reference, "reference");
    return Optional.ofNullable(
        schemas.get(new IntegrationKey(reference.integrationId(), reference.schemaVersion())));
  }

  private static final class PlanAccumulator {

    private final List<MessageAction> onConnect = new ArrayList<>();
    private final List<RecurringAction> recurring = new ArrayList<>();
    private IdleMessageAction idleMessage;
    private Duration reconnectAfterIdle;

    /** Captures the contribution's settings type so every resolved value reads typed settings. */
    private <S> void add(
        String name, ConnectionBehaviorContribution<S> contribution, Object rawSettings) {
      var settings = typedSettings(name, contribution, rawSettings);
      if (!contribution.isEnabled(settings)) {
        return;
      }
      MessageAction message = null;
      if (contribution instanceof OnConnectBehavior<S> onConnectBehavior) {
        message = messageAction(name, onConnectBehavior, settings);
        onConnect.add(message);
      }
      if (contribution instanceof RecurringBehavior<S> recurringBehavior) {
        message = message == null ? messageAction(name, recurringBehavior, settings) : message;
        recurring.add(new RecurringAction(message, recurringBehavior.delay(settings)));
      }
      if (contribution instanceof ReceiveIdleMessageBehavior<S> receiveIdleMessageBehavior) {
        message =
            message == null ? messageAction(name, receiveIdleMessageBehavior, settings) : message;
        idleMessage =
            new IdleMessageAction(message, receiveIdleMessageBehavior.idleThreshold(settings));
      }
      if (contribution instanceof ReceiveIdleReconnectBehavior<S> reconnectBehavior) {
        reconnectAfterIdle = reconnectBehavior.reconnectAfterIdle(settings);
      }
    }

    private ConnectionBehaviorPlan toPlan() {
      var plan =
          new ConnectionBehaviorPlan(
              onConnect,
              recurring,
              Optional.ofNullable(idleMessage),
              Optional.ofNullable(reconnectAfterIdle));
      return plan.isEmpty() ? ConnectionBehaviorPlan.empty() : plan;
    }

    private static <S> S typedSettings(
        String name, ConnectionBehaviorContribution<S> contribution, Object settings) {
      var settingsType = contribution.settingsType();
      if (!settingsType.isInstance(settings)) {
        throw new IllegalArgumentException(
            "Behavior '"
                + name
                + "' reads "
                + settingsType.getName()
                + " settings, not "
                + settings.getClass().getName());
      }
      return settingsType.cast(settings);
    }

    private static <S> MessageAction messageAction(
        String name, MessageProducingBehavior<S> behavior, S settings) {
      return new MessageAction(name, behavior.messageFactory(settings));
    }
  }

  private record IndexedContribution(
      ConnectionBehaviorContribution<?> contribution, String name, int order) {}

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Behavior name must not be blank");
    }
    return name;
  }

  private record IntegrationKey(UUID id, int schemaVersion) {

    private IntegrationKey {
      Objects.requireNonNull(id, "integration.id");
      if (schemaVersion <= 0) {
        throw new IllegalArgumentException("integration.schemaVersion must be positive");
      }
    }

    private static IntegrationKey from(IntegrationConnectionSchema schema) {
      Objects.requireNonNull(schema, "schema");
      Objects.requireNonNull(schema.integration(), "schema.integration");
      return new IntegrationKey(schema.integration().id(), schema.schemaVersion());
    }
  }
}
