// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.component.general.source.DataSourceAutoStartWebTestApplication;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.general.ports.gateway.connection.SocketReconciliationNudgePublisher;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.api.ConnectInstanceResolver;
import com.opencop.core.sdk.ports.gateway.api.ConnectService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = DataSourceAutoStartWebTestApplication.class)
class DataSourceArchiveRepositoryIT extends AbstractDatabaseIT {

  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440a03");
  private static final UUID ACTIVE_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440a04");
  private static final UUID ARCHIVED_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440a05");
  private static final UUID ARCHIVED_BY = UUID.fromString("550e8400-e29b-41d4-a716-446655440a06");
  private static final UUID ACCESS_USER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440a08");
  private static final UUID ACCESS_LAYER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440a09");

  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private ConnectService connectService;
  @MockitoBean private SocketReconciliationNudgePublisher nudgePublisher;
  @MockitoBean private ConnectInstanceResolver connectInstanceResolver;

  private ConnectionFixtures fixtures;

  @BeforeEach
  void setUp() {
    fixtures = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, null);
    fixtures.reset();
    fixtures.seedDefaultMapper(MAPPER_ID);
    seedUser(ARCHIVED_BY);
  }

  @Test
  void findAllActive_excludesArchivedSources() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Active Alpha", MAPPER_ID, true, null);
    fixtures.persistTcpSource(ARCHIVED_SOURCE_ID, "Archived Bravo", MAPPER_ID, true, "connect-1");
    dataSourceRepository.archive(ARCHIVED_SOURCE_ID, ARCHIVED_BY);

    var activeSources = dataSourceRepository.findAll("LOCAL", RecordVisibility.ACTIVE);

    assertTrue(
        activeSources.stream().map(source -> source.id()).anyMatch(ACTIVE_SOURCE_ID::equals));
    assertFalse(
        activeSources.stream().map(source -> source.id()).anyMatch(ARCHIVED_SOURCE_ID::equals));
  }

  @Test
  void archive_setsArchiveMetadataDisablesAndDeletesRuntimeRows() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Me", MAPPER_ID, true, "connect-1");
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer (id, name, access_scope)
        VALUES (?, ?, CAST(? AS iam.layer_access_scope))
        """,
        ACCESS_LAYER_ID,
        "archive-layer",
        "PRIVATE");
    jdbcTemplate.update(
        """
        INSERT INTO iam.server_layer_access(server_id, layer_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        ACTIVE_SOURCE_ID,
        ACCESS_LAYER_ID,
        "ADMIN");
    jdbcTemplate.update(
        """
        INSERT INTO iam.server_user_access(server_id, user_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        ACTIVE_SOURCE_ID,
        ACCESS_USER_ID,
        "OWNER");
    jdbcTemplate.update(
        """
        INSERT INTO profiles.source_runtime_observation(
          source_id, observed_instance_id, state, observed_at, observation_expires_at, connector_started_at
        ) VALUES (?, ?, ?, now(), now() + interval '2 minutes', now())
        """,
        ACTIVE_SOURCE_ID,
        "connect-1",
        "CONNECTED");
    jdbcTemplate.update(
        """
        INSERT INTO profiles.socket_source_restart(source_id, restart_epoch)
        VALUES (?, ?)
        """,
        ACTIVE_SOURCE_ID,
        3L);

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);
    var row =
        jdbcTemplate.queryForMap(
            """
            SELECT archived_at, archived_by, enabled, assigned_instance
              FROM profiles.data_source
             WHERE id = ?
            """,
            ACTIVE_SOURCE_ID);
    assertTrue(row.get("archived_at") != null);
    assertEquals(ARCHIVED_BY, row.get("archived_by"));
    assertEquals(Boolean.FALSE, row.get("enabled"));
    assertEquals(null, row.get("assigned_instance"));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles.source_runtime_observation WHERE source_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles.socket_source_restart WHERE source_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_layer_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
    assertEquals(
        1,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_user_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_preservesSourceAccessRowsForReplay() {
    var accessLayerId = UUID.randomUUID();
    var accessUserId = UUID.randomUUID();
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Access", MAPPER_ID, true, "connect-1");
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer (id, name, access_scope)
        VALUES (?, ?, CAST(? AS iam.layer_access_scope))
        """,
        accessLayerId,
        "replay-layer",
        "PRIVATE");
    jdbcTemplate.update(
        """
        INSERT INTO iam.server_layer_access(server_id, layer_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        ACTIVE_SOURCE_ID,
        accessLayerId,
        "ADMIN");
    jdbcTemplate.update(
        """
        INSERT INTO iam.server_user_access(server_id, user_id, role)
        VALUES (?, ?, CAST(? AS iam.server_role))
        """,
        ACTIVE_SOURCE_ID,
        accessUserId,
        "OWNER");
    var layerAccessCountBeforeArchive =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_layer_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID);
    var userAccessCountBeforeArchive =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_user_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID);

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertEquals(
        layerAccessCountBeforeArchive,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_layer_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
    assertEquals(
        userAccessCountBeforeArchive,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.server_user_access WHERE server_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_setsArchivedAt() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Timestamp", MAPPER_ID, true, "connect-1");

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertNotNull(
        jdbcTemplate.queryForObject(
            "SELECT archived_at FROM profiles.data_source WHERE id = ?",
            Instant.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_setsArchivedBy() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive User", MAPPER_ID, true, "connect-1");

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertEquals(
        ARCHIVED_BY,
        jdbcTemplate.queryForObject(
            "SELECT archived_by FROM profiles.data_source WHERE id = ?",
            UUID.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_disablesDataSource() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Disable", MAPPER_ID, true, "connect-1");

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertEquals(
        false,
        jdbcTemplate.queryForObject(
            "SELECT enabled FROM profiles.data_source WHERE id = ?",
            Boolean.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_unassignsRuntimeInstance() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Unassign", MAPPER_ID, true, "connect-1");

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertNull(
        jdbcTemplate.queryForObject(
            "SELECT assigned_instance FROM profiles.data_source WHERE id = ?",
            String.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_deletesSocketRestartRows() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Archive Restart", MAPPER_ID, true, "connect-1");
    jdbcTemplate.update(
        """
        INSERT INTO profiles.socket_source_restart(source_id, restart_epoch)
        VALUES (?, ?)
        """,
        ACTIVE_SOURCE_ID,
        5L);

    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);

    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM profiles.socket_source_restart WHERE source_id = ?",
            Integer.class,
            ACTIVE_SOURCE_ID));
  }

  @Test
  void archive_whenSourceAlreadyArchived_returnsNotFoundWithoutRefreshingArchiveMetadata() {
    var secondArchivedBy = UUID.fromString("550e8400-e29b-41d4-a716-446655440a07");
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Already Archived", MAPPER_ID, true, null);
    dataSourceRepository.archive(ACTIVE_SOURCE_ID, ARCHIVED_BY);
    var archivedByBefore =
        jdbcTemplate.queryForObject(
            "SELECT archived_by FROM profiles.data_source WHERE id = ?",
            UUID.class,
            ACTIVE_SOURCE_ID);

    assertThrows(
        GlobalOpenCopException.class,
        () -> dataSourceRepository.archive(ACTIVE_SOURCE_ID, secondArchivedBy));

    var archivedByAfter =
        jdbcTemplate.queryForObject(
            "SELECT archived_by FROM profiles.data_source WHERE id = ?",
            UUID.class,
            ACTIVE_SOURCE_ID);
    assertEquals(archivedByBefore, archivedByAfter);
  }

  @Test
  void findAllArchived_returnsOnlyArchivedSources() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Still Active", MAPPER_ID, true, null);
    fixtures.persistTcpSource(ARCHIVED_SOURCE_ID, "Now Archived", MAPPER_ID, true, null);
    dataSourceRepository.archive(ARCHIVED_SOURCE_ID, ARCHIVED_BY);

    var archivedSources = dataSourceRepository.findAll("LOCAL", RecordVisibility.ARCHIVED);

    assertTrue(
        archivedSources.stream().map(source -> source.id()).anyMatch(ARCHIVED_SOURCE_ID::equals));
    assertFalse(
        archivedSources.stream().map(source -> source.id()).anyMatch(ACTIVE_SOURCE_ID::equals));
  }

  @Test
  void findAllAll_returnsActiveAndArchivedSources() {
    fixtures.persistTcpSource(ACTIVE_SOURCE_ID, "Active", MAPPER_ID, true, null);
    fixtures.persistTcpSource(ARCHIVED_SOURCE_ID, "Archived", MAPPER_ID, true, null);
    dataSourceRepository.archive(ARCHIVED_SOURCE_ID, ARCHIVED_BY);

    var allSources = dataSourceRepository.findAll("LOCAL", RecordVisibility.ALL);

    assertTrue(allSources.stream().map(source -> source.id()).anyMatch(ACTIVE_SOURCE_ID::equals));
    assertTrue(allSources.stream().map(source -> source.id()).anyMatch(ARCHIVED_SOURCE_ID::equals));
  }

  private void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
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
