// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection.extension;

import com.opencop.core.api.general.business.model.connection.extension.RecordScope;
import com.opencop.core.api.general.ports.gateway.connection.extension.ConnectionRecordGateway;
import com.opencop.core.sdk.business.model.integration.connection.extension.ConnectionRecord;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JpaConnectionRecordGateway implements ConnectionRecordGateway {

  private final ConnectionRecordJpaRepository repository;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;

  JpaConnectionRecordGateway(
      ConnectionRecordJpaRepository repository, EntityManager entityManager, ObjectMapper mapper) {
    this.repository = repository;
    this.entityManager = entityManager;
    this.mapper = mapper;
  }

  @Override
  public Optional<ConnectionRecord> find(RecordScope scope, String collection, String key) {
    return entity(scope, collection, key).map(this::export);
  }

  @Override
  public List<ConnectionRecord> list(RecordScope scope, String collection) {
    return repository
        .findBySourceIdAndIntegrationIdAndExtensionIdAndCollectionOrderByRecordKey(
            scope.sourceId(), scope.integrationId(), scope.extensionId(), collection)
        .stream()
        .map(this::export)
        .toList();
  }

  @Override
  public boolean existsFor(UUID sourceId) {
    return repository.existsBySourceId(sourceId);
  }

  @Override
  public long count(RecordScope scope) {
    return repository.countBySourceIdAndIntegrationIdAndExtensionId(
        scope.sourceId(), scope.integrationId(), scope.extensionId());
  }

  @Override
  public long storedSize(Map<String, Object> document) {
    return mapper.writeValueAsBytes(document).length;
  }

  @Override
  public ConnectionRecord save(
      RecordScope scope,
      String collection,
      String key,
      Map<String, Object> document,
      long revision,
      UUID userId) {
    var existing = entity(scope, collection, key);
    var entity =
        existing.orElseGet(
            () ->
                new ConnectionRecordEntity(
                    id(scope, collection, key), scope.integrationId(), userId));
    entity.write(document, revision, userId);
    if (existing.isEmpty()) {
      // Its key is assigned, so a merge would first look the new row up again.
      entityManager.persist(entity);
    }
    entityManager.flush();
    // The timestamps are the database's: re-read them once the row is written.
    entityManager.refresh(entity);
    return export(entity);
  }

  @Override
  public void delete(RecordScope scope, String collection, String key) {
    entity(scope, collection, key)
        .ifPresent(
            entity -> {
              repository.delete(entity);
              repository.flush();
            });
  }

  /**
   * The key names the connection but not its integration, so a row found is kept only when it was
   * written for the scope's integration, as listing and counting require too.
   */
  private Optional<ConnectionRecordEntity> entity(
      RecordScope scope, String collection, String key) {
    var found = repository.findById(id(scope, collection, key));
    if (found.isPresent() && !found.get().belongsTo(scope.integrationId())) {
      entityManager.detach(found.get());
      return Optional.empty();
    }
    return found;
  }

  /**
   * open-in-view keeps one EntityManager for the whole request, across the store's transactions. A
   * row left managed would answer the request's later lookups with the state it was loaded with,
   * and a change made to the record handed out would be written by the next flush.
   */
  private ConnectionRecord export(ConnectionRecordEntity entity) {
    entityManager.detach(entity);
    return entity.toRecord();
  }

  private static ConnectionRecordEntityId id(RecordScope scope, String collection, String key) {
    return new ConnectionRecordEntityId(scope.sourceId(), scope.extensionId(), collection, key);
  }
}
