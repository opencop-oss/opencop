// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import java.util.Optional;
import java.util.UUID;

/**
 * What the host gives an extension operation about the connection it is invoked on. The host has
 * already checked that the caller may invoke the operation; the handler never sees who the caller
 * is beyond their id.
 *
 * <p>The host confines every gateway here to this connection and the extension's own data. How the
 * extension uses what it reads, such as reaching its remote system, is the extension's own
 * business: it builds its own clients and sets their timeouts.
 *
 * @param <S> the effective settings type of the connection's schema
 */
public interface ConnectionExtensionContext<S> {

  UUID sourceId();

  /** The integration schema version the connection binds to. */
  IntegrationConnectionSchemaReference integration();

  /** The connection's effective settings: its overrides on the deployment defaults. */
  S settings();

  /**
   * The connection's stored setup, as the integration's own setup produced it, or empty for a
   * connection the host provisions itself rather than setting up through a transport.
   */
  Optional<ConnectionSpec> setup();

  /** The secret material the connection's setup references, and only that. */
  ConnectionSecrets secrets();

  /** The records this extension stores for the connection, and only those. */
  ConnectionRecordStore records();

  /** The internal id of the user invoking the operation, for the extension's own audit. */
  UUID actingUserId();
}
