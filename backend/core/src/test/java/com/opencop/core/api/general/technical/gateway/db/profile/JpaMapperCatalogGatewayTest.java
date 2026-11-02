// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaMapperCatalogGatewayTest {

  private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID HIGH = UUID.fromString("00000000-0000-4000-8000-000000000002");

  private final EntityManager entityManager = mock(EntityManager.class);
  private final JpaMapperCatalogGateway gateway = new JpaMapperCatalogGateway(entityManager);

  /** Every creation takes its mappers in the same order, whatever order it selected them in. */
  @Test
  @SuppressWarnings("unchecked")
  void locksTheSelectedMappersInIdentifierOrderBeforeReadingThemApart() {
    var archivedQuery = (TypedQuery<UUID>) mock(TypedQuery.class);
    when(entityManager.createQuery(anyString(), eq(UUID.class))).thenReturn(archivedQuery);
    when(archivedQuery.setParameter(eq("ids"), any())).thenReturn(archivedQuery);
    when(archivedQuery.getResultList()).thenReturn(List.of(HIGH));

    var archived = gateway.lockAndFindArchived(List.of(HIGH, LOW));

    var order = inOrder(entityManager);
    order
        .verify(entityManager)
        .find(MapperIdentityEntity.class, LOW, LockModeType.PESSIMISTIC_READ);
    order
        .verify(entityManager)
        .find(MapperIdentityEntity.class, HIGH, LockModeType.PESSIMISTIC_READ);
    order.verify(entityManager).createQuery(anyString(), eq(UUID.class));
    assertThat(archived).containsExactly(HIGH);
  }

  @Test
  void locksNothingWhenNoMapperIsSelected() {
    assertThat(gateway.lockAndFindArchived(List.of())).isEmpty();
    verifyNoInteractions(entityManager);
  }
}
