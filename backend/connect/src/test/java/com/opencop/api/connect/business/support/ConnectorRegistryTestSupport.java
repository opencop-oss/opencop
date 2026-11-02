// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.support;

import com.opencop.api.connect.technical.gateway.socket.ConnectionBehaviorRuntime;
import com.opencop.api.connect.technical.gateway.socket.ConnectorRegistry;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.registry.integration.connection.behavior.ConnectionBehaviorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Test-only composition for scenarios that intentionally exercise raw connectors without behavior.
 */
public final class ConnectorRegistryTestSupport {

  private static final ScheduledThreadPoolExecutor SCHEDULER =
      new ScheduledThreadPoolExecutor(
          1, Thread.ofPlatform().daemon().name("raw-registry-test-timer-", 0).factory());
  private static final java.util.concurrent.ExecutorService EXECUTOR =
      Executors.newVirtualThreadPerTaskExecutor();
  private static final ConnectionBehaviorRuntime RUNTIME =
      new ConnectionBehaviorRuntime(SCHEDULER, EXECUTOR, Clock.systemUTC());
  private static final ConnectionBehaviorRegistry EMPTY_BEHAVIORS =
      new ConnectionBehaviorRegistry(List.of(), List.of());
  private static final ConnectionSettingsRegistry EMPTY_SETTINGS =
      new ConnectionSettingsRegistry(List.of(), List.of());

  private ConnectorRegistryTestSupport() {}

  /** A registry without integration behavior whose one factory builds every transport. */
  public static ConnectorRegistry rawOnly(ConnectorFactory every) {
    return rawOnly(every, every, every);
  }

  /** A registry without integration behavior, with each transport's own factory. */
  public static ConnectorRegistry rawOnly(
      ConnectorFactory tcp, ConnectorFactory udp, ConnectorFactory rtsp) {
    return new ConnectorRegistry(tcp, udp, rtsp, EMPTY_BEHAVIORS, EMPTY_SETTINGS, RUNTIME);
  }

  /**
   * A registry whose one factory builds every transport, registering the given schema versions
   * without any behavior of their own.
   */
  public static ConnectorRegistry registering(
      ConnectorFactory every, IntegrationConnectionSchema... schemas) {
    return registering(every, every, every, schemas);
  }

  /**
   * A registry with each transport's own factory, registering the given schema versions without any
   * behavior of their own.
   */
  public static ConnectorRegistry registering(
      ConnectorFactory tcp,
      ConnectorFactory udp,
      ConnectorFactory rtsp,
      IntegrationConnectionSchema... schemas) {
    return new ConnectorRegistry(
        tcp,
        udp,
        rtsp,
        new ConnectionBehaviorRegistry(List.of(schemas), List.of()),
        new ConnectionSettingsRegistry(List.of(schemas), List.of()),
        RUNTIME);
  }
}
