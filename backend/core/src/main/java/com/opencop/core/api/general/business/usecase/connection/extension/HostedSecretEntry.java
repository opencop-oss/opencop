// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionSecretEntry;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One extension's secret entry on one connection. A deleted connection's entry stays readable for
 * its delete hooks, which run before the entry itself is deleted, but is never written again.
 *
 * <p>A write holds the connection's row lock, which deleting the connection or moving it to another
 * integration waits on, and is refused once either happened: a write racing them either lands
 * before, and is deleted with the connection's other entries, or is refused.
 */
final class HostedSecretEntry implements ExtensionSecretEntry {

  private static final String OPERATION = "write extension secrets";
  private static final String READ_ONLY =
      "A deleted connection's secret entry can no longer be written";

  private final UUID sourceId;
  private final UUID integrationId;
  private final String extensionId;
  private final ExtensionSecretGateway gateway;
  private final Consumer<Runnable> writes;

  private HostedSecretEntry(
      UUID sourceId,
      UUID integrationId,
      String extensionId,
      ExtensionSecretGateway gateway,
      Consumer<Runnable> writes) {
    this.sourceId = sourceId;
    this.integrationId = integrationId;
    this.extensionId = extensionId;
    this.gateway = gateway;
    this.writes = writes;
  }

  static HostedSecretEntry writable(
      UUID sourceId,
      IntegrationConnectionSchemaReference integration,
      String extensionId,
      ExtensionSecretGateway gateway,
      ConnectionLock lock) {
    return new HostedSecretEntry(
        sourceId,
        integration.integrationId(),
        extensionId,
        gateway,
        write ->
            lock.holding(
                sourceId,
                connection -> {
                  ResourceLifecyclePolicy.assertDataSourceActive(
                      connection.archivedAt(), OPERATION);
                  ConnectionIntegrationReferencePolicy.assertStillBound(
                      connection.integration(), integration);
                  write.run();
                  return null;
                }));
  }

  static HostedSecretEntry readOnly(
      UUID sourceId, UUID integrationId, String extensionId, ExtensionSecretGateway gateway) {
    return new HostedSecretEntry(
        sourceId,
        integrationId,
        extensionId,
        gateway,
        _ -> {
          throw new IllegalStateException(READ_ONLY);
        });
  }

  @Override
  public Map<String, String> read() {
    return gateway.read(sourceId, integrationId, extensionId);
  }

  @Override
  public void replace(Map<String, String> secrets) {
    Objects.requireNonNull(secrets, "secrets");
    secrets.forEach(HostedSecretEntry::requireEntry);
    var copy = Map.copyOf(secrets);
    writes.accept(
        () -> {
          if (copy.isEmpty()) {
            gateway.delete(sourceId, integrationId, extensionId);
          } else {
            gateway.replace(sourceId, integrationId, extensionId, copy);
          }
        });
  }

  @Override
  public void delete() {
    writes.accept(() -> gateway.delete(sourceId, integrationId, extensionId));
  }

  private static void requireEntry(String key, String value) {
    if (key == null || key.isBlank() || value == null) {
      throw new IllegalArgumentException("Each secret needs a non-blank key and a value");
    }
  }
}
