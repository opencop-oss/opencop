// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.behavior;

import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable execution plan compiled from applicable connection behavior contributions.
 *
 * <p>This model is consumed by the connection runtime; integration authors implement capability
 * interfaces instead of constructing it. Lists are defensively copied and every delay or threshold
 * is validated before connector startup.
 */
public record ConnectionBehaviorPlan(
    List<MessageAction> onConnect,
    List<RecurringAction> recurring,
    Optional<IdleMessageAction> idleMessage,
    Optional<Duration> reconnectAfterIdle) {

  private static final ConnectionBehaviorPlan EMPTY =
      new ConnectionBehaviorPlan(List.of(), List.of(), Optional.empty(), Optional.empty());

  public ConnectionBehaviorPlan {
    onConnect = List.copyOf(Objects.requireNonNull(onConnect, "onConnect"));
    recurring = List.copyOf(Objects.requireNonNull(recurring, "recurring"));
    Objects.requireNonNull(idleMessage, "idleMessage");
    Objects.requireNonNull(reconnectAfterIdle, "reconnectAfterIdle");
    reconnectAfterIdle.ifPresent(threshold -> requirePositive(threshold, "reconnectAfterIdle"));
  }

  /** Returns the shared plan containing no connection behavior. */
  public static ConnectionBehaviorPlan empty() {
    return EMPTY;
  }

  /** Returns whether this plan contains no executable connection behavior. */
  public boolean isEmpty() {
    return onConnect.isEmpty()
        && recurring.isEmpty()
        && idleMessage.isEmpty()
        && reconnectAfterIdle.isEmpty();
  }

  /** One named message action resolved for runtime execution. */
  public record MessageAction(String name, ConnectionMessageFactory factory) {

    public MessageAction {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("name must not be blank");
      }
      Objects.requireNonNull(factory, "factory");
    }
  }

  /** One message action attempted independently after each full positive delay. */
  public record RecurringAction(MessageAction message, Duration delay) {

    public RecurringAction {
      Objects.requireNonNull(message, "message");
      requirePositive(delay, "delay");
    }
  }

  /** One message action attempted after a positive period without inbound activity. */
  public record IdleMessageAction(MessageAction message, Duration threshold) {

    public IdleMessageAction {
      Objects.requireNonNull(message, "message");
      requirePositive(threshold, "threshold");
    }
  }

  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
