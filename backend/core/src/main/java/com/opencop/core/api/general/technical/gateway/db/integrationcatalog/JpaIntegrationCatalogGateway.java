// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogSnapshot;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationConnectionSchemaKey;
import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationIdentity;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogInsertConflictException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JpaIntegrationCatalogGateway implements IntegrationCatalogGateway {

  private final IntegrationIdentityJpaRepository integrations;
  private final IntegrationConnectionSchemaJpaRepository schemas;
  private final EntityManager entityManager;
  // Reads and inserts see and publish committed rows on their own, so a synchronizer racing
  // another one can recover from an insert conflict without losing its own transaction.
  private final TransactionTemplate independentTransaction;

  public JpaIntegrationCatalogGateway(
      IntegrationIdentityJpaRepository integrations,
      IntegrationConnectionSchemaJpaRepository schemas,
      EntityManager entityManager,
      PlatformTransactionManager transactionManager) {
    this.integrations = integrations;
    this.schemas = schemas;
    this.entityManager = entityManager;
    independentTransaction = new TransactionTemplate(transactionManager);
    independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Reads the schema versions before the integrations on purpose. Each statement sees its own
   * snapshot under READ COMMITTED, so the later read observes at least what the earlier one did: a
   * version read first always finds its integration, because a version is only ever inserted after
   * the integration it belongs to and neither is deleted. Reading in the other order can return a
   * version whose integration is missing.
   */
  @Override
  public IntegrationCatalogSnapshot findAll() {
    return independentTransaction.execute(
        _ -> {
          var schemaKeys =
              schemas.findAll().stream().map(IntegrationConnectionSchemaEntity::toKey).toList();
          var identities =
              integrations.findAll().stream().map(IntegrationIdentityEntity::toModel).toList();
          return new IntegrationCatalogSnapshot(identities, schemaKeys);
        });
  }

  @Override
  public Optional<IntegrationIdentity> findIntegration(UUID integrationId) {
    return independentTransaction.execute(
        _ -> integrations.findById(integrationId).map(IntegrationIdentityEntity::toModel));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<IntegrationIdentity> findIntegrationForUpdate(UUID integrationId) {
    return integrations.findForUpdate(integrationId).map(this::refreshAndMap);
  }

  @Override
  public boolean hasSchema(IntegrationConnectionSchemaKey schema) {
    return Boolean.TRUE.equals(
        independentTransaction.execute(
            _ -> schemas.existsById(IntegrationConnectionSchemaId.of(schema))));
  }

  @Override
  public Optional<IntegrationIdentity> findSchemaOwner(IntegrationConnectionSchemaKey schema) {
    return independentTransaction.execute(
        _ ->
            integrations
                .findSchemaOwner(schema.integrationId(), schema.schemaVersion())
                .map(IntegrationIdentityEntity::toModel));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<IntegrationIdentity> findSchemaOwnerForUpdate(
      IntegrationConnectionSchemaKey schema) {
    return integrations
        .findSchemaOwnerForUpdate(schema.integrationId(), schema.schemaVersion())
        .map(this::refreshAndMap);
  }

  @Override
  public void insertIntegration(IntegrationIdentity integration) {
    try {
      independentTransaction.executeWithoutResult(
          _ -> integrations.saveAndFlush(new IntegrationIdentityEntity(integration)));
    } catch (DataIntegrityViolationException conflict) {
      throw IntegrationCatalogInsertConflictException.forIntegration(integration.id(), conflict);
    }
  }

  @Override
  public void insertSchema(IntegrationConnectionSchemaKey schema) {
    try {
      independentTransaction.executeWithoutResult(
          _ -> schemas.saveAndFlush(new IntegrationConnectionSchemaEntity(schema)));
    } catch (DataIntegrityViolationException conflict) {
      throw IntegrationCatalogInsertConflictException.forSchema(schema, conflict);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void updateDisplayMetadata(IntegrationIdentity integration) {
    requireIntegration(integration.id(), "display metadata refresh")
        .updateDisplayMetadata(integration.name(), integration.description());
    integrations.flush();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void updateActivation(UUID integrationId, boolean active) {
    requireIntegration(integrationId, "activation update").updateActivation(active);
    integrations.flush();
  }

  private IntegrationIdentityEntity requireIntegration(UUID integrationId, String operation) {
    return integrations
        .findById(integrationId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Integration disappeared before " + operation + ": " + integrationId));
  }

  private IntegrationIdentity refreshAndMap(IntegrationIdentityEntity entity) {
    // A pessimistic lock can return an entity loaded earlier in this persistence context; refresh
    // it so the caller decides on the row as committed.
    entityManager.refresh(entity);
    return entity.toModel();
  }
}
