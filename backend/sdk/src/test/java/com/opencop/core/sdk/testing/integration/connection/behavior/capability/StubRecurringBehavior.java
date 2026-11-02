// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior.capability;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.RecurringBehavior;
import com.opencop.core.sdk.ports.integration.connection.behavior.message.ConnectionMessageFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Configurable immutable recurring contribution for plugin and runtime tests. */
public final class StubRecurringBehavior implements RecurringBehavior<NoConnectionSettings> {

  private final IntegrationConnectionSchema schema;
  private final String name;
  private final int order;
  private final BooleanSupplier enabled;
  private final ConnectionMessageFactory messageFactory;
  private final Supplier<Duration> delay;

  public StubRecurringBehavior(
      IntegrationConnectionSchema schema,
      String name,
      ConnectionMessageFactory messageFactory,
      Duration delay) {
    this(schema, name, 0, () -> true, messageFactory, () -> delay);
  }

  public StubRecurringBehavior(
      IntegrationConnectionSchema schema,
      String name,
      int order,
      BooleanSupplier enabled,
      ConnectionMessageFactory messageFactory,
      Supplier<Duration> delay) {
    this.schema = Objects.requireNonNull(schema, "schema");
    this.name = name;
    this.order = order;
    this.enabled = Objects.requireNonNull(enabled, "enabled");
    this.messageFactory = messageFactory;
    this.delay = Objects.requireNonNull(delay, "delay");
  }

  @Override
  public IntegrationConnectionSchema schema() {
    return schema;
  }

  @Override
  public Class<NoConnectionSettings> settingsType() {
    return NoConnectionSettings.class;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public int order() {
    return order;
  }

  @Override
  public boolean isEnabled(NoConnectionSettings settings) {
    return enabled.getAsBoolean();
  }

  @Override
  public ConnectionMessageFactory messageFactory(NoConnectionSettings settings) {
    return messageFactory;
  }

  @Override
  public Duration delay(NoConnectionSettings settings) {
    return delay.get();
  }
}
