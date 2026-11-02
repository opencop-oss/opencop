// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.ports.gateway.connection.ConnectionMetadataGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
public class JpaConnectionMetadataGateway implements ConnectionMetadataGateway {

  private static final String ACTIVE_NAME_CONSTRAINT = "uk_data_source_name_active";

  private final EntityManager entityManager;

  public JpaConnectionMetadataGateway(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public ConnectionMetadata lockById(UUID sourceId) {
    var entity =
        entityManager.find(
            PatchableDataSourceEntity.class, sourceId, LockModeType.PESSIMISTIC_WRITE);
    if (entity == null) {
      throw new GlobalOpenCopException("Data source not found: " + sourceId, HttpStatus.NOT_FOUND);
    }
    // open-in-view keeps one EntityManager for the whole request, so a row an earlier transaction
    // of the request loaded comes back with the fields it was loaded with: re-read them under the
    // lock.
    entityManager.refresh(entity);
    return entity.toMetadata();
  }

  @Override
  public List<ConnectionMetadata> findNonArchivedByIntegrationId(UUID integrationId) {
    return entityManager
        .createQuery(
            """
            SELECT source
            FROM PatchableDataSource source
            WHERE source.integrationId = :integrationId
              AND source.archivedAt IS NULL
            """,
            PatchableDataSourceEntity.class)
        .setParameter("integrationId", integrationId)
        .getResultList()
        .stream()
        .map(PatchableDataSourceEntity::toMetadata)
        .toList();
  }

  @Override
  public void apply(UUID sourceId, ValidatedConnectionMetadataPatch patch, UUID updatedBy) {
    var entity = entityManager.find(PatchableDataSourceEntity.class, sourceId);
    if (entity == null) {
      throw new IllegalStateException("Locked data source disappeared: " + sourceId);
    }
    try {
      entity.apply(patch, updatedBy);
      entityManager.flush();
    } catch (ConstraintViolationException exception) {
      if (!ACTIVE_NAME_CONSTRAINT.equals(exception.getConstraintName())) {
        throw exception;
      }
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.CONNECTION_NAME_ALREADY_EXISTS, patch.name());
    }
  }

  @Override
  public void applyAccess(UUID sourceId, ConnectionAccess access, UUID updatedBy) {
    var entity = entityManager.find(PatchableDataSourceEntity.class, sourceId);
    if (entity == null) {
      throw new IllegalStateException("Locked data source disappeared: " + sourceId);
    }
    entity.applyAccess(access, updatedBy);
    // Written now, so a constraint the access breaks fails here, before the settings are stored.
    entityManager.flush();
  }
}
