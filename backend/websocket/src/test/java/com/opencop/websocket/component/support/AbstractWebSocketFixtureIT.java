// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support;

import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.technical.gateway.cache.EventGeoCacheKeyLayout;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import com.opencop.websocket.component.support.fixture.RecordingBinaryWebSocketHandler;
import com.opencop.websocket.component.support.fixture.WebSocketFixtures;
import com.opencop.websocket.technical.spring.websocket.SessionExpiryKiller;
import com.opencop.websocket.technical.spring.websocket.UserSession;
import com.opencop.websocket.technical.spring.websocket.WsSessionRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.sql.DataSource;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import opencop.core.proto.websocket.SocketProtos.SocketMessage.MultiPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public abstract class AbstractWebSocketFixtureIT extends AbstractDatabaseIT {

  private static final String DB_USERNAME = "postgres";
  private static final String DB_PASSWORD = "postgres";

  @LocalServerPort protected int port;

  @Autowired protected DataSource dataSource;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected EventGeoCacheKeyLayout eventGeoCacheKeyLayout;
  @Autowired protected RedisTemplate<String, byte[]> redisTemplate;
  @Autowired protected StringRedisTemplate stringRedisTemplate;
  @Autowired protected SessionExpiryKiller sessionExpiryKiller;
  @Autowired protected WsSessionRegistry sessionRegistry;

  protected JdbcTemplate jdbcTemplate;
  protected final WebSocketFixtures fixture = new WebSocketFixtures();

  protected static void registerCommonWebSocketComponentProperties(
      DynamicPropertyRegistry registry) {
    registry.add("opencop.db.url", AbstractWebSocketFixtureIT::jdbcUrl);
    registry.add("opencop.db.username", () -> DB_USERNAME);
    registry.add("opencop.db.password", () -> DB_PASSWORD);
    registry.add("idp.issuer", () -> "https://component-tests.opencop.local");
    registry.add(
        "spring.flyway.placeholders.postgisExtensionSql",
        () -> FlywayGeoFeaturesPlaceholders.POSTGIS_EXTENSION_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesGeomType",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_GEOM_TYPE);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesGeomIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_GEOM_INDEX_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesHistoryChunkIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_HISTORY_CHUNK_INDEX_SQL);
    registry.add(
        "spring.flyway.placeholders.geoFeaturesActiveHistoryLookupIndexSql",
        () -> FlywayGeoFeaturesPlaceholders.GEO_FEATURES_ACTIVE_HISTORY_LOOKUP_INDEX_SQL);
    registry.add("opencop.dev.mode", () -> "true");
    registry.add("spring.session.store-type", () -> "none");
    registry.add("websocket.allowed-origins", () -> "*");
  }

  @BeforeEach
  void setUpJdbcTemplate() {
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("TRUNCATE TABLE iam.data_source_group CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.server_user_access CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.server_layer_access CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.group_layer_member CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.user_group_member CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.user_group CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.layer_member CASCADE");
    jdbcTemplate.execute("TRUNCATE TABLE iam.layer CASCADE");
  }

  @AfterEach
  void resetWebSocketFixtures() {
    fixture.reset();
    for (var userSession : sessionRegistry.getUsersSessions()) {
      sessionExpiryKiller.cancel(userSession);
      try {
        userSession.getWebsocketSession().close();
      } catch (Exception ignored) {
      }
      sessionRegistry.remove(userSession);
    }
  }

  protected String websocketUrl() {
    return "ws://127.0.0.1:" + port + "/ws";
  }

  protected int pingDatabase() {
    Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    if (value == null) {
      throw new AssertionError("Database ping returned null");
    }
    return value;
  }

  protected void seedCurrentAccount(UUID userId, long sessionGeneration) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, active, administrator, session_generation
        ) VALUES (?, ?, ?, ?, ?, true, false, ?)
        ON CONFLICT (id) DO UPDATE
        SET active = true,
            session_generation = EXCLUDED.session_generation
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        sessionGeneration);
  }

  protected UserSession awaitSingleUserSession() {
    await(
        () -> sessionRegistry.getUsersSessions().size() == 1,
        fixture.defaultTimeout(),
        "exactly one connected user session");
    return sessionRegistry.getUsersSessions().getFirst();
  }

  protected UUID awaitCurrentUserId() {
    return awaitSingleUserSession().getUserId();
  }

  protected void awaitNoRegisteredSessions() {
    await(
        () -> sessionRegistry.getUsersSessions().isEmpty(),
        fixture.defaultTimeout(),
        "session registry cleanup");
  }

  protected SocketMessage awaitMessage(
      RecordingBinaryWebSocketHandler handler, String description) {
    return awaitValue(
        () -> {
          try {
            return handler.pollMessage(50, java.util.concurrent.TimeUnit.MILLISECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + description, e);
          }
        },
        value -> value != null,
        fixture.defaultTimeout(),
        description);
  }

  protected org.springframework.web.socket.CloseStatus awaitCloseStatus(
      RecordingBinaryWebSocketHandler handler, String description) {
    return awaitValue(
        handler::closeStatus, value -> value != null, fixture.defaultTimeout(), description);
  }

  protected void seedSourceAccess(UUID sourceId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.server_user_access(server_id, user_id, role) VALUES (?, ?, 'VIEWER')",
        sourceId,
        userId);
  }

  protected void seedActiveSourceAccess(UUID sourceId, UUID userId) {
    seedSourceRow(sourceId, false, null);
    seedSourceAccess(sourceId, userId);
  }

  protected void seedArchivedSourceAccess(UUID sourceId, UUID userId, UUID archivedBy) {
    seedOpencopUser(archivedBy, "Archive", "User");
    seedSourceRow(sourceId, true, archivedBy);
    seedSourceAccess(sourceId, userId);
  }

  protected void seedDataSourceGroupSourceAccess(UUID sourceId, UUID userId) {
    seedSourceRow(sourceId, false, null);
    seedOpencopUser(userId, "Test", "User");
    UUID dataSourceGroupId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.data_source_group(id, name) VALUES (?, ?)",
        dataSourceGroupId,
        "dsg-" + dataSourceGroupId);
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
        VALUES (?, ?, 'MEMBER')
        """,
        dataSourceGroupId,
        userId);
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
        VALUES (?, ?, 'VIEWER')
        """,
        dataSourceGroupId,
        sourceId);
  }

  protected UUID seedGlobalLayerDataSourceGroupSourceAccess(UUID layerId, UUID sourceId) {
    seedSourceRow(sourceId, false, null);
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer(id, name, access_scope)
        VALUES (?, ?, 'GLOBAL'::iam.layer_access_scope)
        ON CONFLICT (id) DO NOTHING
        """,
        layerId,
        "global-layer-" + layerId);
    UUID dataSourceGroupId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.data_source_group(id, name) VALUES (?, ?)",
        dataSourceGroupId,
        "dsg-" + dataSourceGroupId);
    jdbcTemplate.update(
        """
        INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
        VALUES (?, ?, 'VIEWER')
        """,
        dataSourceGroupId,
        sourceId);
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
        VALUES (?, ?)
        """,
        layerId,
        dataSourceGroupId);
    return dataSourceGroupId;
  }

  protected void archiveSeededSource(UUID sourceId, UUID archivedBy) {
    seedOpencopUser(archivedBy, "Archive", "User");
    jdbcTemplate.update(
        """
        UPDATE profiles.data_source
        SET archived_at = ?,
            archived_by = ?
        WHERE id = ?
        """,
        Timestamp.from(Instant.now()),
        archivedBy,
        sourceId);
  }

  protected void removeLayerDataSourceGroupEmbedding(UUID layerId, UUID dataSourceGroupId) {
    jdbcTemplate.update(
        """
        DELETE FROM iam.layer_data_source_group
        WHERE layer_id = ?
          AND data_source_group_id = ?
        """,
        layerId,
        dataSourceGroupId);
  }

  protected void seedLayerAccess(UUID layerId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer(id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    jdbcTemplate.update(
        "INSERT INTO iam.layer_member(layer_id, user_id, role) VALUES (?, ?, 'MEMBER')",
        layerId,
        userId);
  }

  protected void archiveSeededLayer(UUID layerId, UUID archivedBy) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        archivedBy,
        "idp-" + archivedBy,
        "user-" + archivedBy,
        "https://issuer.test",
        "user-" + archivedBy,
        "user-" + archivedBy + "@example.com",
        "Archive",
        "User",
        "Archive User");
    jdbcTemplate.update(
        """
        UPDATE iam.layer
        SET archived_at = ?,
            archived_by = ?,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = ?
        """,
        Timestamp.from(Instant.now()),
        archivedBy,
        layerId);
  }

  protected void updateSeededLayerLifecycle(UUID layerId, String lifecycleState) {
    jdbcTemplate.update(
        "UPDATE iam.layer SET lifecycle_state = ?::iam.layer_lifecycle_state WHERE id = ?",
        lifecycleState,
        layerId);
  }

  private void seedSourceRow(UUID sourceId, boolean archived, UUID archivedBy) {
    UUID mapperId = UUID.nameUUIDFromBytes(("mapper-" + sourceId).getBytes(StandardCharsets.UTF_8));

    jdbcTemplate.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        mapperId);
    jdbcTemplate.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, description, editable, mapper)
        VALUES (?, 1, ?, 'component-test mapper', true, '{}'::jsonb)
        ON CONFLICT (id, version) DO NOTHING
        """,
        mapperId,
        "mapper-" + sourceId);
    NativeIntegrationFixture.seed(jdbcTemplate);
    jdbcTemplate.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, ingress_mapper_id, connection_spec, enabled, access,
          archived_at, archived_by, integration_id, connection_schema_version
        )
        VALUES (?, ?, 'component-tests', 'TCP', ?, '{}'::jsonb, true, 'R', ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        sourceId,
        "source-" + sourceId,
        mapperId,
        archived ? Timestamp.from(Instant.now()) : null,
        archived ? archivedBy : null,
        NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
        NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }

  private void seedOpencopUser(UUID userId, String firstName, String lastName) {
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
        "user-" + userId + "@example.com",
        firstName,
        lastName,
        firstName + " " + lastName);
  }

  protected void seedLayerAccessViaGroup(UUID layerId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer(id, name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
        layerId,
        "layer-" + layerId);
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
        "user-" + userId + "@example.com",
        "Test",
        "User",
        "Test User");
    UUID groupId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.user_group(id, name) VALUES (?, ?)", groupId, "group-" + groupId);
    jdbcTemplate.update(
        "INSERT INTO iam.user_group_member(group_id, user_id) VALUES (?, ?)", groupId, userId);
    jdbcTemplate.update(
        "INSERT INTO iam.group_layer_member(group_id, layer_id, role) VALUES (?, ?, ?::iam.layer_role)",
        groupId,
        layerId,
        role);
  }

  protected JsonNode readJson(byte[] payload) throws Exception {
    return objectMapper.readTree(payload);
  }

  protected List<byte[]> readMultiPayload(SocketMessage message) throws Exception {
    MultiPayload payload = MultiPayload.parseFrom(message.getPayload());
    return payload.getPayloadList().stream().map(bytes -> bytes.toByteArray()).toList();
  }

  protected void await(BooleanSupplier condition, Duration timeout, String description) {
    awaitValue(() -> condition.getAsBoolean(), Boolean.TRUE::equals, timeout, description);
  }

  protected <T> T awaitValue(
      Supplier<T> supplier,
      java.util.function.Predicate<T> completed,
      Duration timeout,
      String description) {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      T value = supplier.get();
      if (completed.test(value)) {
        return value;
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for " + description, e);
      }
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  private static EmbeddedPostgres embeddedPostgres() {
    try {
      var field = AbstractDatabaseIT.class.getDeclaredField("POSTGRES");
      field.setAccessible(true);
      return (EmbeddedPostgres) field.get(null);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to reuse embedded PostgreSQL", e);
    }
  }

  protected static String jdbcUrl() {
    try (var connection = embeddedPostgres().getPostgresDatabase().getConnection()) {
      return connection.getMetaData().getURL();
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to determine embedded PostgreSQL JDBC URL", e);
    }
  }
}
