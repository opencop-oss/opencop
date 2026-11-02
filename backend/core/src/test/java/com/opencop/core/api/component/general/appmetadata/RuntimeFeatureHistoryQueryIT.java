// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureHistoryResponse;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class RuntimeFeatureHistoryQueryIT extends AbstractWebIT {

  private static final String HISTORY_PATH = "/v1/admin/runtime-features/history";
  private static final String FEATURE_A = "query-feature-a";
  private static final String FEATURE_B = "query-feature-b";

  private static final UUID ADMIN_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655442001");
  private static final UUID ACTOR_B_ID = UUID.fromString("770e8400-e29b-41d4-a716-446655442002");

  private static final Instant TIME_09 = Instant.parse("2026-08-19T09:00:00Z");
  private static final Instant TIME_10 = Instant.parse("2026-08-19T10:00:00Z");
  private static final Instant TIME_11 = Instant.parse("2026-08-19T11:00:00Z");
  private static final Instant TIME_12 = Instant.parse("2026-08-19T12:00:00Z");
  private static final Instant TIME_13 = Instant.parse("2026-08-19T13:00:00Z");
  private static final Instant TIED_TIME = Instant.parse("2026-08-19T14:00:00Z");
  private static final Instant TIME_15 = Instant.parse("2026-08-19T15:00:00Z");

  private static final Authentication ADMIN_AUTH = authenticationFor(ADMIN_ID, true);
  private static final Authentication ACTOR_B_AUTH = authenticationFor(ACTOR_B_ID, false);

  @Autowired private JdbcTemplate jdbc;

  private SeededRows rows;

  @BeforeEach
  void setUp() {
    resetFixtures();
    insertUser(ADMIN_ID, true, "Query Administrator", "query.admin@example.test");
    insertUser(ACTOR_B_ID, false, "Query Actor B", "query.actor.b@example.test");
    insertFeature(FEATURE_A);
    insertFeature(FEATURE_B);
    rows = seedAuditRows();
  }

  @AfterEach
  void tearDown() {
    resetFixtures();
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
                .map(RuntimeFeatureHistoryResponse::id)
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
    var byFeature =
        query(
            get(HISTORY_PATH)
                .queryParam("featureName", FEATURE_A)
                .queryParam("page", "0")
                .queryParam("size", "2"),
            ADMIN_AUTH);
    assertPage(byFeature, 0, 2, 5, 3, rows.row8(), rows.row6());

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
                .queryParam("featureName", FEATURE_A)
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
                .queryParam("featureName", FEATURE_A)
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

    assertBadRequest(get(HISTORY_PATH).queryParam("page", "-1").queryParam("size", "25"));
    assertBadRequest(
        get(HISTORY_PATH)
            .queryParam("page", Integer.toString(Integer.MAX_VALUE))
            .queryParam("size", "2"));
    assertBadRequest(get(HISTORY_PATH).queryParam("page", "0").queryParam("size", "0"));
    assertBadRequest(get(HISTORY_PATH).queryParam("page", "0").queryParam("size", "101"));
    assertBadRequest(get(HISTORY_PATH).queryParam("featureName", " "));
    assertBadRequest(
        get(HISTORY_PATH)
            .queryParam("occurredFrom", TIME_15.toString())
            .queryParam("occurredTo", TIME_09.toString()));
    assertBadRequest(get(HISTORY_PATH).queryParam("operation", "DELETE"));
    assertBadRequest(get(HISTORY_PATH).queryParam("occurredFrom", "not-an-instant"));

    mockMvc
        .perform(get("/v1/app-metadata").with(authentication(ACTOR_B_AUTH)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get(HISTORY_PATH).with(authentication(ACTOR_B_AUTH)))
        .andExpect(status().isForbidden());
  }

  private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
    mockMvc.perform(request.with(authentication(ADMIN_AUTH))).andExpect(status().isBadRequest());
  }

  private PageResponse<RuntimeFeatureHistoryResponse> query(
      MockHttpServletRequestBuilder request, Authentication authentication) throws Exception {
    var result =
        mockMvc
            .perform(request.with(authentication(authentication)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(
        result, new TypeReference<PageResponse<RuntimeFeatureHistoryResponse>>() {});
  }

  private static void assertPage(
      PageResponse<RuntimeFeatureHistoryResponse> page,
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
        .extracting(RuntimeFeatureHistoryResponse::id)
        .containsExactly(expectedIds);
  }

  private SeededRows seedAuditRows() {
    var row1 = insertAudit(1001, FEATURE_A, "CREATE", ADMIN_ID, TIME_09);
    var row2 = insertAudit(1002, FEATURE_A, "PATCH", ADMIN_ID, TIME_10);
    var row3 = insertAudit(1003, FEATURE_B, "CREATE", ACTOR_B_ID, TIME_11);
    var row4 = insertAudit(1004, FEATURE_A, "PATCH", ACTOR_B_ID, TIME_12);
    var row5 = insertAudit(1005, FEATURE_B, "PATCH", ADMIN_ID, TIME_13);
    var row6 = insertAudit(1006, FEATURE_A, "PATCH", ADMIN_ID, TIED_TIME);
    var row7 = insertAudit(1007, FEATURE_B, "PATCH", ACTOR_B_ID, TIED_TIME);
    var row8 = insertAudit(1008, FEATURE_A, "PATCH", ACTOR_B_ID, TIME_15);
    return new SeededRows(row1, row2, row3, row4, row5, row6, row7, row8);
  }

  private long insertAudit(
      long id, String featureName, String operation, UUID actorUserId, Instant occurredAt) {
    var create = operation.equals("CREATE");
    return jdbc.queryForObject(
        """
        INSERT INTO config.runtime_feature_history (
          id, feature_name, consumer_class, operation,
          before_audience, after_audience, revision_before, revision_after, occurred_at,
          actor_user_id, actor_full_name, actor_email, actor_global_admin
        ) OVERRIDING SYSTEM VALUE
        VALUES (?, ?, 'FRONTEND_ONLY', ?, ?::jsonb, '{"mode":"NONE"}'::jsonb, ?, 0,
                ?::timestamptz, ?, ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        id,
        featureName,
        operation,
        create ? null : "{\"mode\":\"NONE\"}",
        create ? null : 0L,
        occurredAt.toString(),
        actorUserId,
        actorUserId.equals(ADMIN_ID) ? "Query Administrator" : "Query Actor B",
        actorUserId.equals(ADMIN_ID) ? "query.admin@example.test" : "query.actor.b@example.test",
        actorUserId.equals(ADMIN_ID));
  }

  private void insertFeature(String name) {
    jdbc.update(
        "INSERT INTO config.runtime_feature (name, consumer_class, audience_mode, revision)"
            + " VALUES (?, 'FRONTEND_ONLY', 'NONE', 0)",
        name);
  }

  private void insertUser(UUID userId, boolean globalAdmin, String displayName, String email) {
    jdbc.update(
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

  private void resetFixtures() {
    jdbc.update(
        "DELETE FROM config.runtime_feature_history WHERE feature_name IN (?, ?)",
        FEATURE_A,
        FEATURE_B);
    jdbc.update("DELETE FROM config.runtime_feature WHERE name IN (?, ?)", FEATURE_A, FEATURE_B);
    jdbc.update("DELETE FROM opencop_user WHERE id IN (?, ?)", ADMIN_ID, ACTOR_B_ID);
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
