// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.settings;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;

/**
 * Host-owned capability contract an integration implements to declare the settings a connection of
 * one schema version may override, and to turn a complete settings document into its typed
 * effective settings.
 *
 * <p>The host owns parsing, constraint checks and the overlay of overrides on defaults; the
 * integration only applies the rules that span several fields. Implementations are immutable
 * singletons, safe for concurrent use. The host hands the value {@link #effective} returns,
 * unchanged, to the schema's behavior contributions, so {@code S} must equal the settings type they
 * declare.
 *
 * <p>{@code S} must have value equality. Connect compares a running connection's effective settings
 * with freshly resolved ones on every reconciliation pass, and swaps the connection's behavior
 * plan, announcing its presence again, whenever they are not {@link Object#equals equal}. A type
 * with identity equality would be swapped on every pass. A record of value types satisfies this.
 *
 * @param <S> the immutable effective settings type of the schema, with value equality
 */
public interface ConnectionSettingsCapability<S> {

  /** Returns the connection schema version these settings belong to. */
  IntegrationConnectionSchema schema();

  /** Returns the effective settings type. */
  Class<S> settingsType();

  /**
   * Returns the declared sections and fields, with the current deployment defaults. Purely
   * declarative: resolvable without I/O.
   */
  ConnectionSettingsDescriptor descriptor();

  /**
   * Builds the effective settings from a complete document, in which every declared field is
   * present and already satisfies its declared constraint.
   *
   * @throws ConnectionSettingsException when a rule spanning several fields is broken
   */
  S effective(SettingsDocument complete) throws ConnectionSettingsException;
}
