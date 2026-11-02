// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.JdbcStatementCounter;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = {WatchboxWebTestApplication.class, JdbcStatementCounter.Config.class})
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxDetectionsEfficiencyIT extends AbstractWebIT {
  private static final UUID CALLER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final Authentication CALLER_AUTH = TestAuthentications.oidcUser(CALLER_ID);
  private static final int MEMBERSHIP_SHAPES = 6;
  // Measured with real IAM: feed 5, filtered 5, get with embeds 18; each budget adds 2.
  private static final int LIST_STATEMENT_BUDGET = 7;
  private static final int FILTERED_STATEMENT_BUDGET = 7;
  private static final int GET_STATEMENT_BUDGET = 20;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  private UUID callerUserGroupId, directLayerGroupId, transitiveLayerGroupId, globalLayerGroupId;
  private Set<UUID> visibleIds;
  private UUID selectedBox, selectedDetection;

  private EventFixtures events;

  @BeforeEach
  void seed() {
    resetAll();
    events = new EventFixtures(jdbcTemplate, objectMapper);
    events.reset();
    insertUser(CALLER_ID);
    seedCallerGroups();
    var source = insertMinimalDataSource();
    visibleIds = new HashSet<>();
    var batch = new ArrayList<Object[]>();
    int layerIndex = 0;
    var time = Instant.parse("2026-09-10T12:00:00Z");
    for (int i = 0; i < 10; i++) {
      var layerIds = new ArrayList<UUID>();
      for (int j = 0; j < 5; j++)
        layerIds.add(
            i % 5 == 4 && j == 4
                ? insertLayer("hidden-" + i, "PRIVATE")
                : insertReachableLayer(layerIndex++));
      var box = insertWatchbox("wb-" + i, layerIds);
      if (i % 5 != 4) visibleIds.add(box);
      if (i == 0) {
        selectedBox = box;
        jdbcTemplate.update(
            "INSERT INTO iam.server_layer_access (server_id,layer_id,role) VALUES (?,?,CAST('VIEWER' AS iam.server_role))",
            source,
            layerIds.getFirst());
      }
      for (int j = 0; j < 400; j++) {
        var id = UUID.randomUUID();
        var revision = UUID.randomUUID();
        var object = "track-" + i + "-" + j;
        if (i == 0 && j == 0) selectedDetection = id;
        events.insertFeature(revision, object, source, time, time, 1, 2);
        batch.add(
            new Object[] {
              id, box, source, object, revision, Timestamp.from(time.plusSeconds(i * 400L + j))
            });
      }
    }
    jdbcTemplate.batchUpdate(
        "INSERT INTO watchbox.watchbox_detection (id,watchbox_id,config_epoch,source_id,object_id,event_id,transition,event_time,created_at) VALUES (?,?,1,?,?,?,'ENTER',now(),?)",
        batch);
    jdbcTemplate.execute("ANALYZE watchbox.watchbox_detection");
  }

  @AfterEach
  void tearDown() {
    // The seeded boxes reference layers; suites that run later reset layers with a bare DELETE,
    // so leave the shared database as this suite found it.
    resetAll();
    events.reset();
  }

  @Test
  void list_unfiltered_staysWithinStatementBudget() throws Exception {
    var body = measure("/v1/watchbox-detections", "LIST", LIST_STATEMENT_BUDGET);
    assertThat(body.path("totalElements").asInt()).isEqualTo(3200);
  }

  @Test
  void list_filtered_staysWithinStatementBudget() throws Exception {
    var body =
        measure(
            "/v1/watchbox-detections?watchboxId=" + selectedBox,
            "FILTERED",
            FILTERED_STATEMENT_BUDGET);
    assertThat(body.path("totalElements").asInt()).isEqualTo(400);
  }

  @Test
  void get_withEmbeds_staysWithinStatementBudget() throws Exception {
    var body = measure("/v1/watchbox-detections/" + selectedDetection, "GET", GET_STATEMENT_BUDGET);
    assertThat(body.path("triggeringEvent").isObject()).isTrue();
    assertThat(body.path("latestEvent").isObject()).isTrue();
  }

  private JsonNode measure(String url, String probe, int budget) throws Exception {
    request(url);
    JdbcStatementCounter.reset();
    var result = request(url);
    long count = JdbcStatementCounter.executions();
    System.out.println("WATCHBOX_DETECTIONS_" + probe + "_PROBE statements " + count);
    assertThat(count).isPositive().isLessThanOrEqualTo(budget);
    return readResponse(result, JsonNode.class);
  }

  private MvcResult request(String url) throws Exception {
    return mockMvc
        .perform(get(url).with(authentication(CALLER_AUTH)))
        .andExpect(status().isOk())
        .andReturn();
  }

  // enable_seqscan is forced off, so this proves the cursor index is usable for the filtered
  // feed, not that the planner chooses it at this fixture size; the feed plan is printed only.
  @Test
  void queryPlans_filteredFeedCanUseTheCursorIndex() {
    new TransactionTemplate(transactionManager)
        .execute(
            tx -> {
              jdbcTemplate.execute("SET LOCAL enable_seqscan = off");
              var filtered = explain(List.of(selectedBox));
              var feed = explain(List.copyOf(visibleIds));
              System.out.println(
                  "WATCHBOX_DETECTIONS_PLAN_FILTERED\n" + String.join("\n", filtered));
              System.out.println("WATCHBOX_DETECTIONS_PLAN_FEED\n" + String.join("\n", feed));
              assertThat(filtered)
                  .anyMatch(line -> line.contains("Index Cond:") && line.contains("watchbox_id"));
              return null;
            });
  }

  private List<String> explain(List<UUID> ids) {
    var placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    return jdbcTemplate.queryForList(
        "EXPLAIN SELECT id,watchbox_id,config_epoch,source_id,object_id,event_id,transition,event_time,object_display_name,created_at FROM watchbox.watchbox_detection WHERE watchbox_id IN ("
            + placeholders
            + ") ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET 0",
        String.class,
        ids.toArray());
  }

  private UUID insertMinimalDataSource() {
    UUID mapperId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, 'INGRESS'::profiles.mapper_qualifier) ON CONFLICT (id) DO NOTHING",
        mapperId);
    jdbcTemplate.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, ?, ?, CAST(? AS jsonb))",
        mapperId,
        1,
        "probe-mapper-" + mapperId,
        "{}");
    var spec =
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, 3000, 15000, true, null, new NoSecurity()));
    NativeIntegrationFixture.seed(jdbcTemplate);
    dataSourceRepository.persist(
        SocketDataSource.fromSpec(
            sourceId,
            "probe-source-" + sourceId,
            mapperId,
            null,
            "default",
            spec,
            NativeIntegrationFixture.CONNECTION_SCHEMA,
            AccessMode.READ,
            true,
            null));
    return sourceId;
  }

  private UUID insertReachableLayer(int layerIndex) {
    int shape = layerIndex % MEMBERSHIP_SHAPES;
    String name = "layer-" + layerIndex + "-shape-" + shape;
    UUID layerId = insertLayer(name, shape == 5 ? "GLOBAL" : "PRIVATE");
    switch (shape) {
      case 0 -> insertLayerMember(layerId, CALLER_ID, "EDITOR");
      case 1 -> insertGroupLayerMember(callerUserGroupId, layerId, "MEMBER");
      case 2 -> insertLayerGroupLayer(directLayerGroupId, layerId, "EDITOR");
      case 3 -> insertLayerGroupLayer(transitiveLayerGroupId, layerId, "MEMBER");
      case 4 -> insertLayerGroupLayer(globalLayerGroupId, layerId, "EDITOR");
      case 5 -> {} // GLOBAL access scope alone implies MEMBER.
      default -> throw new IllegalStateException("unexpected shape " + shape);
    }
    return layerId;
  }

  private void seedCallerGroups() {
    callerUserGroupId = insertUserGroup("probe-user-group");
    insertUserGroupMember(callerUserGroupId, CALLER_ID);
    directLayerGroupId = insertLayerGroup("probe-direct-layer-group", "PRIVATE");
    insertLayerGroupMember(directLayerGroupId, CALLER_ID);
    transitiveLayerGroupId = insertLayerGroup("probe-transitive-layer-group", "PRIVATE");
    insertLayerGroupUserGroup(transitiveLayerGroupId, callerUserGroupId);
    globalLayerGroupId = insertLayerGroup("probe-global-layer-group", "GLOBAL");
  }

  private void resetAll() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    jdbcTemplate.update("DELETE FROM iam.layer_group_layer");
    jdbcTemplate.update("DELETE FROM iam.layer_group_member");
    jdbcTemplate.update("DELETE FROM iam.layer_group_user_group");
    jdbcTemplate.update("DELETE FROM iam.layer_group");
    jdbcTemplate.update("DELETE FROM iam.group_layer_member");
    jdbcTemplate.update("DELETE FROM iam.user_group_member");
    jdbcTemplate.update("DELETE FROM iam.user_group");
    jdbcTemplate.update("DELETE FROM iam.server_layer_access");
    jdbcTemplate.update("DELETE FROM iam.layer_member");
    jdbcTemplate.update("DELETE FROM iam.layer");
  }

  private UUID insertWatchbox(String name, List<UUID> layerIds) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox (id, name, direction, polygon_json)
        VALUES (?, ?, 'BOTH', '{"type":"Polygon","coordinates":[[[0,0],[0,1],[1,1],[1,0],[0,0]]]}'::jsonb)
        """,
        id,
        name);
    for (UUID layerId : layerIds) {
      jdbcTemplate.update(
          "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)", id, layerId);
    }
    return id;
  }

  private UUID insertLayer(String name, String accessScope) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer (id, name, access_scope) VALUES (?, ?, CAST(? AS iam.layer_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerMember(UUID layerId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_member (layer_id, user_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerId,
        userId,
        role);
  }

  private UUID insertUserGroup(String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO iam.user_group (id, name) VALUES (?, ?)", id, name);
    return id;
  }

  private void insertUserGroupMember(UUID groupId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)", groupId, userId);
  }

  private void insertGroupLayerMember(UUID groupId, UUID layerId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.group_layer_member (group_id, layer_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        groupId,
        layerId,
        role);
  }

  private UUID insertLayerGroup(String name, String accessScope) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group (id, name, access_scope) VALUES (?, ?, CAST(? AS iam.layer_group_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private void insertLayerGroupMember(UUID layerGroupId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role) VALUES (?, ?, CAST('MEMBER' AS iam.layer_group_role))",
        layerGroupId,
        userId);
  }

  private void insertLayerGroupUserGroup(UUID layerGroupId, UUID userGroupId) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_user_group (layer_group_id, user_group_id, role) VALUES (?, ?, CAST('MEMBER' AS iam.layer_group_role))",
        layerGroupId,
        userGroupId);
  }

  private void insertLayerGroupLayer(UUID layerGroupId, UUID layerId, String role) {
    jdbcTemplate.update(
        "INSERT INTO iam.layer_group_layer (layer_group_id, layer_id, role) VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerGroupId,
        layerId,
        role);
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId);
  }
}
