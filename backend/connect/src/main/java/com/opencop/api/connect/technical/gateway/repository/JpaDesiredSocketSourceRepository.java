// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.repository;

import com.opencop.api.connect.business.connector.DesiredSocketSource;
import com.opencop.api.connect.ports.gateway.repository.DesiredSocketSourceRepository;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@Transactional(readOnly = true)
public class JpaDesiredSocketSourceRepository implements DesiredSocketSourceRepository {

  private static final List<TransportKind> SOCKET_PROVIDERS =
      List.of(TransportKind.TCP, TransportKind.UDP, TransportKind.RTSP);

  private final DesiredSocketSourceJpaRepository jpa;
  private final ObjectMapper objectMapper;

  public JpaDesiredSocketSourceRepository(
      DesiredSocketSourceJpaRepository jpa, ObjectMapper objectMapper) {
    this.jpa = jpa;
    this.objectMapper = objectMapper;
  }

  @Override
  public String diagnosticLabel() {
    return "Postgres desired-state repository";
  }

  @Override
  public Optional<DesiredSocketSource> findSocketSourceById(UUID sourceId) {
    return jpa.findByIdAndProviderInAndArchivedAtIsNull(sourceId, SOCKET_PROVIDERS)
        .map(this::toDesiredSource);
  }

  @Override
  public List<DesiredSocketSource> findEnabledSocketSourcesAssignedTo(String instanceId) {
    return jpa
        .findByEnabledTrueAndAssignedInstanceAndProviderInAndArchivedAtIsNullOrderByCreatedAtDesc(
            instanceId, SOCKET_PROVIDERS)
        .stream()
        .map(this::toDesiredSource)
        .toList();
  }

  @Override
  public Map<UUID, SocketDataSource> findSocketSourcesByIds(Set<UUID> sourceIds) {
    if (sourceIds == null || sourceIds.isEmpty()) {
      return Collections.emptyMap();
    }

    return jpa.findByIdInAndProviderInAndArchivedAtIsNull(sourceIds, SOCKET_PROVIDERS).stream()
        .map(this::toSocketSource)
        .collect(Collectors.toMap(SocketDataSource::id, Function.identity()));
  }

  /** The read joins the settings table, so a connection without a row overrides nothing. */
  private DesiredSocketSource toDesiredSource(DesiredSocketSourceEntity entity) {
    return new DesiredSocketSource(
        toSocketSource(entity),
        Optional.ofNullable(entity.getIntegrationSettings()).map(SettingsOverrides::new));
  }

  private SocketDataSource toSocketSource(DesiredSocketSourceEntity entity) {
    return new SocketDataSource(
        entity.getId(),
        entity.getName(),
        entity.getTenant(),
        deserializeSpec(entity.getConnectionSpec()),
        entity.getIngressMapperId(),
        entity.getEgressMapperId(),
        integrationReference(entity),
        AccessMode.fromString(entity.getAccess()),
        entity.isEnabled(),
        entity.getAssignedInstance(),
        null, // archivedAt - not projected
        null, // archivedBy - not projected
        entity.getCreatedAt(),
        null, // createdBy - not projected
        entity.getUpdatedAt(),
        null, // updatedBy - not projected
        null, // lastEventAt - not projected
        entity.isDefaultVisible());
  }

  private static IntegrationConnectionSchemaReference integrationReference(
      DesiredSocketSourceEntity entity) {
    if (entity.getIntegrationId() == null) {
      return null;
    }
    return new IntegrationConnectionSchemaReference(
        entity.getIntegrationId(), entity.getConnectionSchemaVersion());
  }

  private ConnectionSpec deserializeSpec(String json) {
    try {
      return objectMapper.readValue(json, ConnectionSpec.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialize socket connection spec JSON", e);
    }
  }
}
