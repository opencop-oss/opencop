// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.JdbcStatementCounter;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Statement-budget regression guard for the watchbox read paths. List cost is flat per request —
 * one heartbeat read, the active-watchbox reads, one bulk role read, one bulk source-status read,
 * and (for manager rows) one source-name lookup — independent of watchbox count, layers per
 * watchbox, and visible-row count. The scale matrix seeds the caller through every membership shape
 * the reachability model supports and asserts that flatness directly; the manager profile carries
 * its own budget because the source-name family only executes for manager-visible rows. Get stays
 * per-watchbox and carries per-profile budgets kept below measured + layers so a reintroduced
 * per-layer read cannot fit.
 *
 * <p>Every fifth watchbox carries one unreachable layer so the all-layers visibility predicate is
 * exercised in both directions.
 *
 * <p>The probe tables print to stdout (redirected to the failsafe report file) as measurement
 * evidence alongside the budget assertions.
 */
@ComponentWebIT(classes = {WatchboxWebTestApplication.class, JdbcStatementCounter.Config.class})
class WatchboxListEfficiencyIT extends AbstractWebIT {

  private static final UUID CALLER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655442001");
  private static final Authentication CALLER_AUTH = TestAuthentications.oidcUser(CALLER_ID);

  private static final int MEMBERSHIP_SHAPES = 6;

  // Budgets are measured value plus 2 headroom (statement counts are stable across runs; the
  // headroom only absorbs incidental pool or plan statements). Non-manager list measured 5 flat
  // at every seeded scale; the manager list adds the one source-name lookup (6). Any per-layer,
  // per-visible-row, or per-watchbox read regression blows these flat ceilings at the larger
  // scales, and the spread assertion catches scale-dependent growth even inside the headroom.
  // Get measured 5 non-manager / 6 manager at five layers; both stay below measured + layers so
  // a reintroduced one-read-per-layer regression cannot fit.
  private static final int LIST_STATEMENT_BUDGET = 7;
  private static final int MANAGER_LIST_STATEMENT_BUDGET = 8;
  private static final int GET_STATEMENT_BUDGET = 7;
  private static final int MANAGER_GET_STATEMENT_BUDGET = 8;
  private static final int SCALE_SPREAD_TOLERANCE = 2;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;

  private UUID callerUserGroupId;
  private UUID directLayerGroupId;
  private UUID transitiveLayerGroupId;
  private UUID globalLayerGroupId;

  private record Scale(int watchboxes, int layersPerWatchbox) {}

  private record Measurement(Scale scale, long statements, long millis, int visible) {}

  @AfterEach
  void tearDown() {
    // The seeded boxes reference layers and later suites reset layers with a bare DELETE, so clear
    // them before the next suite runs.
    resetAll();
  }

  @Test
  void list_acrossSeededScales_staysWithinStatementBudget() throws Exception {
    List<Measurement> measurements = new ArrayList<>();
    for (Scale scale :
        List.of(new Scale(10, 1), new Scale(10, 5), new Scale(50, 1), new Scale(50, 5))) {
      resetAll();
      Set<UUID> expectedVisible = seed(scale);

      // Warm-up run: JPA plan caches, pooled connections, and JIT otherwise dominate run one.
      listWatchboxes();

      JdbcStatementCounter.reset();
      long startNanos = System.nanoTime();
      MvcResult result = listWatchboxes();
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
      long statements = JdbcStatementCounter.executions();

      Set<UUID> visibleIds = responseIds(result);
      assertThat(visibleIds).isEqualTo(expectedVisible);
      measurements.add(new Measurement(scale, statements, elapsedMillis, visibleIds.size()));
    }

    System.out.println("WATCHBOX_LIST_PROBE W L visible statements millis");
    for (Measurement m : measurements) {
      System.out.printf(
          "WATCHBOX_LIST_PROBE %d %d %d %d %d%n",
          m.scale().watchboxes(),
          m.scale().layersPerWatchbox(),
          m.visible(),
          m.statements(),
          m.millis());
      assertThat(m.statements())
          .as(
              "list statements at W=%d L=%d (visible=%d)",
              m.scale().watchboxes(), m.scale().layersPerWatchbox(), m.visible())
          .isPositive()
          .isLessThanOrEqualTo(LIST_STATEMENT_BUDGET);
    }
    assertScaleIndependence(measurements);
  }

  /**
   * The consolidation's core property: list cost must not grow with watchbox count or layers per
   * watchbox. The small tolerance absorbs incidental pool or plan statements without letting a
   * per-layer, per-watchbox, or per-visible-row read (which adds at least W statements between
   * these scales) regress silently.
   */
  private static void assertScaleIndependence(List<Measurement> measurements) {
    long min = measurements.stream().mapToLong(Measurement::statements).min().orElseThrow();
    long max = measurements.stream().mapToLong(Measurement::statements).max().orElseThrow();
    assertThat(max - min)
        .as("statement-count spread across all seeded scales")
        .isLessThanOrEqualTo(SCALE_SPREAD_TOLERANCE);
  }

  @Test
  void get_ofVisibleWatchbox_staysWithinStatementBudget() throws Exception {
    resetAll();
    Set<UUID> expectedVisible = seed(new Scale(10, 5));
    UUID watchboxId = expectedVisible.iterator().next();

    // Warm-up run: JPA plan caches, pooled connections, and JIT otherwise dominate run one.
    getWatchbox(watchboxId);

    JdbcStatementCounter.reset();
    getWatchbox(watchboxId);
    long statements = JdbcStatementCounter.executions();

    System.out.println("WATCHBOX_GET_PROBE statements " + statements);
    assertThat(statements).isPositive().isLessThanOrEqualTo(GET_STATEMENT_BUDGET);
  }

  @Test
  void list_managerWithSourceStatuses_staysWithinManagerBudget() throws Exception {
    resetAll();
    Set<UUID> expectedVisible = seedManaged(new Scale(10, 5));

    // Warm-up run: JPA plan caches, pooled connections, and JIT otherwise dominate run one.
    listWatchboxes();

    JdbcStatementCounter.reset();
    MvcResult result = listWatchboxes();
    long statements = JdbcStatementCounter.executions();

    Set<UUID> visibleIds = responseIds(result);
    assertThat(visibleIds).isEqualTo(expectedVisible);
    System.out.println(
        "WATCHBOX_LIST_MANAGER_PROBE visible " + visibleIds.size() + " statements " + statements);
    assertThat(statements).isPositive().isLessThanOrEqualTo(MANAGER_LIST_STATEMENT_BUDGET);
  }

  @Test
  void get_managerWithSourceStatuses_staysWithinManagerBudget() throws Exception {
    resetAll();
    Set<UUID> expectedVisible = seedManaged(new Scale(10, 5));
    UUID watchboxId = expectedVisible.iterator().next();

    // Warm-up run: JPA plan caches, pooled connections, and JIT otherwise dominate run one.
    getWatchbox(watchboxId);

    JdbcStatementCounter.reset();
    getWatchbox(watchboxId);
    long statements = JdbcStatementCounter.executions();

    System.out.println("WATCHBOX_GET_MANAGER_PROBE statements " + statements);
    assertThat(statements).isPositive().isLessThanOrEqualTo(MANAGER_GET_STATEMENT_BUDGET);
  }

  private void getWatchbox(UUID watchboxId) throws Exception {
    mockMvc
        .perform(get("/v1/watchboxes/" + watchboxId).with(authentication(CALLER_AUTH)))
        .andExpect(status().isOk());
  }

  private MvcResult listWatchboxes() throws Exception {
    return mockMvc
        .perform(get("/v1/watchboxes").with(authentication(CALLER_AUTH)))
        .andExpect(status().isOk())
        .andReturn();
  }

  private Set<UUID> responseIds(MvcResult result) throws Exception {
    JsonNode array = readResponse(result, JsonNode.class);
    Set<UUID> ids = new HashSet<>();
    array.forEach(node -> ids.add(UUID.fromString(node.get("id").asString())));
    return ids;
  }

  /**
   * Seeds the scale's watchboxes. Layer n takes membership shape n % 6: direct member,
   * user-group->layer, user->layer-group->layer, user-group->layer-group->layer, GLOBAL layer
   * group, GLOBAL layer. For every watchbox with index i % 5 == 4, the last layer is replaced with
   * an unreachable private layer, making the watchbox invisible to the caller.
   */
  private Set<UUID> seed(Scale scale) {
    insertUser(CALLER_ID);
    seedFreshDetectionHeartbeat();
    seedCallerGroups();

    Set<UUID> expectedVisible = new HashSet<>();
    int layerIndex = 0;
    for (int i = 0; i < scale.watchboxes(); i++) {
      boolean visible = i % 5 != 4;
      List<UUID> layerIds = new ArrayList<>();
      for (int j = 0; j < scale.layersPerWatchbox(); j++) {
        boolean unreachable = !visible && j == scale.layersPerWatchbox() - 1;
        layerIds.add(
            unreachable
                ? insertLayer("unreachable-" + i, "PRIVATE")
                : insertReachableLayer(layerIndex++));
      }
      UUID watchboxId = insertWatchbox("wb-" + i, layerIds);
      if (visible) {
        expectedVisible.add(watchboxId);
      }
    }
    return expectedVisible;
  }

  /**
   * Seeds like {@link #seed} but with the caller holding direct OWNER on every reachable layer and
   * one source-status row per watchbox, so the measured window includes the manager-only status and
   * source-name statement families. Membership-shape variety stays with {@link #seed}; this profile
   * targets the assembly statement families, not role-read consolidation.
   */
  private Set<UUID> seedManaged(Scale scale) {
    insertUser(CALLER_ID);
    seedFreshDetectionHeartbeat();
    UUID sourceId = insertMinimalDataSource();

    Set<UUID> expectedVisible = new HashSet<>();
    for (int i = 0; i < scale.watchboxes(); i++) {
      boolean visible = i % 5 != 4;
      List<UUID> layerIds = new ArrayList<>();
      for (int j = 0; j < scale.layersPerWatchbox(); j++) {
        boolean unreachable = !visible && j == scale.layersPerWatchbox() - 1;
        UUID layerId = insertLayer("managed-" + i + "-" + j, "PRIVATE");
        if (!unreachable) {
          insertLayerMember(layerId, CALLER_ID, "OWNER");
        }
        layerIds.add(layerId);
      }
      UUID watchboxId = insertWatchbox("managed-wb-" + i, layerIds);
      insertSourceStatus(watchboxId, sourceId);
      if (visible) {
        expectedVisible.add(watchboxId);
      }
    }
    return expectedVisible;
  }

  private void insertSourceStatus(UUID watchboxId, UUID sourceId) {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_source_status (
            watchbox_id, config_epoch, source_id, status, reason_code, observed_count, cap
        ) VALUES (?, 1, ?, 'DEGRADED', 'WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN', 1, 1000)
        """,
        watchboxId,
        sourceId);
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

  private void seedFreshDetectionHeartbeat() {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox_detection_heartbeat
          (instance_id, listener_started_at, last_heartbeat_at, heartbeat_expires_at,
           assigned_partitions, heartbeat_sequence)
        VALUES ('processor-probe', now(), now(), now() + INTERVAL '60 seconds', 2, 1)
        ON CONFLICT (instance_id) DO UPDATE SET
          heartbeat_expires_at = now() + INTERVAL '60 seconds',
          stopped_at = NULL
        """);
  }
}
