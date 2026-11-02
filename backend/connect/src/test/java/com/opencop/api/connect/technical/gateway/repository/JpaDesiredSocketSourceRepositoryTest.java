// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.api.connect.component.support.AbstractSocketComponentIT;
import com.opencop.api.connect.component.support.ConnectComponentDatabaseSupport;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsOverrides;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class JpaDesiredSocketSourceRepositoryTest extends AbstractSocketComponentIT {

  private static final String CONNECT_INSTANCE = "connect-1";
  private static final UUID ARCHIVED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655449001");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void findSocketSourceById_reconstructsTheExactIntegrationReference() throws Exception {
    var integration = new IntegrationConnectionSchemaReference(UUID.randomUUID(), 4);
    var sourceId = UUID.randomUUID();
    ConnectComponentDatabaseSupport.seedSocketSource(
        sourceId,
        "integrated-source",
        fixture.tcpSpec(25099),
        true,
        CONNECT_INSTANCE,
        objectMapper);
    ConnectComponentDatabaseSupport.setSocketSourceIntegration(sourceId, integration);

    var desired =
        ConnectComponentDatabaseSupport.desiredSocketSourceRepository(objectMapper)
            .findSocketSourceById(sourceId)
            .orElseThrow();

    assertThat(desired.source().integration()).isEqualTo(integration);
  }

  @Test
  void sourcesToRunCarryTheirStoredIntegrationSettings() throws Exception {
    var integration = new IntegrationConnectionSchemaReference(UUID.randomUUID(), 1);
    var withSettings = seedIntegratedSource(integration, 25101);
    var without = seedIntegratedSource(integration, 25102);
    ConnectComponentDatabaseSupport.setSocketSourceSettings(
        withSettings,
        integration,
        "{\"presence\": {\"callsign\": \"Alpha\", \"latitude\": 45.5},"
            + " \"heartbeat\": {\"pingEnabled\": false}}");
    var repository = ConnectComponentDatabaseSupport.desiredSocketSourceRepository(objectMapper);
    var expected =
        Optional.of(
            new SettingsOverrides(
                Map.of(
                    "presence", Map.of("callsign", "Alpha", "latitude", 45.5),
                    "heartbeat", Map.of("pingEnabled", false))));

    assertThat(repository.findSocketSourceById(withSettings).orElseThrow().overrides())
        .isEqualTo(expected);
    assertThat(repository.findSocketSourceById(without).orElseThrow().overrides()).isEmpty();
    assertThat(repository.findEnabledSocketSourcesAssignedTo(CONNECT_INSTANCE))
        .filteredOn(desired -> desired.id().equals(withSettings))
        .singleElement()
        .satisfies(desired -> assertThat(desired.overrides()).isEqualTo(expected));
  }

  private UUID seedIntegratedSource(IntegrationConnectionSchemaReference integration, int port)
      throws Exception {
    var sourceId = UUID.randomUUID();
    ConnectComponentDatabaseSupport.seedSocketSource(
        sourceId,
        "source-" + sourceId,
        fixture.tcpSpec(port),
        true,
        CONNECT_INSTANCE,
        objectMapper);
    ConnectComponentDatabaseSupport.setSocketSourceIntegration(sourceId, integration);
    return sourceId;
  }

  @Test
  void findSocketSourceById_excludesArchivedRows() throws Exception {
    UUID sourceId = UUID.randomUUID();
    ConnectComponentDatabaseSupport.seedSocketSource(
        sourceId, "archived-source", fixture.tcpSpec(25100), true, CONNECT_INSTANCE, objectMapper);
    archiveSource(sourceId);

    var source =
        ConnectComponentDatabaseSupport.desiredSocketSourceRepository(objectMapper)
            .findSocketSourceById(sourceId);

    assertTrue(source.isEmpty());
  }

  @Test
  void findEnabledSocketSourcesAssignedTo_returnsOnlyActiveRows() throws Exception {
    UUID activeId = UUID.randomUUID();
    UUID archivedId = UUID.randomUUID();
    ConnectComponentDatabaseSupport.seedSocketSource(
        activeId, "active-source", fixture.tcpSpec(25101), true, CONNECT_INSTANCE, objectMapper);
    ConnectComponentDatabaseSupport.seedSocketSource(
        archivedId,
        "archived-source",
        fixture.tcpSpec(25102),
        true,
        CONNECT_INSTANCE,
        objectMapper);
    archiveSource(archivedId);

    var sources =
        ConnectComponentDatabaseSupport.desiredSocketSourceRepository(objectMapper)
            .findEnabledSocketSourcesAssignedTo(CONNECT_INSTANCE);

    assertEquals(1, sources.size());
    assertEquals(activeId, sources.getFirst().id());
  }

  @Test
  void findSocketSourcesByIds_excludesArchivedRowsFromMap() throws Exception {
    UUID activeId = UUID.randomUUID();
    UUID archivedId = UUID.randomUUID();
    ConnectComponentDatabaseSupport.seedSocketSource(
        activeId, "active-source", fixture.tcpSpec(25103), true, CONNECT_INSTANCE, objectMapper);
    ConnectComponentDatabaseSupport.seedSocketSource(
        archivedId,
        "archived-source",
        fixture.tcpSpec(25104),
        true,
        CONNECT_INSTANCE,
        objectMapper);
    archiveSource(archivedId);

    var sources =
        ConnectComponentDatabaseSupport.desiredSocketSourceRepository(objectMapper)
            .findSocketSourcesByIds(Set.of(activeId, archivedId));

    assertEquals(1, sources.size());
    assertTrue(sources.containsKey(activeId));
    assertFalse(sources.containsKey(archivedId));
  }

  private void archiveSource(UUID sourceId) throws Exception {
    ConnectComponentDatabaseSupport.archiveSocketSource(sourceId, ARCHIVED_BY);
  }

  @Test
  void legacyRtspConnectionSpecUsesRuntimeDefaultsWhenConnectReadsDesiredState() {
    var jpa = mock(DesiredSocketSourceJpaRepository.class);
    var sourceId = UUID.randomUUID();
    when(jpa.findByIdAndProviderInAndArchivedAtIsNull(eq(sourceId), anyCollection()))
        .thenReturn(Optional.of(rtspEntity(sourceId)));
    var repository = new JpaDesiredSocketSourceRepository(jpa, new ObjectMapper());

    var source = repository.findSocketSourceById(sourceId).orElseThrow().source();

    assertThat(source.provider()).isEqualTo(TransportKind.RTSP);
    assertThat(source.spec().transport()).isInstanceOf(RtspConfig.class);
    var rtsp = (RtspConfig) source.spec().transport();
    assertThat(rtsp.getHost()).isEqualTo("camera.example.test");
    assertThat(rtsp.getPort()).isEqualTo(554);
  }

  private static DesiredSocketSourceEntity rtspEntity(UUID sourceId) {
    var entity = new DesiredSocketSourceEntity();
    ReflectionTestUtils.setField(entity, "id", sourceId);
    ReflectionTestUtils.setField(entity, "name", "rtsp-source");
    ReflectionTestUtils.setField(entity, "tenant", "default");
    ReflectionTestUtils.setField(entity, "provider", TransportKind.RTSP);
    ReflectionTestUtils.setField(entity, "enabled", true);
    ReflectionTestUtils.setField(entity, "access", "R");
    ReflectionTestUtils.setField(entity, "assignedInstance", "connect-a");
    ReflectionTestUtils.setField(entity, "createdAt", Instant.parse("2026-05-29T12:00:00Z"));
    ReflectionTestUtils.setField(entity, "updatedAt", Instant.parse("2026-05-29T12:01:00Z"));
    ReflectionTestUtils.setField(
        entity,
        "connectionSpec",
        """
        {
          "transport": {
            "type": "rtsp",
            "url": "rtsp://camera.example.test/live",
            "coordinates": null,
            "connectTimeoutMs": 1234,
            "readTimeoutMs": 5678,
            "preferTcp": false,
            "host": "legacy.example.invalid",
            "port": 9999
          }
        }
        """);
    return entity;
  }
}
