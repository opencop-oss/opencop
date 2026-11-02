// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionLock;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.gateway.connection.extension.ExtensionSecretGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.technical.gateway.db.connection.TransactionalConnectionLock;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class HostedSecretEntryTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID INTEGRATION_ID =
      UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2");
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(INTEGRATION_ID, 1);
  private static final IntegrationConnectionSchemaReference OTHER_INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1);
  private static final String EXTENSION_ID = "data-sync";
  private static final Map<String, String> SECRETS = Map.of("mission.alpha.password", "secret");

  private final ExtensionSecretGateway gateway = mock(ExtensionSecretGateway.class);
  private final ConnectionMetadataGateway connections = mock(ConnectionMetadataGateway.class);
  private final ConnectionLock lock =
      new TransactionalConnectionLock(
          connections, new TransactionTemplate(mock(PlatformTransactionManager.class)));

  @Test
  void readsAndReplacesTheEntryOfItsOwnConnectionAndExtension() {
    givenConnection(null);
    when(gateway.read(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID)).thenReturn(SECRETS);
    var entry = writable();

    assertThat(entry.read()).isEqualTo(SECRETS);
    entry.replace(SECRETS);

    verify(gateway).replace(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID, SECRETS);
  }

  @Test
  void replacingWithNothingDeletesTheEntry() {
    givenConnection(null);
    var entry = writable();

    entry.replace(Map.of());

    verify(gateway).delete(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID);
    verify(gateway, never()).replace(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID, Map.of());
  }

  @Test
  void aSecretWithoutKeyOrValueIsAnExtensionBug() {
    var entry = writable();
    var withoutValue = new HashMap<String, String>();
    withoutValue.put("token", null);

    assertThatThrownBy(() -> entry.replace(Map.of(" ", "value")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> entry.replace(withoutValue))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(gateway);
  }

  /**
   * Deleting the connection waits on its row lock, so a write holding the lock lands before the
   * deletion and is deleted with the connection's other entries.
   */
  @Test
  void aWriteHoldsTheConnectionsLockWhileItWrites() {
    givenConnection(null);

    writable().replace(SECRETS);

    var order = inOrder(connections, gateway);
    order.verify(connections).lockById(SOURCE_ID);
    order.verify(gateway).replace(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID, SECRETS);
  }

  /**
   * An invocation still running once its connection is deleted writes nothing, so its entry is not
   * recreated after the deletion removed it.
   */
  @Test
  void aWriteAfterTheConnectionWasDeletedIsRefused() {
    givenConnection(Instant.parse("2026-10-13T00:00:00Z"));
    var entry = writable();

    assertThatThrownBy(() -> entry.replace(SECRETS)).isInstanceOf(GlobalOpenCopException.class);
    assertThatThrownBy(entry::delete).isInstanceOf(GlobalOpenCopException.class);
    verify(gateway, never()).replace(any(), any(), any(), any());
    verify(gateway, never()).delete(any(), any(), any());
  }

  /**
   * A write after the connection moved to another integration would land where the move's cleanup
   * already ran, and stay there, so it is refused.
   */
  @Test
  void aWriteAfterTheConnectionMovedToAnotherIntegrationIsRefused() {
    givenConnection(null, OTHER_INTEGRATION);
    var entry = writable();

    assertThatThrownBy(() -> entry.replace(SECRETS))
        .isInstanceOfSatisfying(
            GlobalOpenCopException.class,
            error ->
                assertThat(error.getCode())
                    .isEqualTo(ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE.code()));
    verify(gateway, never()).replace(any(), any(), any(), any());
  }

  @Test
  void aDeletedConnectionsEntryCanBeReadButNotWritten() {
    when(gateway.read(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID)).thenReturn(SECRETS);
    var entry = HostedSecretEntry.readOnly(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID, gateway);

    assertThat(entry.read()).isEqualTo(SECRETS);
    assertThatThrownBy(() -> entry.replace(SECRETS)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(entry::delete).isInstanceOf(IllegalStateException.class);
    verify(gateway, never()).delete(SOURCE_ID, INTEGRATION_ID, EXTENSION_ID);
  }

  private HostedSecretEntry writable() {
    return HostedSecretEntry.writable(SOURCE_ID, INTEGRATION, EXTENSION_ID, gateway, lock);
  }

  private void givenConnection(Instant archivedAt) {
    givenConnection(archivedAt, INTEGRATION);
  }

  private void givenConnection(
      Instant archivedAt, IntegrationConnectionSchemaReference integration) {
    when(connections.lockById(SOURCE_ID))
        .thenReturn(
            new ConnectionMetadata(
                SOURCE_ID,
                "connection",
                null,
                TransportKind.TCP,
                AccessMode.READ,
                true,
                archivedAt,
                true,
                integration));
  }
}
