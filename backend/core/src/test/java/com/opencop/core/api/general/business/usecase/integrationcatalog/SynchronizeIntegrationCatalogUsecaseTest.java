// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.factory.IntegrationCatalogRegistrationFactory;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogConflictException;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogInsertConflictException;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SynchronizeIntegrationCatalogUsecaseTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("e5df702b-a3db-42cb-8497-48cb00571d65");
  private static final IntegrationConnectionSchemaKey SCHEMA_V1 =
      new IntegrationConnectionSchemaKey(INTEGRATION_ID, 1);
  private static final IntegrationIdentity PUBLISHED =
      new IntegrationIdentity(INTEGRATION_ID, "Alpha", "First", true);
  private static final IntegrationCatalogInsertConflictException CONCURRENT_INSERT =
      IntegrationCatalogInsertConflictException.forIntegration(
          INTEGRATION_ID, new IllegalStateException("race"));

  private static final IntegrationCatalogRegistrationFactory REGISTRATION_FACTORY =
      new IntegrationCatalogRegistrationFactory();

  private final IntegrationCatalogGateway gateway = mock(IntegrationCatalogGateway.class);

  @Test
  void aRegistrationPublishingASchemaVersionTwiceFailsBeforeTouchingTheCatalog() {
    var usecase = usecase(integration(1, "Alpha", "First"), integration(1, "Beta", "Second"));

    assertThatThrownBy(usecase::synchronize)
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining("Duplicate registered connection schema");
    verifyNoInteractions(gateway);
  }

  @Test
  void aNewIntegrationIsInsertedActiveBeforeItsSchemaVersion() {
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.empty());

    usecase(integration(1, "Alpha", "First")).synchronize();

    var order = inOrder(gateway);
    order.verify(gateway).insertIntegration(PUBLISHED);
    order.verify(gateway).insertSchema(SCHEMA_V1);
  }

  @Test
  void anIntegrationOfferingNoConnectionSchemaIsStillRecorded() {
    var nativeIntegration = new TestIntegration(INTEGRATION_ID, "Native", "Built-in mappers");
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.empty());

    new SynchronizeIntegrationCatalogUsecase(
            List.of(nativeIntegration), List.of(), gateway, REGISTRATION_FACTORY)
        .synchronize();

    verify(gateway)
        .insertIntegration(
            new IntegrationIdentity(INTEGRATION_ID, "Native", "Built-in mappers", true));
    verify(gateway, never()).insertSchema(any());
  }

  @Test
  void aSchemaOfAnIntegrationNoPluginPublishesFailsBeforeTouchingTheCatalog() {
    var orphan = integration(1, "Alpha", "First");

    assertThatThrownBy(
            new SynchronizeIntegrationCatalogUsecase(
                    List.of(), List.of(orphan), gateway, REGISTRATION_FACTORY)
                ::synchronize)
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining("belongs to an integration no plugin publishes");
    verifyNoInteractions(gateway);
  }

  @Test
  void aKnownIntegrationWithChangedMetadataIsRefreshedAndKeepsItsActivation() {
    when(gateway.findIntegration(INTEGRATION_ID))
        .thenReturn(Optional.of(new IntegrationIdentity(INTEGRATION_ID, "Old", "Old", false)));
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(true);

    usecase(integration(1, "Alpha", "First")).synchronize();

    verify(gateway)
        .updateDisplayMetadata(new IntegrationIdentity(INTEGRATION_ID, "Alpha", "First", false));
    verify(gateway, never()).insertIntegration(any());
    verify(gateway, never()).insertSchema(any());
  }

  @Test
  void aCatalogMatchingEverythingPublishedIsNotWritten() {
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.of(PUBLISHED));
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(true);

    usecase(integration(1, "Alpha", "First")).synchronize();

    verify(gateway, never()).insertIntegration(any());
    verify(gateway, never()).insertSchema(any());
    verify(gateway, never()).updateDisplayMetadata(any());
  }

  @Test
  void aNewSchemaVersionOfAKnownIntegrationLeavesItsActivationAlone() {
    var deactivated = new IntegrationIdentity(INTEGRATION_ID, "Alpha", "First", false);
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.of(deactivated));
    var schemaV2 = new IntegrationConnectionSchemaKey(INTEGRATION_ID, 2);
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(true);

    usecase(integration(1, "Alpha", "First"), integration(2, "Alpha", "First")).synchronize();

    verify(gateway).insertSchema(schemaV2);
    verify(gateway, never()).updateActivation(any(), anyBoolean());
    verify(gateway, never()).updateDisplayMetadata(any());
  }

  @Test
  void anIntegrationInsertedConcurrentlyIsReloadedAndRefreshed() {
    when(gateway.findIntegration(INTEGRATION_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(new IntegrationIdentity(INTEGRATION_ID, "Other", "Winner", true)));
    doThrow(CONCURRENT_INSERT).when(gateway).insertIntegration(PUBLISHED);
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(true);

    usecase(integration(1, "Alpha", "First")).synchronize();

    verify(gateway).updateDisplayMetadata(PUBLISHED);
  }

  @Test
  void anIntegrationInsertConflictWithNoIntegrationLeftIsAConflict() {
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.empty());
    doThrow(CONCURRENT_INSERT).when(gateway).insertIntegration(PUBLISHED);

    assertThatThrownBy(usecase(integration(1, "Alpha", "First"))::synchronize)
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining(INTEGRATION_ID.toString())
        .hasCause(CONCURRENT_INSERT);
  }

  @Test
  void aSchemaVersionInsertedConcurrentlyIsAccepted() {
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.of(PUBLISHED));
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(false).thenReturn(true);
    doThrow(CONCURRENT_INSERT).when(gateway).insertSchema(SCHEMA_V1);

    usecase(integration(1, "Alpha", "First")).synchronize();

    verify(gateway, never()).updateDisplayMetadata(any());
  }

  @Test
  void aSchemaInsertConflictWithNoSchemaLeftIsAConflict() {
    when(gateway.findIntegration(INTEGRATION_ID)).thenReturn(Optional.of(PUBLISHED));
    when(gateway.hasSchema(SCHEMA_V1)).thenReturn(false);
    doThrow(CONCURRENT_INSERT).when(gateway).insertSchema(SCHEMA_V1);

    assertThatThrownBy(usecase(integration(1, "Alpha", "First"))::synchronize)
        .isInstanceOf(IntegrationCatalogConflictException.class)
        .hasMessageContaining(INTEGRATION_ID + " version 1")
        .hasCause(CONCURRENT_INSERT);
  }

  private SynchronizeIntegrationCatalogUsecase usecase(IntegrationConnectionSchema... published) {
    return synchronizer(List.of(published), gateway);
  }

  /** Publishes each schema's integration once, as a plugin publishes its integration bean. */
  private static SynchronizeIntegrationCatalogUsecase synchronizer(
      List<? extends IntegrationConnectionSchema> schemas, IntegrationCatalogGateway gateway) {
    return new SynchronizeIntegrationCatalogUsecase(
        integrationsOf(schemas), List.copyOf(schemas), gateway, REGISTRATION_FACTORY);
  }

  private static List<Integration> integrationsOf(
      List<? extends IntegrationConnectionSchema> schemas) {
    var byId = new LinkedHashMap<UUID, Integration>();
    schemas.forEach(schema -> byId.put(schema.integration().id(), schema.integration()));
    return List.copyOf(byId.values());
  }

  private static IntegrationConnectionSchema integration(
      int schemaVersion, String name, String description) {
    return new TestConnectionSchema(INTEGRATION_ID, schemaVersion, name, description);
  }
}
