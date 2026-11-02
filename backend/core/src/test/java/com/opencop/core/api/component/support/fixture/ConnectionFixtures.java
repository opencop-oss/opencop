// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.KafkaDataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ConnectionFixtures implements TestFixture {

  /**
   * The listen port {@link #persistUdpSource} occupies, so callers can contest it without restating
   * the literal.
   */
  public static final int UDP_LISTEN_PORT = 8087;

  private final JdbcTemplate jdbcTemplate;
  private final DataSourceRepository dataSourceRepository;
  private final ObjectMapper objectMapper;

  public ConnectionFixtures(
      JdbcTemplate jdbcTemplate,
      DataSourceRepository dataSourceRepository,
      ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.dataSourceRepository = dataSourceRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM profiles.data_source");
    jdbcTemplate.update("DELETE FROM profiles.data_mapper");
    jdbcTemplate.update("DELETE FROM profiles.mapper");
  }

  /** The ingress mapper the persisted sources decode with. */
  public void seedDefaultMapper(UUID mapperId) {
    seedMapper(mapperId, "default-mapper", "INGRESS", false);
  }

  /** A mapper the catalog lists under the qualifier, in its first revision. */
  public void seedMapper(UUID mapperId, String name, String qualifier, boolean archived) {
    jdbcTemplate.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, CAST(? AS profiles.mapper_qualifier)) ON CONFLICT (id) DO NOTHING",
        mapperId,
        qualifier);
    jdbcTemplate.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper, archived) VALUES (?, ?, ?, CAST(? AS jsonb), ?)",
        mapperId,
        1,
        name,
        "{\"enabled\":true}",
        archived);
  }

  public void persistTcpSource(
      UUID id, String name, UUID mapperId, boolean enabled, String assignedInstance) {
    persistTcpSource(id, name, mapperId, AccessMode.READ, enabled, assignedInstance);
  }

  public void persistTcpSource(
      UUID id,
      String name,
      UUID mapperId,
      AccessMode access,
      boolean enabled,
      String assignedInstance) {
    var spec =
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity()));

    // A write-only source decodes nothing, so it holds no ingress mapper.
    var ingressMapperId = access.canRead() ? mapperId : null;
    var dataSource =
        socketSource(
            id,
            name,
            ingressMapperId,
            TestEgressMapper.forAccess(jdbcTemplate, access),
            spec,
            access);
    NativeIntegrationFixture.seed(jdbcTemplate);
    dataSourceRepository.persist(dataSource);
    if (assignedInstance == null) {
      jdbcTemplate.update(
          "UPDATE profiles.data_source SET assigned_instance = NULL WHERE id = ?", id);
    } else {
      dataSourceRepository.setAssignedInstance(id, assignedInstance, null);
    }
    dataSourceRepository.setEnabled(id, enabled, null);
  }

  public void persistKafkaSource(UUID id, String name, UUID mapperId, boolean enabled) {
    var dataSource =
        new KafkaDataSource(
            id,
            name,
            "default",
            "topic-" + id,
            "user-" + id,
            mapperId,
            NativeIntegrationFixture.CONNECTION_SCHEMA,
            AccessMode.READ,
            enabled,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "localhost:9092",
            true);
    NativeIntegrationFixture.seed(jdbcTemplate);
    dataSourceRepository.persist(dataSource);
    dataSourceRepository.setEnabled(id, enabled, null);
  }

  public void persistUdpSource(UUID id, String name, UUID mapperId, boolean enabled) {
    var spec = new ConnectionSpec(new UdpConfig("127.0.0.1", UDP_LISTEN_PORT, null));

    var dataSource = socketSource(id, name, mapperId, null, spec, AccessMode.READ);
    NativeIntegrationFixture.seed(jdbcTemplate);
    dataSourceRepository.persist(dataSource);
    dataSourceRepository.setEnabled(id, enabled, null);
  }

  private static SocketDataSource socketSource(
      UUID id,
      String name,
      UUID ingressMapperId,
      UUID egressMapperId,
      ConnectionSpec spec,
      AccessMode access) {
    return new SocketDataSource(
        id,
        name,
        "default",
        spec,
        ingressMapperId,
        egressMapperId,
        NativeIntegrationFixture.CONNECTION_SCHEMA,
        access,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  public int countDataSources() {
    return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM profiles.data_source", Integer.class);
  }

  public int countArchivedSources(UUID sourceId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
          FROM profiles.data_source
         WHERE id = ?
           AND archived_at IS NOT NULL
        """,
        Integer.class,
        sourceId);
  }

  public int countRuntimeRows(UUID sourceId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM profiles.source_runtime_observation WHERE source_id = ?",
        Integer.class,
        sourceId);
  }

  public int countRestartRows(UUID sourceId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM profiles.socket_source_restart WHERE source_id = ?",
        Integer.class,
        sourceId);
  }

  public Map<String, Object> loadDataSourceRowByName(String name) {
    return jdbcTemplate.queryForMap("SELECT * FROM profiles.data_source WHERE name = ?", name);
  }

  /**
   * Changes a stored reading source's access together with the mappers it holds for it: a writable
   * source names the test egress mapper, and only a reading one keeps its ingress mapper.
   */
  public void setAccess(UUID sourceId, AccessMode access) {
    jdbcTemplate.update(
        """
        UPDATE profiles.data_source ds
           SET access = ?::profiles.access_mode,
               ingress_mapper_id = CASE WHEN ? THEN ds.ingress_mapper_id END,
               egress_mapper_id = ?
         WHERE ds.id = ?
        """,
        access.getAccess(),
        access.canRead(),
        TestEgressMapper.forAccess(jdbcTemplate, access),
        sourceId);
  }

  /**
   * Turns a stored socket source into the given transport and access mode, rewriting its spec's
   * transport to match, so the row stays a valid source of that transport.
   */
  public void setSocketTransportAndAccess(UUID sourceId, String transport, String access) {
    var transportJson =
        switch (transport) {
          case "TCP" -> "{\"type\":\"tcp\",\"host\":\"127.0.0.1\",\"port\":8087}";
          case "UDP" ->
              "{\"type\":\"udp\",\"host\":\"127.0.0.1\",\"port\":9000,\"localPort\":null}";
          case "RTSP" -> "{\"type\":\"rtsp\",\"url\":\"rtsp://127.0.0.1:8554/stream\"}";
          default -> throw new IllegalArgumentException("Not a socket transport: " + transport);
        };
    jdbcTemplate.update(
        """
        UPDATE profiles.data_source ds
           SET provider = ?::profiles.transport_kind,
               access = ?::profiles.access_mode,
               ingress_mapper_id = CASE WHEN ? THEN ds.ingress_mapper_id END,
               egress_mapper_id = ?,
               connection_spec = jsonb_set(connection_spec, '{transport}', ?::jsonb)
         WHERE ds.id = ?
        """,
        transport,
        access,
        AccessMode.fromString(access).canRead(),
        TestEgressMapper.forAccess(jdbcTemplate, AccessMode.fromString(access)),
        transportJson,
        sourceId);
  }

  /** The stored {@code transport} of the named connection's spec. */
  public JsonNode loadTransport(String name) {
    return objectMapper.readTree(
        jdbcTemplate.queryForObject(
            "SELECT connection_spec -> 'transport' FROM profiles.data_source WHERE name = ?",
            String.class,
            name));
  }

  public Boolean loadEnabled(UUID sourceId) {
    return jdbcTemplate.queryForObject(
        "SELECT enabled FROM profiles.data_source WHERE id = ?", Boolean.class, sourceId);
  }

  public Map<String, Object> loadEnabledAndAssignedInstance(UUID sourceId) {
    return jdbcTemplate.queryForMap(
        "SELECT enabled, assigned_instance FROM profiles.data_source WHERE id = ?", sourceId);
  }

  public static com.opencop.core.sdk.business.model.iam.access.SourceUser sourceUser(
      UUID userId, UUID sourceId, com.opencop.core.sdk.technical.gateway.db.iam.ServerRole role) {
    return new com.opencop.core.sdk.business.model.iam.access.SourceUser.Builder()
        .withUserId(userId)
        .withServerId(sourceId)
        .withRole(role)
        .withUsername("user-" + userId)
        .withGrantedAt(Instant.parse("2026-03-12T10:00:00Z"))
        .build();
  }
}
