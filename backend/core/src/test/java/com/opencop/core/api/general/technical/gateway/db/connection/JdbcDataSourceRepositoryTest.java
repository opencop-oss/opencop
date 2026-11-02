// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.api.general.business.model.connection.setup.StoredConnectionSetup;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceConstraintViolationException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.integration.builtin.business.model.NativeConnectionSchema;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.RtspConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class JdbcDataSourceRepositoryTest {

  @Mock private NamedParameterJdbcTemplate jdbc;
  @Captor private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;
  @Captor private ArgumentCaptor<String> sqlCaptor;

  private JdbcDataSourceRepository repo;

  private static final Set<String> ALLOWED_SPEC_KEYS =
      Set.of("topicName", "username", "brokerProvider");

  private static final String UDP_SOURCE_NAME = "udp-source";
  private static final int UDP_LISTEN_PORT = 9100;

  private static final OffsetDateTime ARCHIVED_AT = OffsetDateTime.parse("2026-05-25T12:00:00Z");
  private static final OffsetDateTime LAST_EVENT_AT = OffsetDateTime.parse("2026-05-25T11:00:00Z");
  private static final UUID ARCHIVED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final UUID CREATED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
  private static final UUID UPDATED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440005");
  private static final UUID EGRESS_MAPPER_ID =
      UUID.fromString("00000000-0000-4000-8000-0000000000e1");
  private static final IntegrationConnectionSchemaReference INTEGRATION =
      new IntegrationConnectionSchemaReference(
          UUID.fromString("714e8096-8832-4486-ad47-5422f86f918a"), 1);

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = JsonMapper.builder().build();
    repo = new JdbcDataSourceRepository(jdbc, objectMapper);
  }

  @Test
  void persistKafka_connectionSpecDoesNotContainPassword() {
    var sourceId = UUID.randomUUID();
    var ds =
        new KafkaDataSource(
            sourceId,
            "test-source",
            "default",
            "opencop.byod.test." + sourceId,
            "byod-test-" + sourceId,
            null,
            null,
            AccessMode.READ,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "LOCAL",
            true);

    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(OffsetDateTime.class)))
        .thenReturn(OffsetDateTime.now());

    repo.persist(ds);

    verify(jdbc)
        .queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(OffsetDateTime.class));

    String connectionSpec = (String) paramsCaptor.getValue().getValue("connection_spec");
    ObjectMapper reader = JsonMapper.builder().build();
    Map<String, Object> specMap = reader.readValue(connectionSpec, Map.class);

    assertEquals(
        ALLOWED_SPEC_KEYS,
        specMap.keySet(),
        "connection_spec must contain only topicName, username, and brokerProvider");
    assertFalse(
        connectionSpec.toLowerCase().contains("password"),
        "connection_spec must not mention 'password'");
  }

  @Test
  void mapRow_socketDataSourceIncludesArchiveMetadata() throws Exception {
    var source = mapSocketRow(null);

    assertEquals(ARCHIVED_AT.toInstant(), source.archivedAt());
    assertEquals(ARCHIVED_BY, source.archivedBy());
    assertEquals(CREATED_BY, source.createdBy());
    assertEquals(UPDATED_BY, source.updatedBy());
    assertEquals(LAST_EVENT_AT.toInstant(), source.lastEventAt());
    assertEquals(MAPPER_ID, source.ingressMapperId());
    assertEquals(EGRESS_MAPPER_ID, source.egressMapperId());
    assertNull(source.integration());
  }

  @Test
  void mapRow_socketDataSourceReconstructsANonNullIntegrationReference() throws Exception {
    assertEquals(INTEGRATION, mapSocketRow(INTEGRATION).integration());
  }

  @Test
  void mapRow_kafkaDataSourceIncludesArchiveMetadataAndNoSecretFields() throws Exception {
    var source = mapKafkaRow(null);

    assertEquals(ARCHIVED_AT.toInstant(), source.archivedAt());
    assertEquals(ARCHIVED_BY, source.archivedBy());
    assertEquals(CREATED_BY, source.createdBy());
    assertEquals(UPDATED_BY, source.updatedBy());
    assertEquals(LAST_EVENT_AT.toInstant(), source.lastEventAt());
    assertNull(source.integration());

    var componentNames =
        Arrays.stream(KafkaDataSource.class.getRecordComponents()).map(rc -> rc.getName()).toList();
    assertFalse(componentNames.contains("password"));
    assertFalse(componentNames.contains("scram"));
    assertFalse(componentNames.contains("acl"));
    assertFalse(componentNames.contains("quota"));
    assertFalse(componentNames.contains("vaultPath"));
  }

  @Test
  void mapRow_kafkaDataSourceReconstructsANonNullIntegrationReference() throws Exception {
    assertEquals(INTEGRATION, mapKafkaRow(INTEGRATION).integration());
  }

  private static SocketDataSource mapSocketRow(IntegrationConnectionSchemaReference integration)
      throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getString("provider")).thenReturn("TCP");
    when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
    when(resultSet.getString("name")).thenReturn("socket-source");
    when(resultSet.getString("tenant")).thenReturn("default");
    when(resultSet.getObject("ingress_mapper_id", UUID.class)).thenReturn(MAPPER_ID);
    when(resultSet.getObject("egress_mapper_id", UUID.class)).thenReturn(EGRESS_MAPPER_ID);
    when(resultSet.getBoolean("enabled")).thenReturn(true);
    when(resultSet.getObject("created_at", OffsetDateTime.class))
        .thenReturn(ARCHIVED_AT.minusDays(1));
    when(resultSet.getObject("created_by", UUID.class)).thenReturn(CREATED_BY);
    when(resultSet.getObject("updated_at", OffsetDateTime.class))
        .thenReturn(ARCHIVED_AT.minusHours(1));
    when(resultSet.getObject("updated_by", UUID.class)).thenReturn(UPDATED_BY);
    when(resultSet.getObject("last_event_at", OffsetDateTime.class)).thenReturn(LAST_EVENT_AT);
    when(resultSet.getBoolean("default_visible")).thenReturn(true);
    when(resultSet.getObject("archived_at", OffsetDateTime.class)).thenReturn(ARCHIVED_AT);
    when(resultSet.getObject("archived_by", UUID.class)).thenReturn(ARCHIVED_BY);
    when(resultSet.getString("access")).thenReturn("R");
    when(resultSet.getString("connection_spec"))
        .thenReturn(
            "{\"transport\":{\"type\":\"tcp\",\"host\":\"127.0.0.1\",\"port\":9000,\"security\":{\"type\":\"none\"}}}");
    when(resultSet.getString("assigned_instance")).thenReturn("connect-1");
    stubIntegration(resultSet, integration);

    var rowMapper = new DataSourceRowMapper(JsonMapper.builder().build());
    return assertInstanceOf(SocketDataSource.class, rowMapper.mapRow(resultSet, 0));
  }

  private static KafkaDataSource mapKafkaRow(IntegrationConnectionSchemaReference integration)
      throws SQLException {
    ResultSet resultSet = mock(ResultSet.class);
    when(resultSet.getString("provider")).thenReturn("KAFKA");
    when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
    when(resultSet.getString("name")).thenReturn("kafka-source");
    when(resultSet.getString("tenant")).thenReturn("default");
    when(resultSet.getObject("ingress_mapper_id", UUID.class)).thenReturn(MAPPER_ID);
    when(resultSet.getBoolean("enabled")).thenReturn(false);
    when(resultSet.getObject("created_at", OffsetDateTime.class))
        .thenReturn(ARCHIVED_AT.minusDays(2));
    when(resultSet.getObject("created_by", UUID.class)).thenReturn(CREATED_BY);
    when(resultSet.getObject("updated_at", OffsetDateTime.class))
        .thenReturn(ARCHIVED_AT.minusDays(1));
    when(resultSet.getObject("updated_by", UUID.class)).thenReturn(UPDATED_BY);
    when(resultSet.getObject("last_event_at", OffsetDateTime.class)).thenReturn(LAST_EVENT_AT);
    when(resultSet.getBoolean("default_visible")).thenReturn(true);
    when(resultSet.getObject("archived_at", OffsetDateTime.class)).thenReturn(ARCHIVED_AT);
    when(resultSet.getObject("archived_by", UUID.class)).thenReturn(ARCHIVED_BY);
    when(resultSet.getString("access")).thenReturn("R");
    when(resultSet.getString("connection_spec"))
        .thenReturn(
            "{\"topicName\":\"topic-1\",\"username\":\"k-user\",\"brokerProvider\":\"LOCAL\",\"password\":\"never-map\"}");
    stubIntegration(resultSet, integration);

    var rowMapper = new DataSourceRowMapper(JsonMapper.builder().build());
    return assertInstanceOf(KafkaDataSource.class, rowMapper.mapRow(resultSet, 0));
  }

  private static void stubIntegration(
      ResultSet resultSet, IntegrationConnectionSchemaReference integration) throws SQLException {
    when(resultSet.getObject("integration_id", UUID.class))
        .thenReturn(integration == null ? null : integration.integrationId());
    if (integration != null) {
      when(resultSet.getObject("connection_schema_version", Integer.class))
          .thenReturn(integration.schemaVersion());
    }
  }

  @Test
  void findAll_whenFilterHasArchiveActive_addsActiveArchivePredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(null, Set.of(), RecordVisibility.ACTIVE, null, null, null);

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("archived_at IS NULL"));
    assertFalse(sqlCaptor.getValue().contains("AND archived_at IS NOT NULL"));
  }

  @Test
  void findOne_whenFilterHasArchiveAllAndId_doesNotAddArchivePredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var sourceId = UUID.randomUUID();
    var filter =
        new DataSourceReadFilter(sourceId, Set.of(), RecordVisibility.ALL, null, null, null);

    repo.findOne(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("id = :id"));
    assertFalse(sqlCaptor.getValue().contains("archived_at IS NULL"));
    assertFalse(sqlCaptor.getValue().contains("archived_at IS NOT NULL"));
  }

  @Test
  void findAll_whenFilterHasArchiveArchived_addsArchivedArchivePredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(null, Set.of(), RecordVisibility.ARCHIVED, null, null, null);

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("archived_at IS NOT NULL"));
  }

  @Test
  void findAll_whenFilterHasIdsAndBrokerProvider_appliesProviderPredicateAndBindsCurrentProvider() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(
            null,
            Set.of(UUID.randomUUID(), UUID.randomUUID()),
            RecordVisibility.ALL,
            "LOCAL",
            null,
            null);

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), mapCaptor.capture(), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("id IN (:ids)"));
    assertTrue(
        sqlCaptor.getValue().contains("connection_spec->>'brokerProvider' = :currentProvider"));
    assertEquals("LOCAL", mapCaptor.getValue().get("currentProvider"));
  }

  @Test
  void findById_whenArchivedRecordExists_usesRawIdentityLookupWithoutArchivePredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());

    repo.findById(UUID.randomUUID());

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("id = :id"));
    assertFalse(sqlCaptor.getValue().contains("archived_at IS NULL"));
    assertFalse(sqlCaptor.getValue().contains("archived_at IS NOT NULL"));
  }

  @Test
  void findAll_whenFilterHasEnabled_appliesEnabledPredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(null, Set.of(), RecordVisibility.ACTIVE, null, true, null);

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("enabled = :enabled"));
  }

  @Test
  void findAll_whenFilterHasAssignedInstance_appliesAssignedInstancePredicate() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(null, Set.of(), RecordVisibility.ACTIVE, null, null, "connect-1");

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertTrue(sqlCaptor.getValue().contains("assigned_instance = :assignedInstance"));
  }

  @Test
  void findAll_whenFilterHasEmptyIds_doesNotAddIdsPredicateAndKeepsOtherPredicates() {
    when(jdbc.query(any(String.class), any(Map.class), any(DataSourceRowMapper.class)))
        .thenReturn(List.of());
    var filter =
        new DataSourceReadFilter(null, Set.of(), RecordVisibility.ACTIVE, "LOCAL", null, null);

    repo.findAll(filter);

    verify(jdbc).query(sqlCaptor.capture(), any(Map.class), any(DataSourceRowMapper.class));
    assertFalse(sqlCaptor.getValue().contains("id IN (:ids)"));
    assertTrue(sqlCaptor.getValue().contains("archived_at IS NULL"));
    assertTrue(
        sqlCaptor.getValue().contains("connection_spec->>'brokerProvider' = :currentProvider"));
  }

  @Test
  void archive_isTransactional() throws Exception {
    var archiveMethod = JdbcDataSourceRepository.class.getMethod("archive", UUID.class, UUID.class);

    assertTrue(archiveMethod.isAnnotationPresent(Transactional.class));
  }

  @Test
  void archive_updatesActiveRowBeforeDeletingRuntimeRows() {
    var sourceId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    when(jdbc.update(any(String.class), any(Map.class))).thenReturn(1, 0, 0);

    repo.archive(sourceId, archivedBy);

    verify(jdbc, times(3)).update(sqlCaptor.capture(), any(Map.class));
    var sqlStatements = sqlCaptor.getAllValues();
    assertTrue(sqlStatements.get(0).contains("UPDATE profiles.data_source"));
    assertTrue(sqlStatements.get(0).contains("archived_at IS NULL"));
    assertTrue(sqlStatements.get(1).contains("DELETE FROM profiles.source_runtime_observation"));
    assertTrue(sqlStatements.get(2).contains("DELETE FROM profiles.socket_source_restart"));
  }

  @Test
  void archive_whenNoActiveRowUpdated_throwsNotFoundAndSkipsRuntimeDeletes() {
    when(jdbc.update(any(String.class), any(Map.class))).thenReturn(0);

    assertThrows(
        GlobalOpenCopException.class, () -> repo.archive(UUID.randomUUID(), UUID.randomUUID()));

    verify(jdbc).update(sqlCaptor.capture(), any(Map.class));
    assertTrue(sqlCaptor.getValue().contains("UPDATE profiles.data_source"));
  }

  /**
   * Callers provision external resources before persisting, so they have to recognise a rejected
   * write to release them. Every unattributable violation therefore leaves as a port type carrying
   * the original as its cause — never as the framework exception, which a business caller cannot
   * catch without importing Spring.
   */
  @Test
  void persist_whenAnUnknownConstraintIsViolated_raisesAConstraintViolationCarryingTheCause() {
    var violation = integrityViolation(postgresCause("uk_data_source_something_else"));
    givenPersistFailsWith(violation);

    var thrown =
        assertThrows(DataSourceConstraintViolationException.class, () -> repo.persist(udpSource()));

    assertEquals(UDP_SOURCE_NAME, thrown.name());
    assertSame(violation, thrown.getCause());
  }

  @Test
  void persist_whenTheCauseIsNotPostgres_raisesAConstraintViolationCarryingTheCause() {
    var violation = integrityViolation(new SQLException("some other driver"));
    givenPersistFailsWith(violation);

    var thrown =
        assertThrows(DataSourceConstraintViolationException.class, () -> repo.persist(udpSource()));

    assertSame(violation, thrown.getCause());
  }

  @Test
  void
      persist_whenPostgresReportsNoServerErrorMessage_raisesAConstraintViolationCarryingTheCause() {
    var violation = integrityViolation(mock(PSQLException.class));
    givenPersistFailsWith(violation);

    var thrown =
        assertThrows(DataSourceConstraintViolationException.class, () -> repo.persist(udpSource()));

    assertSame(violation, thrown.getCause());
  }

  @Test
  void updateSetup_whenAnotherSourceHoldsTheListenPort_raisesAListenPortConflict() {
    var violation = integrityViolation(postgresCause("uk_data_source_listen_port_active"));
    when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenThrow(violation);

    var thrown =
        assertThrows(
            DataSourceListenPortConflictException.class,
            () -> repo.updateSetup(UUID.randomUUID(), udpSetup(), UUID.randomUUID()));

    assertEquals(UDP_LISTEN_PORT, thrown.listenPort());
  }

  @Test
  void updateSetup_whenAnUnknownConstraintIsViolated_raisesAConstraintViolationCarryingTheCause() {
    var violation = integrityViolation(postgresCause("uk_data_source_something_else"));
    when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenThrow(violation);
    var sourceId = UUID.randomUUID();

    var thrown =
        assertThrows(
            DataSourceConstraintViolationException.class,
            () -> repo.updateSetup(sourceId, udpSetup(), UUID.randomUUID()));

    assertEquals(sourceId.toString(), thrown.name());
    assertSame(violation, thrown.getCause());
  }

  private static StoredConnectionSetup udpSetup() {
    return new StoredConnectionSetup(
        new ReadAccess(UUID.randomUUID()),
        NativeConnectionSchema.V1_REFERENCE,
        new ConnectionSpec(new UdpConfig("127.0.0.1", 9000, UDP_LISTEN_PORT)),
        new SetupPrefill(UUID.randomUUID(), Map.of()));
  }

  private void givenPersistFailsWith(DataIntegrityViolationException violation) {
    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(OffsetDateTime.class)))
        .thenThrow(violation);
  }

  private static DataIntegrityViolationException integrityViolation(Throwable cause) {
    return new DataIntegrityViolationException("insert rejected", cause);
  }

  private static PSQLException postgresCause(String constraint) {
    var serverError = mock(ServerErrorMessage.class);
    when(serverError.getConstraint()).thenReturn(constraint);
    var psql = mock(PSQLException.class);
    when(psql.getServerErrorMessage()).thenReturn(serverError);
    return psql;
  }

  private static SocketDataSource udpSource() {
    var spec = new ConnectionSpec(new UdpConfig("127.0.0.1", 9000, null));
    return SocketDataSource.fromSpec(
        UUID.randomUUID(),
        UDP_SOURCE_NAME,
        null,
        null,
        "default",
        spec,
        null,
        AccessMode.READ,
        true,
        null);
  }

  @Test
  void persistWritesTheIngressAndEgressMappersOfAConnectionThatReadsAndWrites() {
    var ingressMapperId = UUID.randomUUID();
    var egressMapperId = UUID.randomUUID();
    var spec =
        new ConnectionSpec(
            new TcpConfig("tak.example.test", 8089, null, null, null, null, new NoSecurity()));
    var ds =
        SocketDataSource.fromSpec(
            UUID.randomUUID(),
            "tak-source",
            ingressMapperId,
            egressMapperId,
            "default",
            spec,
            null,
            AccessMode.READ_WRITE,
            true,
            null);
    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(OffsetDateTime.class)))
        .thenReturn(OffsetDateTime.now());

    repo.persist(ds);

    verify(jdbc)
        .queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(OffsetDateTime.class));
    assertEquals(ingressMapperId, paramsCaptor.getValue().getValue("ingressMapperId"));
    assertEquals(egressMapperId, paramsCaptor.getValue().getValue("egressMapperId"));
    assertTrue(sqlCaptor.getValue().contains("egress_mapper_id"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void persistRtspConnectionSpecOmitsRuntimeDefaultsAndDerivedEndpoint() {
    var sourceId = UUID.randomUUID();
    var spec = new ConnectionSpec(new RtspConfig("rtsp://camera.example.test:8554/live", null));
    var ds =
        SocketDataSource.fromSpec(
            sourceId,
            "rtsp-source",
            null,
            null,
            "default",
            spec,
            null,
            AccessMode.READ,
            true,
            null);

    when(jdbc.queryForObject(
            any(String.class), any(MapSqlParameterSource.class), eq(OffsetDateTime.class)))
        .thenReturn(OffsetDateTime.now());

    repo.persist(ds);

    verify(jdbc)
        .queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(OffsetDateTime.class));

    var params = paramsCaptor.getValue();
    assertEquals(TransportKind.RTSP.name(), params.getValue("provider"));
    assertFalse(params.hasValue("host"));
    assertFalse(params.hasValue("port"));

    String connectionSpec = (String) params.getValue("connection_spec");
    ObjectMapper reader = JsonMapper.builder().build();
    Map<String, Object> specMap = reader.readValue(connectionSpec, Map.class);
    Map<String, Object> transport = (Map<String, Object>) specMap.get("transport");

    assertEquals("rtsp", transport.get("type"));
    assertEquals("rtsp://camera.example.test:8554/live", transport.get("url"));
    assertFalse(transport.containsKey("connectTimeoutMs"));
    assertFalse(transport.containsKey("readTimeoutMs"));
    assertFalse(transport.containsKey("preferTcp"));
    assertFalse(transport.containsKey("host"));
    assertFalse(transport.containsKey("port"));
  }
}
