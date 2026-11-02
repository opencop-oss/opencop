// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the listen-port index itself — the only seam that can catch a dropped predicate in the
 * migration. Modelled on {@code ArchiveSchemaIT}: plain JUnit, no Spring context.
 */
class DataSourceListenPortSchemaIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static final String LISTEN_PORT_CONSTRAINT = "uk_data_source_listen_port_active";
  private static final String HOST_PORT_INDEX = "ds_host_port_idx";
  private static final String VERSION_BEFORE_LISTEN_PORT_INDEX = "38";
  private static final Instant ARCHIVED_AT = Instant.parse("2026-03-12T10:00:00Z");

  @Test
  void insertUdpSource_whenSecondActiveRowHasSamePort_rejectsWithListenPortIndex()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      insertUdpSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, "127.0.0.1", 9000, null);

      // Different host, same port — the collision the old (host, port) rule allowed through.
      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertUdpSource(
                      jdbc, UUID.randomUUID(), "Bravo", mapperId, "10.0.0.9", 9000, null));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertNotNull(sqlCause);
      assertEquals(LISTEN_PORT_CONSTRAINT, sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertUdpSource_whenThePortHolderIsArchived_acceptsTheActiveRow() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);
      var archivedBy = UUID.randomUUID();
      insertUser(jdbc, archivedBy);

      insertUdpSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, "127.0.0.1", 9000, archivedBy);
      insertUdpSource(jdbc, UUID.randomUUID(), "Bravo", mapperId, "127.0.0.1", 9000, null);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM profiles.data_source WHERE archived_at IS NULL",
              Integer.class));
    }
  }

  @Test
  void insertUdpSource_whenTheHolderIsStopped_stillRejects() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      var holderId = UUID.randomUUID();
      insertUdpSource(jdbc, holderId, "Alpha", mapperId, "127.0.0.1", 9000, null);
      jdbc.update("UPDATE profiles.data_source SET enabled = false WHERE id = ?", holderId);

      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertUdpSource(
                      jdbc, UUID.randomUUID(), "Bravo", mapperId, "127.0.0.1", 9000, null));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertEquals(LISTEN_PORT_CONSTRAINT, sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void setEnabled_onTheOnlyHolderOfAPort_doesNotViolateTheIndex() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      var holderId = UUID.randomUUID();
      insertUdpSource(jdbc, holderId, "Alpha", mapperId, "127.0.0.1", 9000, null);

      jdbc.update("UPDATE profiles.data_source SET enabled = false WHERE id = ?", holderId);
      jdbc.update("UPDATE profiles.data_source SET enabled = true WHERE id = ?", holderId);

      assertEquals(
          Boolean.TRUE,
          jdbc.queryForObject(
              "SELECT enabled FROM profiles.data_source WHERE id = ?", Boolean.class, holderId));
    }
  }

  @Test
  void insertUdpSource_whoseLocalPortIsAnotherSourcesPort_rejectsWithListenPortIndex()
      throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      insertUdpSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, "127.0.0.1", 9000, null);

      // Bravo binds its localPort, which is the port Alpha binds.
      var error =
          assertThrows(
              DataIntegrityViolationException.class,
              () ->
                  insertUdpSource(
                      jdbc, UUID.randomUUID(), "Bravo", mapperId, "127.0.0.1", 7000, 9000, null));
      var sqlCause = (PSQLException) NestedExceptionUtils.getMostSpecificCause(error);
      assertEquals(LISTEN_PORT_CONSTRAINT, sqlCause.getServerErrorMessage().getConstraint());
    }
  }

  @Test
  void insertUdpSources_sharingPortButBindingDistinctLocalPorts_areBothAccepted() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      insertUdpSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, "127.0.0.1", 9000, 9101, null);
      insertUdpSource(jdbc, UUID.randomUUID(), "Bravo", mapperId, "127.0.0.1", 9000, 9102, null);

      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM profiles.data_source WHERE provider = 'UDP'", Integer.class));
    }
  }

  @Test
  void insertTcpSources_sharingHostAndPort_areBothAccepted() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);
      var mapperId = createMapper(jdbc);

      insertTcpSource(jdbc, UUID.randomUUID(), "Alpha", mapperId, "127.0.0.1", 8087, null);
      insertTcpSource(jdbc, UUID.randomUUID(), "Bravo", mapperId, "127.0.0.1", 8087, null);

      assertEquals(
          2,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM profiles.data_source WHERE provider = 'TCP'", Integer.class));
    }
  }

  @Test
  void hostPortIndex_noLongerExists() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'profiles' AND indexname = ?",
              Integer.class,
              HOST_PORT_INDEX));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'profiles' AND indexname = ?",
              Integer.class,
              LISTEN_PORT_CONSTRAINT));
    }
  }

  /**
   * The index this migration drops is created in the baseline, so an environment that was baselined
   * past it — or hand-tuned — may not carry it. Dropping it unconditionally would fail startup over
   * an index the migration wants gone anyway.
   */
  @Test
  void migration_whenTheHostPortIndexIsAlreadyAbsent_stillCompletes() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateTo(dataSource, VERSION_BEFORE_LISTEN_PORT_INDEX);
      var jdbc = new JdbcTemplate(dataSource);
      jdbc.execute("DROP INDEX profiles." + HOST_PORT_INDEX);

      migrateAll(dataSource);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'profiles' AND indexname = ?",
              Integer.class,
              LISTEN_PORT_CONSTRAINT));
    }
  }

  private static void migrateTo(DataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(version)
        .load()
        .migrate();
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  private static UUID createMapper(JdbcTemplate jdbc) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        mapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        mapperId,
        1,
        "mapper-" + mapperId,
        "{}");
    return mapperId;
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  private static void insertUdpSource(
      JdbcTemplate jdbc,
      UUID id,
      String name,
      UUID mapperId,
      String host,
      int port,
      UUID archivedBy) {
    insertUdpSource(jdbc, id, name, mapperId, host, port, null, archivedBy);
  }

  private static void insertUdpSource(
      JdbcTemplate jdbc,
      UUID id,
      String name,
      UUID mapperId,
      String host,
      int port,
      Integer localPort,
      UUID archivedBy) {
    insertDataSource(
        jdbc,
        id,
        name,
        mapperId,
        TransportKind.UDP,
        "{\"type\":\"udp\",\"host\":\"%s\",\"port\":%d,\"localPort\":%s}"
            .formatted(host, port, localPort),
        archivedBy);
  }

  private static void insertTcpSource(
      JdbcTemplate jdbc,
      UUID id,
      String name,
      UUID mapperId,
      String host,
      int port,
      UUID archivedBy) {
    insertDataSource(
        jdbc,
        id,
        name,
        mapperId,
        TransportKind.TCP,
        "{\"type\":\"tcp\",\"host\":\"%s\",\"port\":%d,\"security\":{\"type\":\"none\"}}"
            .formatted(host, port),
        archivedBy);
  }

  /**
   * The relational {@code provider} keys the index while the serialized transport discriminator
   * says what the row actually is, so a fixture that lets them disagree can make a narrow index
   * assertion pass on domain-invalid data. Every row it writes is read back and checked before any
   * test asserts index behaviour.
   */
  private static void insertDataSource(
      JdbcTemplate jdbc,
      UUID id,
      String name,
      UUID mapperId,
      TransportKind provider,
      String transportJson,
      UUID archivedBy) {
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        """
        INSERT INTO profiles.data_source (
            id, name, ingress_mapper_id, connection_spec, enabled, access,
            provider, archived_at, archived_by, integration_id, connection_schema_version
        ) VALUES (
            ?, ?, ?, CAST(? AS jsonb), ?,
            CAST(? AS profiles.access_mode),
            CAST(? AS profiles.transport_kind), ?, ?, ?, ?
        )
        """,
        id,
        name,
        mapperId,
        """
        {"transport":%s}"""
            .formatted(transportJson),
        true,
        "R",
        provider.name(),
        archivedBy == null ? null : Timestamp.from(ARCHIVED_AT),
        archivedBy,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());

    var row =
        jdbc.queryForMap(
            """
            SELECT provider::text AS provider, connection_spec #>> '{transport,type}' AS transport_type
              FROM profiles.data_source WHERE id = ?
            """,
            id);
    assertEquals(
        provider.name().toLowerCase(Locale.ROOT),
        row.get("transport_type"),
        "Fixture row " + name + " must serialize the transport its provider column declares");
    assertEquals(provider.name(), row.get("provider"));
  }
}
