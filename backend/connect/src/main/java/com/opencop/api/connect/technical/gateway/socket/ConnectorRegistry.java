// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import com.opencop.api.connect.business.connector.ConnectorCreationContext;
import com.opencop.api.connect.ports.gateway.socket.connector.ConnectorProvider;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.api.connect.technical.gateway.socket.factory.ConnectorFactory;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsResolution;
import com.opencop.core.sdk.business.registry.integration.connection.behavior.ConnectionBehaviorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands a connection's spec to the factory of its transport, then composes applicable SDK
 * connection behavior around the raw connector. Each factory handles its transport's security.
 * Behavior is compiled from the connection's effective settings: its stored overrides on the
 * integration's deployment defaults. A connector that starts with overrides that no longer resolve
 * runs on the defaults alone; a running one keeps its current plan.
 */
public final class ConnectorRegistry implements ConnectorProvider {

  private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

  private final ConnectorFactory tcp;
  private final ConnectorFactory udp;
  private final ConnectorFactory rtsp;
  private final ConnectionBehaviorRegistry behaviorRegistry;
  private final ConnectionSettingsRegistry settingsRegistry;
  private final ConnectionBehaviorRuntime behaviorRuntime;

  public ConnectorRegistry(
      ConnectorFactory tcp,
      ConnectorFactory udp,
      ConnectorFactory rtsp,
      ConnectionBehaviorRegistry behaviorRegistry,
      ConnectionSettingsRegistry settingsRegistry,
      ConnectionBehaviorRuntime behaviorRuntime) {
    this.tcp = Objects.requireNonNull(tcp, "tcp");
    this.udp = Objects.requireNonNull(udp, "udp");
    this.rtsp = Objects.requireNonNull(rtsp, "rtsp");
    this.behaviorRegistry = Objects.requireNonNull(behaviorRegistry, "behaviorRegistry");
    this.settingsRegistry = Objects.requireNonNull(settingsRegistry, "settingsRegistry");
    this.behaviorRuntime = Objects.requireNonNull(behaviorRuntime, "behaviorRuntime");
  }

  @Override
  public boolean canRun(ConnectorCreationContext context) {
    return context.integration() == null
        || behaviorRegistry.find(context.integration()).isPresent();
  }

  /**
   * Creates the raw connector of the spec's transport before resolving any integration behavior.
   */
  @Override
  public SocketConnector create(ConnectorCreationContext context) {
    var spec = context.spec();
    var factory =
        switch (spec.transport()) {
          case TcpConfig _ -> tcp;
          case UdpConfig _ -> udp;
          case RtspConfig _ -> rtsp;
        };
    var raw = factory.create(context.sourceId(), spec);
    if (context.integration() == null) {
      return raw;
    }
    var integration = integration(context);
    // Stored overrides that no longer resolve, after a deployment default changed for instance,
    // do not keep the connection down: it starts on the defaults alone.
    var effectiveSettings =
        resolve(integration, context)
            .orElseGet(
                () -> {
                  log.warn("Connection {} starts on the deployment defaults", context.sourceId());
                  return settingsRegistry.defaults(integration);
                });
    return behaviorRuntime.decorate(
        raw,
        context.sourceId(),
        behaviorRegistry.planFor(integration, effectiveSettings),
        effectiveSettings);
  }

  /**
   * Swaps the behavior plan of a running integration-backed connector when its effective settings
   * changed, keeping the raw socket open. Overrides that no longer resolve leave the current plan
   * in place.
   *
   * @return whether the plan was swapped
   */
  @Override
  public boolean applySettings(SocketConnector connector, ConnectorCreationContext context) {
    if (!(connector instanceof ConnectionBehaviorSocketConnector decorated)
        || context.integration() == null) {
      return false;
    }
    var integration = integration(context);
    var effectiveSettings = resolve(integration, context);
    if (effectiveSettings.isEmpty()
        || effectiveSettings.orElseThrow().equals(decorated.effectiveSettings())) {
      return false;
    }
    var changed = effectiveSettings.orElseThrow();
    return decorated.applyPlan(behaviorRegistry.planFor(integration, changed), changed);
  }

  private IntegrationConnectionSchema integration(ConnectorCreationContext context) {
    return behaviorRegistry
        .find(context.integration())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "UNKNOWN_INTEGRATION_SCHEMA: "
                        + context.integration()
                        + " for connection "
                        + context.sourceId()));
  }

  /** Resolves the stored overrides, logging and returning empty when they no longer resolve. */
  private Optional<Object> resolve(
      IntegrationConnectionSchema integration, ConnectorCreationContext context) {
    return switch (settingsRegistry.resolve(integration, context.overrides())) {
      case SettingsResolution.Resolved resolved -> Optional.of(resolved.effective());
      case SettingsResolution.Rejected rejected -> {
        log.warn(
            "Stored integration settings of connection {} do not resolve: {}",
            context.sourceId(),
            rejected.violations());
        yield Optional.empty();
      }
    };
  }
}
