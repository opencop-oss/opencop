// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.policy.connection.extension.ConnectionExtensionAccessPolicy;
import com.opencop.core.api.general.ports.gateway.authorization.ConnectionAuthorization;
import com.opencop.core.api.general.ports.usecase.connection.extension.ConnectionExtensionSummary;
import com.opencop.core.api.general.ports.usecase.connection.extension.ExtensionOperationSummary;
import com.opencop.core.api.general.ports.usecase.connection.extension.ListConnectionExtensions;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.extension.RegisteredExtension;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lists a connection's extensions with only the operations the caller may invoke, applying the same
 * rules invoking them does, so a client offers exactly what will be accepted. Invoking an operation
 * still checks everything again.
 */
public class ListConnectionExtensionsUsecase implements ListConnectionExtensions {

  private final ExtensionConnections connections;
  private final ConnectionAuthorization authorization;
  private final ConnectionExtensionRegistry extensions;

  public ListConnectionExtensionsUsecase(
      ExtensionConnections connections,
      ConnectionAuthorization authorization,
      ConnectionExtensionRegistry extensions) {
    this.connections = connections;
    this.authorization = authorization;
    this.extensions = extensions;
  }

  @Override
  public List<ConnectionExtensionSummary> list(UUID sourceId, UUID userId, boolean isGlobalAdmin) {
    var connection = connections.readable(sourceId, userId, isGlobalAdmin);
    var integration = connections.availableIntegration(connection);
    if (integration.isEmpty()) {
      return List.of();
    }
    var canManage = authorization.isDirectOwnerOrAdmin(sourceId, userId, isGlobalAdmin);
    return extensions.extensionsOf(integration.get()).stream()
        .map(extension -> summary(extension, canManage))
        .flatMap(Optional::stream)
        .toList();
  }

  /** An extension the caller can invoke nothing of is not offered at all. */
  private static Optional<ConnectionExtensionSummary> summary(
      RegisteredExtension extension, boolean canManage) {
    var invocable =
        extension.operations().stream()
            .filter(
                operation ->
                    ConnectionExtensionAccessPolicy.isInvocable(operation.access(), canManage))
            .map(ListConnectionExtensionsUsecase::summary)
            .toList();
    if (invocable.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new ConnectionExtensionSummary(
            extension.id(),
            extension.descriptor().label(),
            extension.descriptor().description(),
            invocable));
  }

  private static ExtensionOperationSummary summary(ExtensionOperation<?, ?, ?> operation) {
    return new ExtensionOperationSummary(
        operation.method(), operation.path().template(), operation.access());
  }
}
