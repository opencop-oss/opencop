// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.SQLException;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JpaConnectionMetadataGatewayTest {

  private static final UUID ACTOR_ID = UUID.fromString("550e8400-e29b-41d4-a716-4466554430a1");

  @Test
  void lockById_whenSourceDoesNotExist_translatesToNotFound() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var gateway = new JpaConnectionMetadataGateway(entityManager);
    when(entityManager.find(
            PatchableDataSourceEntity.class, sourceId, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(null);

    var exception = assertThrows(GlobalOpenCopException.class, () -> gateway.lockById(sourceId));

    assertEquals("Data source not found: " + sourceId, exception.getMessage());
    assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
  }

  /**
   * open-in-view keeps one EntityManager for the request, so a row an earlier transaction loaded is
   * returned by the locking find with its old fields.
   */
  @Test
  void lockById_rereadsTheLockedRowBeforeReadingIt() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var entity = mock(PatchableDataSourceEntity.class);
    var gateway = new JpaConnectionMetadataGateway(entityManager);
    when(entityManager.find(
            PatchableDataSourceEntity.class, sourceId, LockModeType.PESSIMISTIC_WRITE))
        .thenReturn(entity);

    gateway.lockById(sourceId);

    var order = inOrder(entityManager, entity);
    order
        .verify(entityManager)
        .find(PatchableDataSourceEntity.class, sourceId, LockModeType.PESSIMISTIC_WRITE);
    order.verify(entityManager).refresh(entity);
    order.verify(entity).toMetadata();
  }

  @Test
  void apply_whenFlushViolatesIntegrity_translatesToDuplicateConnectionName() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var entity = mock(PatchableDataSourceEntity.class);
    var patch = new ValidatedConnectionMetadataPatch("Bravo", null, null, null);
    var gateway = new JpaConnectionMetadataGateway(entityManager);
    when(entityManager.find(PatchableDataSourceEntity.class, sourceId)).thenReturn(entity);
    doThrow(
            new ConstraintViolationException(
                "duplicate name",
                new SQLException("duplicate name"),
                "update profiles.data_source",
                "uk_data_source_name_active"))
        .when(entityManager)
        .flush();

    var exception =
        assertThrows(
            ConnectionManagementException.class, () -> gateway.apply(sourceId, patch, ACTOR_ID));

    assertEquals("CORE-CONNECTION-1003", exception.getCode());
    assertEquals("A data source named 'Bravo' already exists", exception.getMessage());
    assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    verify(entity).apply(patch, ACTOR_ID);
  }

  @Test
  void apply_whenDifferentConstraintIsViolated_preservesTechnicalFailure() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var entity = mock(PatchableDataSourceEntity.class);
    var patch = new ValidatedConnectionMetadataPatch("Bravo", null, null, null);
    var gateway = new JpaConnectionMetadataGateway(entityManager);
    var failure =
        new ConstraintViolationException(
            "archive actor mismatch",
            new SQLException("archive actor mismatch"),
            "update profiles.data_source",
            "ck_data_source_archive_actor_consistency");
    when(entityManager.find(PatchableDataSourceEntity.class, sourceId)).thenReturn(entity);
    doThrow(failure).when(entityManager).flush();

    var thrown =
        assertThrows(
            ConstraintViolationException.class, () -> gateway.apply(sourceId, patch, ACTOR_ID));

    assertSame(failure, thrown);
  }

  @Test
  void applyAccess_writesTheAccessThroughTheLockedEntityAndFlushesIt() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var entity = mock(PatchableDataSourceEntity.class);
    var gateway = new JpaConnectionMetadataGateway(entityManager);
    var access = new ReadWriteAccess(UUID.randomUUID(), UUID.randomUUID());
    when(entityManager.find(PatchableDataSourceEntity.class, sourceId)).thenReturn(entity);

    gateway.applyAccess(sourceId, access, ACTOR_ID);

    var order = inOrder(entity, entityManager);
    order.verify(entity).applyAccess(access, ACTOR_ID);
    order.verify(entityManager).flush();
  }

  @Test
  void applyAccess_whenTheLockedSourceVanished_fails() {
    var sourceId = UUID.randomUUID();
    var entityManager = mock(EntityManager.class);
    var gateway = new JpaConnectionMetadataGateway(entityManager);

    assertThrows(
        IllegalStateException.class,
        () -> gateway.applyAccess(sourceId, new ReadAccess(UUID.randomUUID()), ACTOR_ID));
  }
}
