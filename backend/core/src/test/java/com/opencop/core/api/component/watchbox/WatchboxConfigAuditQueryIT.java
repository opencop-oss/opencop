// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxConfigAuditResponse;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = WatchboxWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxConfigAuditQueryIT extends AbstractWebIT {

  private static final String HISTORY_PATH = "/v1/admin/watchboxes/config-history";

  private static final UUID ADMIN_ID = UUID.fromString("760e8400-e29b-41d4-a716-446655442001");
  private static final UUID ACTOR_B_ID = UUID.fromString("760e8400-e29b-41d4-a716-446655442002");
  private static final UUID WATCHBOX_A_ID = UUID.fromString("760e8400-e29b-41d4-a716-446655442011");
  private static final UUID WATCHBOX_B_ID = UUID.fromString("760e8400-e29b-41d4-a716-446655442012");

  private static final Instant TIME_09 = Instant.parse("2026-08-19T09:00:00Z");
  private static final Instant TIME_10 = Instant.parse("2026-08-19T10:00:00Z");
  private static final Instant TIME_11 = Instant.parse("2026-08-19T11:00:00Z");
  private static final Instant TIME_12 = Instant.parse("2026-08-19T12:00:00Z");
  private static final Instant TIME_13 = Instant.parse("2026-08-19T13:00:00Z");
  private static final Instant TIED_TIME = Instant.parse("2026-08-19T14:00:00Z");
  private static final Instant TIME_15 = Instant.parse("2026-08-19T15:00:00Z");

  private static final Authentication ADMIN_AUTH = authenticationFor(ADMIN_ID, true);
  private static final Authentication ACTOR_B_AUTH = authenticationFor(ACTOR_B_ID, false);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;

  @Autowired
  private com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository
      serverLayerRepository;

  private LayerFixtures layerFixtures;
  private UUID managedLayerId;
  private SeededRows rows;

  @BeforeEach
  void setUp() {
    layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);
    resetQueryFixtures();
    insertUser(ADMIN_ID, true, "Query Administrator", "query.admin@example.test");
    insertUser(ACTOR_B_ID, false, "Query Actor B", "query.actor.b@example.test");
    managedLayerId =
        layerFixtures.persistLayerWithMembers(
            "Query actor managed layer", Map.of(ACTOR_B_ID, LayerRole.OWNER));
    insertWatchbox(WATCHBOX_A_ID, "query-watchbox-a");
    insertWatchbox(WATCHBOX_B_ID, "query-watchbox-b");
    attachManagedLayer(WATCHBOX_A_ID);
    attachManagedLayer(WATCHBOX_B_ID);
    rows = seedAuditRows();
  }

  @AfterEach
  void tearDown() {
    resetQueryFixtures();
  }

  @Test
  void history_whenTimestampTieSpansPages_usesFixedChronologyWithoutGapsOrDuplicates()
      throws Exception {
    var page0 =
        query(
            get(HISTORY_PATH)
                .queryParam("page", "0")
                .queryParam("size", "2")
                .queryParam("sort", "id,asc"),
            ADMIN_AUTH);
    var page1 =
        query(
            get(HISTORY_PATH)
                .queryParam("page", "1")
                .queryParam("size", "2")
                .queryParam("sort", "id,asc"),
            ADMIN_AUTH);
    var page2 =
        query(get(HISTORY_PATH).queryParam("page", "2").queryParam("size", "2"), ADMIN_AUTH);
    var page3 =
        query(get(HISTORY_PATH).queryParam("page", "3").queryParam("size", "2"), ADMIN_AUTH);

    assertPage(page0, 0, 2, 8, 4, rows.row8(), rows.row7());
    assertPage(page1, 1, 2, 8, 4, rows.row6(), rows.row5());
    assertPage(page2, 2, 2, 8, 4, rows.row4(), rows.row3());
    assertPage(page3, 3, 2, 8, 4, rows.row2(), rows.row1());

    assertThat(page0.content().getLast().occurredAt()).isEqualTo(TIED_TIME);
    assertThat(page1.content().getFirst().occurredAt()).isEqualTo(TIED_TIME);
    assertThat(page0.content().getLast().id()).isGreaterThan(page1.content().getFirst().id());

    assertThat(
            List.of(page0, page1, page2, page3).stream()
                .flatMap(page -> page.content().stream())
                .map(WatchboxConfigAuditResponse::id)
                .toList())
        .containsExactly(
            rows.row8(),
            rows.row7(),
            rows.row6(),
            rows.row5(),
            rows.row4(),
            rows.row3(),
            rows.row2(),
            rows.row1())
        .doesNotHaveDuplicates();
  }

  @Test
  void history_whenFiltersAreCombined_appliesAllToContentAndTotals() throws Exception {
    var byWatchbox =
        query(
            get(HISTORY_PATH)
                .queryParam("watchboxId", WATCHBOX_A_ID.toString())
                .queryParam("page", "0")
                .queryParam("size", "2"),
            ADMIN_AUTH);
    assertPage(byWatchbox, 0, 2, 5, 3, rows.row8(), rows.row6());

    var byOperation = query(get(HISTORY_PATH).queryParam("operation", "CREATE"), ADMIN_AUTH);
    assertPage(byOperation, 0, 25, 2, 1, rows.row3(), rows.row1());

    var byActor =
        query(get(HISTORY_PATH).queryParam("actorUserId", ADMIN_ID.toString()), ADMIN_AUTH);
    assertPage(byActor, 0, 25, 4, 1, rows.row6(), rows.row5(), rows.row2(), rows.row1());

    var fromInclusive =
        query(get(HISTORY_PATH).queryParam("occurredFrom", TIED_TIME.toString()), ADMIN_AUTH);
    assertPage(fromInclusive, 0, 25, 3, 1, rows.row8(), rows.row7(), rows.row6());

    var toInclusive =
        query(get(HISTORY_PATH).queryParam("occurredTo", TIME_10.toString()), ADMIN_AUTH);
    assertPage(toInclusive, 0, 25, 2, 1, rows.row2(), rows.row1());

    var exactInstant =
        query(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", TIED_TIME.toString())
                .queryParam("occurredTo", TIED_TIME.toString()),
            ADMIN_AUTH);
    assertPage(exactInstant, 0, 25, 2, 1, rows.row7(), rows.row6());

    var conjunctionPage0 =
        query(
            get(HISTORY_PATH)
                .queryParam("watchboxId", WATCHBOX_A_ID.toString())
                .queryParam("operation", "PATCH")
                .queryParam("actorUserId", ADMIN_ID.toString())
                .queryParam("occurredFrom", TIME_10.toString())
                .queryParam("occurredTo", TIED_TIME.toString())
                .queryParam("page", "0")
                .queryParam("size", "1"),
            ADMIN_AUTH);
    var conjunctionPage1 =
        query(
            get(HISTORY_PATH)
                .queryParam("watchboxId", WATCHBOX_A_ID.toString())
                .queryParam("operation", "PATCH")
                .queryParam("actorUserId", ADMIN_ID.toString())
                .queryParam("occurredFrom", TIME_10.toString())
                .queryParam("occurredTo", TIED_TIME.toString())
                .queryParam("page", "1")
                .queryParam("size", "1"),
            ADMIN_AUTH);
    assertPage(conjunctionPage0, 0, 1, 2, 2, rows.row6());
    assertPage(conjunctionPage1, 1, 1, 2, 2, rows.row2());
  }

  @Test
  void history_whenBoundsAreInvalidOrCallerIsNotAdmin_rejectsRequest() throws Exception {
    query(get(HISTORY_PATH).queryParam("page", "0").queryParam("size", "100"), ADMIN_AUTH);

    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("page", "-1")
                .queryParam("size", "25")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("page", Integer.toString(Integer.MAX_VALUE))
                .queryParam("size", "2")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("page", "0")
                .queryParam("size", "0")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("page", "0")
                .queryParam("size", "101")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", TIME_15.toString())
                .queryParam("occurredTo", TIME_09.toString())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH).queryParam("operation", "UPDATED").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", "not-an-instant")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());

    assertThat(layerFixtures.loadMemberRole(managedLayerId, ACTOR_B_ID)).isEqualTo(LayerRole.OWNER);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchbox.watchbox_layer WHERE layer_id = ?",
                Integer.class,
                managedLayerId))
        .isEqualTo(2);
    mockMvc
        .perform(get(HISTORY_PATH).with(authentication(ACTOR_B_AUTH)))
        .andExpect(status().isForbidden());
  }

  private PageResponse<WatchboxConfigAuditResponse> query(
      MockHttpServletRequestBuilder request, Authentication authentication) throws Exception {
    var result =
        mockMvc
            .perform(request.with(authentication(authentication)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<PageResponse<WatchboxConfigAuditResponse>>() {});
  }

  private static void assertPage(
      PageResponse<WatchboxConfigAuditResponse> page,
      int expectedPage,
      int expectedSize,
      long expectedTotalElements,
      int expectedTotalPages,
      Long... expectedIds) {
    assertThat(page.page()).isEqualTo(expectedPage);
    assertThat(page.size()).isEqualTo(expectedSize);
    assertThat(page.totalElements()).isEqualTo(expectedTotalElements);
    assertThat(page.totalPages()).isEqualTo(expectedTotalPages);
    assertThat(page.content())
        .extracting(WatchboxConfigAuditResponse::id)
        .containsExactly(expectedIds);
  }

  private SeededRows seedAuditRows() {
    var row1 = insertAudit(1001, WATCHBOX_A_ID, "CREATE", ADMIN_ID, TIME_09);
    var row2 = insertAudit(1002, WATCHBOX_A_ID, "PATCH", ADMIN_ID, TIME_10);
    var row3 = insertAudit(1003, WATCHBOX_B_ID, "CREATE", ACTOR_B_ID, TIME_11);
    var row4 = insertAudit(1004, WATCHBOX_A_ID, "PATCH", ACTOR_B_ID, TIME_12);
    var row5 = insertAudit(1005, WATCHBOX_B_ID, "ARCHIVE", ADMIN_ID, TIME_13);
    var row6 = insertAudit(1006, WATCHBOX_A_ID, "PATCH", ADMIN_ID, TIED_TIME);
    var row7 = insertAudit(1007, WATCHBOX_B_ID, "PATCH", ACTOR_B_ID, TIED_TIME);
    var row8 = insertAudit(1008, WATCHBOX_A_ID, "ARCHIVE", ACTOR_B_ID, TIME_15);
    return new SeededRows(row1, row2, row3, row4, row5, row6, row7, row8);
  }

  private long insertAudit(
      long id, UUID watchboxId, String operation, UUID actorUserId, Instant occurredAt) {
    var snapshot = snapshot(watchboxId, managedLayerId);
    var beforeSnapshot = operation.equals("CREATE") ? null : snapshot;
    var afterSnapshot = operation.equals("ARCHIVE") ? null : snapshot;
    var configEpochBefore = operation.equals("CREATE") ? null : 1L;
    var configEpochAfter = operation.equals("ARCHIVE") ? null : 1L;
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO watchbox.watchbox_config_audit (
          id,
          watchbox_id,
          operation,
          before_snapshot,
          after_snapshot,
          reset_reasons,
          config_epoch_before,
          config_epoch_after,
          occurred_at,
          actor_user_id,
          actor_full_name,
          actor_email,
          actor_global_admin
        ) OVERRIDING SYSTEM VALUE
        VALUES (?, ?, ?, ?::jsonb, ?::jsonb, '[]'::jsonb, ?, ?, ?::timestamptz, ?, ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        id,
        watchboxId,
        operation,
        beforeSnapshot,
        afterSnapshot,
        configEpochBefore,
        configEpochAfter,
        occurredAt.toString(),
        actorUserId,
        actorUserId.equals(ADMIN_ID) ? "Query Administrator" : "Query Actor B",
        actorUserId.equals(ADMIN_ID) ? "query.admin@example.test" : "query.actor.b@example.test",
        actorUserId.equals(ADMIN_ID));
  }

  private static String snapshot(UUID watchboxId, UUID layerId) {
    return """
        {
          "schemaVersion":1,
          "id":"%s",
          "name":"query-history-fixture",
          "description":null,
          "enabled":true,
          "direction":"BOTH",
          "polygon":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]},
          "filter":{"combine":"ALL","predicates":[]},
          "layerIds":["%s"],
          "configEpoch":1,
          "createdAt":"2026-08-19T08:00:00Z",
          "updatedAt":"2026-08-19T08:00:00Z"
        }
        """
        .formatted(watchboxId, layerId);
  }

  private void attachManagedLayer(UUID watchboxId) {
    jdbcTemplate.update(
        "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)",
        watchboxId,
        managedLayerId);
  }

  private void insertWatchbox(UUID watchboxId, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox (
          id, name, enabled, direction, polygon_json, filter_json, config_epoch
        ) VALUES (
          ?, ?, true, 'BOTH',
          '{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}'::jsonb,
          '{"combine":"ALL","predicates":[]}'::jsonb,
          1
        )
        """,
        watchboxId,
        name);
  }

  private void insertUser(UUID userId, boolean globalAdmin, String displayName, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, display_name, email,
          active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, now(), now())
        """,
        userId,
        "query-idp-user-" + userId,
        "query-user-" + userId,
        "http://localhost/query-test-issuer",
        "query-user-" + userId,
        displayName,
        email,
        globalAdmin);
  }

  private void resetQueryFixtures() {
    // Watchbox references FK-block the global layer reset, including rows other suites left.
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    if (layerFixtures != null) {
      layerFixtures.reset();
    }
    jdbcTemplate.update("DELETE FROM opencop_user WHERE id IN (?, ?)", ADMIN_ID, ACTOR_B_ID);
  }

  private static Authentication authenticationFor(UUID userId, boolean globalAdmin) {
    var principal =
        new OpenCopPrincipal(
                userId,
                "http://localhost/query-test-issuer",
                "query-idp-user-" + userId,
                "query-subject-" + userId,
                new OpenCopPrincipal.Profile("query-user-" + userId, null, null, null, null, false),
                0,
                Instant.parse("2026-08-19T08:00:00Z"))
            .withRequestCurrentGlobalAdministrator(globalAdmin);
    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }

  private record SeededRows(
      long row1, long row2, long row3, long row4, long row5, long row6, long row7, long row8) {}
}
