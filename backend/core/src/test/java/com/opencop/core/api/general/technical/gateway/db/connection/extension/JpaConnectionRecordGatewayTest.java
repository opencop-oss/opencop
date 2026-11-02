// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JpaConnectionRecordGatewayTest {

  private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-0000000000b1");
  private static final UUID INTEGRATION_ID =
      UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91");
  private static final RecordScope SCOPE = new RecordScope(SOURCE_ID, INTEGRATION_ID, "sync");
  private static final String COLLECTION = "subscriptions";
  private static final String KEY = "alpha";
  private static final ConnectionRecordEntityId ID =
      new ConnectionRecordEntityId(SOURCE_ID, "sync", COLLECTION, KEY);

  private final ConnectionRecordJpaRepository repository =
      mock(ConnectionRecordJpaRepository.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final JpaConnectionRecordGateway gateway =
      new JpaConnectionRecordGateway(repository, entityManager, new ObjectMapper());

  /**
   * The row's key does not name its integration, so a row another integration's extension wrote is
   * out of the scope even though its key matches.
   */
  @Test
  void aRowWrittenForAnotherIntegrationIsOutOfScope() {
    when(repository.findById(ID)).thenReturn(Optional.of(entity(OTHER_INTEGRATION_ID)));

    assertThat(gateway.find(SCOPE, COLLECTION, KEY)).isEmpty();
    gateway.delete(SCOPE, COLLECTION, KEY);
    verify(repository, never()).delete(any());
  }

  /**
   * open-in-view keeps one EntityManager for the request: a row left managed would answer later
   * lookups with old state, and a change to the record handed out would be flushed.
   */
  @Test
  void everyRowHandedOutOrSetAsideIsDetached() {
    var found = entity(INTEGRATION_ID);
    var listed = entity(INTEGRATION_ID);
    var foreign = entity(OTHER_INTEGRATION_ID);
    when(repository.findById(ID)).thenReturn(Optional.of(found), Optional.of(foreign));
    when(repository.findBySourceIdAndIntegrationIdAndExtensionIdAndCollectionOrderByRecordKey(
            SOURCE_ID, INTEGRATION_ID, "sync", COLLECTION))
        .thenReturn(List.of(listed));

    gateway.find(SCOPE, COLLECTION, KEY);
    gateway.list(SCOPE, COLLECTION);
    gateway.find(SCOPE, COLLECTION, KEY);

    verify(entityManager).detach(found);
    verify(entityManager).detach(listed);
    verify(entityManager).detach(foreign);
  }

  @Test
  void aSavedRowIsDetachedOnceItsTimestampsAreRead() {
    var existing = entity(INTEGRATION_ID);
    when(repository.findById(ID)).thenReturn(Optional.of(existing));

    gateway.save(SCOPE, COLLECTION, KEY, Map.of(), 2, USER_ID);

    var order = inOrder(entityManager);
    order.verify(entityManager).flush();
    order.verify(entityManager).refresh(existing);
    order.verify(entityManager).detach(existing);
  }

  @Test
  void aRowOfTheScopesIntegrationIsDeleted() {
    var entity = entity(INTEGRATION_ID);
    when(repository.findById(ID)).thenReturn(Optional.of(entity));

    gateway.delete(SCOPE, COLLECTION, KEY);

    verify(repository).delete(entity);
  }

  /** A row as read back: written, so it carries a document. */
  private static ConnectionRecordEntity entity(UUID integrationId) {
    var entity = new ConnectionRecordEntity(ID, integrationId, USER_ID);
    entity.write(Map.of(), 1, USER_ID);
    return entity;
  }
}
