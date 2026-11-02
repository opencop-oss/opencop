// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceListenPortConflictException;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceNameConflictException;
import com.opencop.core.api.general.technical.gateway.db.connection.JdbcDataSourceRepository;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.connection.spec.transport.TransportConfig;
import com.opencop.core.sdk.model.connection.spec.transport.UdpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves how {@link JdbcDataSourceRepository} reads and reports listen-port and name occupancy
 * against a real PostgreSQL schema — the seam where the SQL, the partial indexes, and the typed
 * conflict exceptions have to agree. Modelled on {@code DataSourceListenPortSchemaIT}: plain JUnit,
 * no Spring context, the repository instantiated directly.
 */
class JdbcDataSourceRepositoryConflictIT {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private static final int HELD_LISTEN_PORT = 8087;
  private static final int OTHER_LISTEN_PORT = 8088;
  private static final String SHARED_HOST = "127.0.0.1";
  private static final String OTHER_HOST = "10.0.0.9";
  private static final String TENANT = "default";
  private static final String HELD_NAME = "Port Holder";
  private static final String CONTENDER_NAME = "Port Contender";

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;
  private static JdbcDataSourceRepository repository;
  private static UUID mapperId;
  private static UUID archivedBy;
  private static UUID creator;
  private static UUID editor;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    var dataSource = postgres.getPostgresDatabase();
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();

    jdbc = new JdbcTemplate(dataSource);
    NativeIntegrationFixture.seed(jdbc);
    repository =
        new JdbcDataSourceRepository(
            new NamedParameterJdbcTemplate(dataSource), JsonMapper.builder().build());
    mapperId = createMapper();
    archivedBy = UUID.randomUUID();
    insertUser(archivedBy);
    // created_by/updated_by are FKs to opencop_user, so the actors have to exist as real rows.
    creator = UUID.randomUUID();
    insertUser(creator);
    editor = UUID.randomUUID();
    insertUser(editor);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    postgres.close();
  }

  @BeforeEach
  void clearDataSources() {
    jdbc.execute("TRUNCATE profiles.data_source CASCADE");
  }

  @Test
  void findActiveByListenPort_returnsTheActiveHolderOfThePort() {
    var holderId = UUID.randomUUID();
    repository.persist(udpSource(holderId, HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    var holder = repository.findActiveByListenPort(TransportKind.UDP, HELD_LISTEN_PORT);

    assertEquals(holderId, holder.orElseThrow());
  }

  @Test
  void findActiveByListenPort_ignoresArchivedRows() {
    var holderId = UUID.randomUUID();
    repository.persist(udpSource(holderId, HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));
    repository.archive(holderId, archivedBy);

    var holder = repository.findActiveByListenPort(TransportKind.UDP, HELD_LISTEN_PORT);

    assertTrue(holder.isEmpty());
  }

  @Test
  void findActiveByListenPort_stillReportsAHolderThatIsMerelyStopped() {
    var holderId = UUID.randomUUID();
    repository.persist(udpSource(holderId, HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));
    repository.setEnabled(holderId, false, null);

    var holder = repository.findActiveByListenPort(TransportKind.UDP, HELD_LISTEN_PORT);

    assertEquals(holderId, holder.orElseThrow());
  }

  @Test
  void findActiveByListenPort_ignoresAnotherPort() {
    repository.persist(udpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    var holder = repository.findActiveByListenPort(TransportKind.UDP, OTHER_LISTEN_PORT);

    assertTrue(holder.isEmpty());
  }

  @Test
  void persist_whenAnotherActiveUdpSourceHoldsThePort_raisesListenPortConflict() {
    repository.persist(udpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    // Different host, same port: the collision the old (host, port) rule allowed through. This
    // bypasses the use-case pre-check, so it is the race path the unique index closes.
    var conflict =
        assertThrows(
            DataSourceListenPortConflictException.class,
            () ->
                repository.persist(
                    udpSource(UUID.randomUUID(), CONTENDER_NAME, OTHER_HOST, HELD_LISTEN_PORT)));

    assertEquals(HELD_LISTEN_PORT, conflict.listenPort());
  }

  @Test
  void persist_whenThePortHolderIsArchived_isAccepted() {
    var holderId = UUID.randomUUID();
    repository.persist(udpSource(holderId, HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));
    repository.archive(holderId, archivedBy);

    var successorId = UUID.randomUUID();
    repository.persist(udpSource(successorId, CONTENDER_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    assertEquals(
        successorId,
        repository.findActiveByListenPort(TransportKind.UDP, HELD_LISTEN_PORT).orElseThrow());
  }

  @Test
  void persist_whenAnotherActiveSourceHoldsTheName_raisesNameConflict() {
    repository.persist(udpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    var conflict =
        assertThrows(
            DataSourceNameConflictException.class,
            () ->
                repository.persist(
                    udpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, OTHER_LISTEN_PORT)));

    assertEquals(HELD_NAME, conflict.name());
  }

  @Test
  void persist_whenTheNameHolderIsArchived_isAccepted() {
    var holderId = UUID.randomUUID();
    repository.persist(udpSource(holderId, HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));
    repository.archive(holderId, archivedBy);

    repository.persist(udpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM profiles.data_source WHERE name = ? AND archived_at IS NULL",
            Integer.class,
            HELD_NAME));
  }

  @Test
  void persist_whenTwoOutboundSourcesShareAnEndpoint_acceptsBoth() {
    repository.persist(tcpSource(UUID.randomUUID(), HELD_NAME, SHARED_HOST, HELD_LISTEN_PORT));
    repository.persist(tcpSource(UUID.randomUUID(), CONTENDER_NAME, SHARED_HOST, HELD_LISTEN_PORT));

    assertEquals(
        2,
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM profiles.data_source
             WHERE connection_spec #>> '{transport,host}' = ?
               AND (connection_spec #>> '{transport,port}')::integer = ?
            """,
            Integer.class,
            SHARED_HOST,
            HELD_LISTEN_PORT));
  }

  @Test
  void persistThenSetEnabled_keepsTheCreatorAndRecordsTheActorWhoChangedIt() {
    var sourceId = UUID.randomUUID();
    repository.persist(
        socketSource(
            sourceId, HELD_NAME, new UdpConfig(SHARED_HOST, HELD_LISTEN_PORT, null), creator));

    assertEquals(creator, createdBy(sourceId));
    assertNull(updatedBy(sourceId), "a source nobody has changed yet has no editor");

    repository.setEnabled(sourceId, false, editor);

    assertEquals(editor, updatedBy(sourceId));
    assertEquals(creator, createdBy(sourceId), "a later change must not rewrite the creator");
  }

  /**
   * A system-initiated change clears the editor rather than preserving the previous one, so {@code
   * updated_at} without an {@code updated_by} reads as "the system changed this". Retaining the
   * last human would attribute an automated restart to someone who did not cause it.
   *
   * <p>Every write that moves {@code updated_at} follows this rule — see the assignment cases
   * above, which had to be corrected to match.
   */
  @Test
  void setEnabled_whenSystemInitiated_overwritesThePreviousEditorWithNull() {
    var sourceId = UUID.randomUUID();
    repository.persist(
        socketSource(
            sourceId, HELD_NAME, new UdpConfig(SHARED_HOST, HELD_LISTEN_PORT, null), creator));
    repository.setEnabled(sourceId, false, editor);

    repository.setEnabled(sourceId, true, null);

    assertNull(updatedBy(sourceId));
    assertEquals(creator, createdBy(sourceId));
  }

  @Test
  void setAssignedInstance_whenSystemInitiated_doesNotLeaveThePreviousHumanAsEditor() {
    var sourceId = UUID.randomUUID();
    repository.persist(
        socketSource(
            sourceId, HELD_NAME, new UdpConfig(SHARED_HOST, HELD_LISTEN_PORT, null), creator));
    repository.setEnabled(sourceId, false, editor);
    assertEquals(editor, updatedBy(sourceId));

    // Reassignment moves updated_at via the trigger; without writing updated_by, the fresh
    // timestamp would still name the human who last edited the source.
    repository.setAssignedInstance(sourceId, "connect-1", null);

    assertNull(updatedBy(sourceId), "an automated reassignment must not stay attributed to a user");
  }

  @Test
  void setAssignedInstance_whenUserDriven_recordsThatUserAsTheEditor() {
    var sourceId = UUID.randomUUID();
    repository.persist(
        socketSource(
            sourceId, HELD_NAME, new UdpConfig(SHARED_HOST, HELD_LISTEN_PORT, null), creator));

    repository.setAssignedInstance(sourceId, "connect-1", editor);

    assertEquals(editor, updatedBy(sourceId));
    assertEquals(creator, createdBy(sourceId));
  }

  @Test
  void archive_attributesTheArchiverAsTheLastEditor() {
    var sourceId = UUID.randomUUID();
    repository.persist(
        socketSource(
            sourceId, HELD_NAME, new UdpConfig(SHARED_HOST, HELD_LISTEN_PORT, null), creator));

    repository.archive(sourceId, archivedBy);

    assertEquals(archivedBy, updatedBy(sourceId));
    assertEquals(creator, createdBy(sourceId));
  }

  private static UUID createdBy(UUID sourceId) {
    return jdbc.queryForObject(
        "SELECT created_by FROM profiles.data_source WHERE id = ?", UUID.class, sourceId);
  }

  private static UUID updatedBy(UUID sourceId) {
    return jdbc.queryForObject(
        "SELECT updated_by FROM profiles.data_source WHERE id = ?", UUID.class, sourceId);
  }

  private static SocketDataSource udpSource(UUID id, String name, String host, int port) {
    return socketSource(id, name, new UdpConfig(host, port, null));
  }

  private static SocketDataSource tcpSource(UUID id, String name, String host, int port) {
    return socketSource(
        id, name, new TcpConfig(host, port, 3000, 15000, true, null, new NoSecurity()));
  }

  private static SocketDataSource socketSource(UUID id, String name, TransportConfig transport) {
    return socketSource(id, name, transport, null);
  }

  private static SocketDataSource socketSource(
      UUID id, String name, TransportConfig transport, UUID createdBy) {
    var spec = new ConnectionSpec(transport);
    return SocketDataSource.fromSpec(
        id,
        name,
        mapperId,
        null,
        TENANT,
        spec,
        NativeIntegrationFixture.CONNECTION_SCHEMA,
        AccessMode.READ,
        true,
        createdBy);
  }

  private static UUID createMapper() {
    var newMapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        newMapperId);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        newMapperId,
        1,
        "mapper-" + newMapperId,
        "{}");
    return newMapperId;
  }

  private static void insertUser(UUID userId) {
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
}
