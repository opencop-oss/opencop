// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.general.business.model.Caller;
import com.opencop.core.api.general.business.model.audit.AuditEntry;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = AuditWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class AuditHistoryQueryIT extends AbstractWebIT {

  private static final String HISTORY_PATH = "/v1/admin/audit-history";

  private static final UUID ADMIN_ID = UUID.fromString("9a0e8400-e29b-41d4-a716-446655449001");
  private static final UUID OPERATOR_ID = UUID.fromString("9a0e8400-e29b-41d4-a716-446655449002");
  private static final UUID LAYER_A = UUID.fromString("9a0e8400-e29b-41d4-a716-44665544901a");
  private static final UUID LAYER_B = UUID.fromString("9a0e8400-e29b-41d4-a716-44665544901b");
  private static final UUID SOURCE_A = UUID.fromString("9a0e8400-e29b-41d4-a716-44665544902a");

  private static final Instant T09 = Instant.parse("2026-10-14T09:00:00Z");
  private static final Instant T10 = Instant.parse("2026-10-14T10:00:00Z");
  private static final Instant T11 = Instant.parse("2026-10-14T11:00:00Z");
  private static final Instant TIED = Instant.parse("2026-10-14T12:00:00Z");
  private static final Instant T13 = Instant.parse("2026-10-14T13:00:00Z");

  private static final Authentication ADMIN_AUTH = authenticationFor(ADMIN_ID, true);
  private static final Authentication OPERATOR_AUTH = authenticationFor(OPERATOR_ID, false);

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    reset();
    insertUser(ADMIN_ID, true, "Query Administrator", "query.admin@example.test");
    insertUser(OPERATOR_ID, false, "Query Operator", "query.operator@example.test");

    insert("LAYER", LAYER_A, "PATCH", ADMIN_ID, T09);
    insert("LAYER", LAYER_A, "MEMBERS_CHANGED", OPERATOR_ID, T10);
    insert("LAYER", LAYER_B, "PATCH", ADMIN_ID, T11);
    insert("DATA_SOURCE", SOURCE_A, "CONNECT", ADMIN_ID, TIED);
    insert("LAYER", LAYER_A, "ARCHIVE", OPERATOR_ID, TIED);
    insert("DATA_SOURCE", SOURCE_A, "DISCONNECT", OPERATOR_ID, T13);
  }

  @AfterEach
  void tearDown() {
    reset();
  }

  @Test
  void servesEverythingNewestFirstWithAnExactTotal() throws Exception {
    var page = query(get(HISTORY_PATH), ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(6);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.content()).hasSize(6);
    assertThat(page.content().getFirst().occurredAt()).isEqualTo(T13);
    assertThat(page.content().getLast().occurredAt()).isEqualTo(T09);
  }

  @Test
  void filtersByEntityTypeAndMovesTheTotalWithIt() throws Exception {
    var page = query(get(HISTORY_PATH).queryParam("entityType", "DATA_SOURCE"), ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .allSatisfy(row -> assertThat(row.entityType().name()).isEqualTo("DATA_SOURCE"));
  }

  @Test
  void filtersByEntityId() throws Exception {
    var page = query(get(HISTORY_PATH).queryParam("entityId", LAYER_A.toString()), ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(3);
    assertThat(page.content()).allSatisfy(row -> assertThat(row.entityId()).isEqualTo(LAYER_A));
  }

  @Test
  void filtersByOperationAndActorTogether() throws Exception {
    var page =
        query(
            get(HISTORY_PATH)
                .queryParam("operation", "PATCH")
                .queryParam("actorUserId", ADMIN_ID.toString()),
            ADMIN_AUTH);

    // Both PATCH rows belong to the admin, so the conjunction keeps them and drops the rest.
    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content())
        .allSatisfy(
            row -> {
              assertThat(row.operation().name()).isEqualTo("PATCH");
              assertThat(row.actor().userId()).isEqualTo(ADMIN_ID);
            });
  }

  @Test
  void andsEveryFilterRatherThanOringThem() throws Exception {
    var page =
        query(
            get(HISTORY_PATH)
                .queryParam("entityType", "LAYER")
                .queryParam("entityId", LAYER_A.toString())
                .queryParam("operation", "PATCH")
                .queryParam("actorUserId", ADMIN_ID.toString()),
            ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.content().getFirst().occurredAt()).isEqualTo(T09);
  }

  @Test
  void treatsBothTimeBoundsAsInclusive() throws Exception {
    var page =
        query(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", T10.toString())
                .queryParam("occurredTo", T11.toString()),
            ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(2);
    assertThat(page.content()).extracting(HistoryRow::occurredAt).containsExactly(T11, T10);
  }

  @Test
  void returnsTheSingleRowWhenBothBoundsAreTheSameMoment() throws Exception {
    var page =
        query(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", T09.toString())
                .queryParam("occurredTo", T09.toString()),
            ADMIN_AUTH);

    assertThat(page.totalElements()).isEqualTo(1);
    assertThat(page.content().getFirst().occurredAt()).isEqualTo(T09);
  }

  @Test
  void pagesThroughTiedTimestampsWithoutGapsOrDuplicates() throws Exception {
    var page0 =
        query(get(HISTORY_PATH).queryParam("page", "0").queryParam("size", "2"), ADMIN_AUTH);
    var page1 =
        query(get(HISTORY_PATH).queryParam("page", "1").queryParam("size", "2"), ADMIN_AUTH);
    var page2 =
        query(get(HISTORY_PATH).queryParam("page", "2").queryParam("size", "2"), ADMIN_AUTH);

    var ids =
        List.of(page0, page1, page2).stream()
            .flatMap(page -> page.content().stream())
            .map(HistoryRow::id)
            .toList();

    // Two rows share TIED; without id as a tiebreak one of them can repeat across pages while
    // the other never appears.
    assertThat(ids).doesNotHaveDuplicates().hasSize(6);
    assertThat(page0.totalPages()).isEqualTo(3);
  }

  @Test
  void ordersTiedTimestampsNewestInsertFirst() throws Exception {
    var tied =
        query(get(HISTORY_PATH).queryParam("size", "100"), ADMIN_AUTH).content().stream()
            .filter(row -> TIED.equals(row.occurredAt()))
            .toList();

    // ARCHIVE was inserted after CONNECT, so the id tiebreak puts it first.
    assertThat(tied)
        .extracting(row -> row.operation().name())
        .containsExactly("ARCHIVE", "CONNECT");
  }

  @Test
  void answersAPastTheEndPageWithNoRowsAndTheRealTotal() throws Exception {
    var page =
        query(get(HISTORY_PATH).queryParam("page", "50").queryParam("size", "2"), ADMIN_AUTH);

    assertThat(page.content()).isEmpty();
    assertThat(page.totalElements()).isEqualTo(6);
  }

  @Test
  void rejectsAnUnknownEntityTypeAndOperation() throws Exception {
    mockMvc
        .perform(
            get(HISTORY_PATH).queryParam("entityType", "WATCHBOX").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(HISTORY_PATH).queryParam("operation", "BOGUS").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsNothingForAnOperationTheResourceCannotHave() throws Exception {
    var page =
        query(
            get(HISTORY_PATH)
                .queryParam("entityType", "DATA_SOURCE")
                .queryParam("operation", "MERGED_FROM"),
            ADMIN_AUTH);

    assertThat(page.content()).isEmpty();
  }

  @Test
  void refusesAnUnauthenticatedCaller() throws Exception {
    mockMvc.perform(get(HISTORY_PATH)).andExpect(status().is4xxClientError());
  }

  @Test
  void ignoresCallerSuppliedSorting() throws Exception {
    var page = query(get(HISTORY_PATH).queryParam("sort", "occurredAt,asc"), ADMIN_AUTH);

    // Chronology is fixed by the endpoint; a sort parameter is not part of the contract.
    assertThat(page.content().getFirst().occurredAt()).isEqualTo(T13);
  }

  @Test
  void refusesANonAdministrator() throws Exception {
    mockMvc
        .perform(get(HISTORY_PATH).with(authentication(OPERATOR_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsAnInvertedRange() throws Exception {
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("occurredFrom", T13.toString())
                .queryParam("occurredTo", T09.toString())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  /**
   * Spring Data computes the offset as an int, so without a guard this is an {@code
   * InvalidDataAccessApiUsageException} from inside the repository and a 500 to the caller.
   */
  @Test
  void rejectsAPagePastTheSupportedOffsetRange() throws Exception {
    mockMvc
        .perform(
            get(HISTORY_PATH)
                .queryParam("page", String.valueOf(Integer.MAX_VALUE))
                .queryParam("size", "100")
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsASizeAboveTheCap() throws Exception {
    mockMvc
        .perform(get(HISTORY_PATH).queryParam("size", "101").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void servesActorFactsAndRawChangeAsStored() throws Exception {
    var page = query(get(HISTORY_PATH).queryParam("entityId", LAYER_B.toString()), ADMIN_AUTH);

    var row = page.content().getFirst();
    assertThat(row.actor().userId()).isEqualTo(ADMIN_ID);
    assertThat(row.actor().fullName()).isEqualTo("Query Administrator");
    assertThat(row.actor().email()).isEqualTo("query.admin@example.test");
    assertThat(row.actor().globalAdmin()).isTrue();
    assertThat(row.entityName()).isEqualTo("fixture name");
    // Served as a JSON object, not as an escaped string a client would have to parse again.
    assertThat(row.change().isObject()).isTrue();
    assertThat(row.change().get("changed").isArray()).isTrue();
    assertThat(row.changeVersion()).isEqualTo(1);
  }

  private PageResponse<HistoryRow> query(MockHttpServletRequestBuilder request, Authentication auth)
      throws Exception {
    var result =
        mockMvc.perform(request.with(authentication(auth))).andExpect(status().isOk()).andReturn();
    return readResponse(result, new TypeReference<PageResponse<HistoryRow>>() {});
  }

  /**
   * The response as a client sees it, with {@code change} read as the JSON object the endpoint
   * serves rather than as the string the server happens to hold it in.
   */
  private record HistoryRow(
      long id,
      AuditEntry.EntityType entityType,
      UUID entityId,
      String entityName,
      AuditEntry.Operation operation,
      Instant occurredAt,
      Caller actor,
      JsonNode change,
      int changeVersion) {}

  private void insert(
      String entityType, UUID entityId, String operation, UUID actorUserId, Instant occurredAt) {
    var admin = actorUserId.equals(ADMIN_ID);
    jdbcTemplate.update(
        """
        INSERT INTO audit.audit_history (
          entity_type, entity_id, entity_name, operation, occurred_at,
          actor_user_id, actor_full_name, actor_email, actor_global_admin,
          change, change_version)
        VALUES (?, ?, 'fixture name', ?, ?, ?, ?, ?, ?, '{"changed":[]}'::jsonb, 1)
        """,
        entityType,
        entityId,
        operation,
        java.sql.Timestamp.from(occurredAt),
        actorUserId,
        admin ? "Query Administrator" : "Query Operator",
        admin ? "query.admin@example.test" : "query.operator@example.test",
        admin);
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
        "audit-idp-user-" + userId,
        "audit-user-" + userId,
        "http://localhost/audit-test-issuer",
        "audit-user-" + userId,
        displayName,
        email,
        globalAdmin);
  }

  private void reset() {
    // Opts in to the append-only trigger for this one statement's transaction.
    jdbcTemplate.update(
        "DELETE FROM audit.audit_history WHERE set_config('audit.maintenance', 'on', true) = 'on'");
    jdbcTemplate.update("DELETE FROM opencop_user WHERE id IN (?, ?)", ADMIN_ID, OPERATOR_ID);
  }

  private static Authentication authenticationFor(UUID userId, boolean globalAdmin) {
    var principal =
        new OpenCopPrincipal(
                userId,
                "http://localhost/audit-test-issuer",
                "audit-idp-user-" + userId,
                "audit-subject-" + userId,
                new OpenCopPrincipal.Profile("audit-user-" + userId, null, null, null, null, false),
                0,
                Instant.parse("2026-10-14T08:00:00Z"))
            .withRequestCurrentGlobalAdministrator(globalAdmin);
    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }
}
