// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import java.util.List;

/**
 * Host-owned capability contract an integration implements to add operations to the connections of
 * one schema version, which the host serves under {@code
 * /v1/connections/{sourceId}/extensions/{id}}.
 *
 * <p>The host owns routing, authorization, request reading and response writing; the integration
 * only declares its operations and serves them. Implementations are immutable singletons, safe for
 * concurrent use.
 *
 * @param <S> the effective settings type of the schema, the type its settings capability resolves
 *     to
 */
public interface ConnectionExtensionCapability<S> {

  /** Returns the connection schema version whose connections carry this extension. */
  IntegrationConnectionSchema schema();

  /** Returns the effective settings type the operations read. */
  Class<S> settingsType();

  /**
   * Returns the extension's identifier, the path segment it is served under: lowercase kebab-case,
   * unique among the extensions of its schema version.
   */
  String id();

  ExtensionDescriptor descriptor();

  /** Returns the operations, each a distinct method and path. */
  List<ExtensionOperation<S, ?, ?>> operations();

  /**
   * Releases what the extension holds outside OpenCOP for a connection once it is deleted, such as
   * remote subscriptions. Called after the deletion is final, so it can neither prevent nor undo
   * it, and best effort: within a time limit, with any failure only logged.
   *
   * <p>The context still holds the connection's settings and the secret material its setup
   * referenced, read before the deletion removed it. Its records can be read but no longer written.
   *
   * @throws ConnectionExtensionException when the release fails; the deletion stands regardless
   */
  default void afterConnectionDeleted(ConnectionExtensionContext<S> context)
      throws ConnectionExtensionException {
    // Most extensions hold nothing outside OpenCOP.
  }
}
