// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration.connection.behavior.capability;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.ports.integration.connection.behavior.capability.ReceiveIdleReconnectBehavior;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Configurable immutable receive-idle reconnect contribution for plugin and runtime tests. */
public final class StubReceiveIdleReconnectBehavior
    implements ReceiveIdleReconnectBehavior<NoConnectionSettings> {

  private final IntegrationConnectionSchema schema;
  private final String name;
  private final int order;
  private final BooleanSupplier enabled;
  private final Supplier<Duration> reconnectAfterIdle;

  public StubReceiveIdleReconnectBehavior(
      IntegrationConnectionSchema schema, String name, Duration reconnectAfterIdle) {
    this(schema, name, 0, () -> true, () -> reconnectAfterIdle);
  }

  public StubReceiveIdleReconnectBehavior(
      IntegrationConnectionSchema schema,
      String name,
      int order,
      BooleanSupplier enabled,
      Supplier<Duration> reconnectAfterIdle) {
    this.schema = Objects.requireNonNull(schema, "schema");
    this.name = name;
    this.order = order;
    this.enabled = Objects.requireNonNull(enabled, "enabled");
    this.reconnectAfterIdle = Objects.requireNonNull(reconnectAfterIdle, "reconnectAfterIdle");
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
  public Duration reconnectAfterIdle(NoConnectionSettings settings) {
    return reconnectAfterIdle.get();
  }
}
