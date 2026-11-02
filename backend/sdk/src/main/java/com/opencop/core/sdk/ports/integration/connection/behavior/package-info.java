// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Stable authoring interface for integration-owned connection behavior.
 *
 * <p>An integration publishes each cohesive behavior as an ordinary bean implementing {@link
 * com.opencop.core.sdk.ports.integration.connection.behavior.ConnectionBehaviorContribution} and
 * one or more trigger capabilities. The SDK collects those beans automatically; integrations do not
 * construct aggregates, call registration callbacks, or depend on Connect runtime classes.
 *
 * <p>Contribution beans and their message factories are immutable, stateless, deterministic for a
 * supplied context, and safe for concurrent use. Mutable connection state, scheduling, socket IO,
 * cancellation, and failure logging belong to the consuming runtime. Expected message construction,
 * serialization, and send failures are non-fatal to the connection; fatal JVM errors are not
 * converted to behavior failures.
 *
 * <p>Lower {@code order()} values run first and names break ties. Multiple on-connect and recurring
 * capabilities are supported. Each connection schema version may publish at most one receive-idle
 * message and one receive-idle reconnect capability. A class may implement multiple capabilities
 * when they represent the same cohesive behavior.
 *
 * <p>Each contribution reads the connection's effective settings, passed as {@code S} whenever a
 * plan is compiled. A schema without connection settings uses {@code NoConnectionSettings}. A
 * minimal contribution needs only its connection schema, settings type, name, and trigger-specific
 * values:
 *
 * <pre>{@code
 * final class PresenceBehavior implements OnConnectBehavior<PresenceSettings> {
 *   private final IntegrationConnectionSchema schema;
 *
 *   PresenceBehavior(IntegrationConnectionSchema schema) {
 *     this.schema = schema;
 *   }
 *
 *   public IntegrationConnectionSchema schema() { return schema; }
 *   public Class<PresenceSettings> settingsType() { return PresenceSettings.class; }
 *   public String name() { return "presence"; }
 *   public ConnectionMessageFactory messageFactory(PresenceSettings settings) {
 *     return new PresenceMessages(settings.callsign());
 *   }
 * }
 * }</pre>
 */
package com.opencop.core.sdk.ports.integration.connection.behavior;
