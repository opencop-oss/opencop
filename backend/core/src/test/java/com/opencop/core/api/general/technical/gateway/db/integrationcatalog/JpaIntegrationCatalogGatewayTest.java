// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class JpaIntegrationCatalogGatewayTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20");

  private final IntegrationIdentityJpaRepository integrations =
      mock(IntegrationIdentityJpaRepository.class);
  private final IntegrationConnectionSchemaJpaRepository schemas =
      mock(IntegrationConnectionSchemaJpaRepository.class);

  private final JpaIntegrationCatalogGateway gateway =
      new JpaIntegrationCatalogGateway(
          integrations, schemas, mock(EntityManager.class), mock(PlatformTransactionManager.class));

  /**
   * The read order is the whole guarantee the snapshot rests on: each statement sees its own
   * snapshot, so reading the versions first is what makes a version's integration certain to be
   * visible to the later read. Swapping the two calls passes every other test in the module, so
   * this pins the order itself.
   */
  @Test
  void readsTheSchemaVersionsBeforeTheIntegrationsThatOwnThem() {
    when(schemas.findAll()).thenReturn(List.of(schemaEntity(1)));
    when(integrations.findAll()).thenReturn(List.of(identityEntity()));

    gateway.findAll();

    var order = inOrder(schemas, integrations);
    order.verify(schemas).findAll();
    order.verify(integrations).findAll();
  }

  @Test
  void mapsBothSidesOfTheSnapshotOntoBusinessModels() {
    when(schemas.findAll()).thenReturn(List.of(schemaEntity(1), schemaEntity(2)));
    when(integrations.findAll()).thenReturn(List.of(identityEntity()));

    var snapshot = gateway.findAll();

    assertThat(snapshot.schemas())
        .containsExactly(
            new IntegrationConnectionSchemaKey(INTEGRATION_ID, 1),
            new IntegrationConnectionSchemaKey(INTEGRATION_ID, 2));
    assertThat(snapshot.integrations())
        .containsExactly(
            new IntegrationIdentity(INTEGRATION_ID, "Streaming feed", "Example integration", true));
  }

  private static IntegrationConnectionSchemaEntity schemaEntity(int schemaVersion) {
    return new IntegrationConnectionSchemaEntity(
        new IntegrationConnectionSchemaKey(INTEGRATION_ID, schemaVersion));
  }

  private static IntegrationIdentityEntity identityEntity() {
    return new IntegrationIdentityEntity(
        new IntegrationIdentity(INTEGRATION_ID, "Streaming feed", "Example integration", true));
  }
}
