// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionIntegrationSelectionTest {

  private static final NativeConnectionSchema NATIVE =
      new NativeConnectionSchema(new NativeIntegration());
  private static final IntegrationConnectionSchemaReference REGISTERED =
      NativeConnectionSchema.V1_REFERENCE;
  private static final IntegrationConnectionSchemaReference UNREGISTERED =
      new IntegrationConnectionSchemaReference(NativeIntegration.NATIVE_ID, 99);
  private static final IntegrationIdentity ACTIVE =
      new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", true);
  private static final IntegrationIdentity INACTIVE =
      new IntegrationIdentity(NativeIntegration.NATIVE_ID, "Native", "Native", false);

  private final IntegrationCatalogGateway catalog = mock(IntegrationCatalogGateway.class);
  private final ConnectionIntegrationSelection selection =
      new ConnectionIntegrationSelection(catalog, List.of(NATIVE));

  @Test
  void acceptsAnActiveCataloguedSchemaThisRuntimeRegisters() {
    when(catalog.findSchemaOwner(key(REGISTERED))).thenReturn(Optional.of(ACTIVE));

    assertThatCode(() -> selection.requireSelectable(REGISTERED)).doesNotThrowAnyException();
  }

  @Test
  void reportsAnUnknownSchemaAsNotFoundEvenWhenUnregistered() {
    when(catalog.findSchemaOwner(key(UNREGISTERED))).thenReturn(Optional.empty());

    assertRejected(
        () -> selection.requireSelectable(UNREGISTERED),
        ConnectionExceptionCodes.INTEGRATION_NOT_FOUND);
  }

  @Test
  void reportsAnInactiveIntegrationAsInactiveEvenWhenUnregistered() {
    when(catalog.findSchemaOwner(key(UNREGISTERED))).thenReturn(Optional.of(INACTIVE));

    assertRejected(
        () -> selection.requireSelectable(UNREGISTERED),
        ConnectionExceptionCodes.INTEGRATION_INACTIVE);
  }

  @Test
  void rejectsAnActiveCataloguedSchemaThisRuntimeDoesNotRegister() {
    when(catalog.findSchemaOwner(key(UNREGISTERED))).thenReturn(Optional.of(ACTIVE));

    assertRejected(
        () -> selection.requireSelectable(UNREGISTERED),
        ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE);
  }

  @Test
  void locksTheOwningIntegrationWhenCheckingForAnUpdate() {
    when(catalog.findSchemaOwnerForUpdate(key(UNREGISTERED))).thenReturn(Optional.of(ACTIVE));

    assertRejected(
        () -> selection.requireSelectableForUpdate(UNREGISTERED),
        ConnectionExceptionCodes.INTEGRATION_UNAVAILABLE);
    verify(catalog).findSchemaOwnerForUpdate(key(UNREGISTERED));
    verify(catalog, never()).findSchemaOwner(key(UNREGISTERED));
  }

  private static IntegrationConnectionSchemaKey key(
      IntegrationConnectionSchemaReference reference) {
    return new IntegrationConnectionSchemaKey(reference.integrationId(), reference.schemaVersion());
  }

  private static void assertRejected(Runnable check, ConnectionExceptionCodes code) {
    assertThatThrownBy(check::run)
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(code.code()));
  }
}
