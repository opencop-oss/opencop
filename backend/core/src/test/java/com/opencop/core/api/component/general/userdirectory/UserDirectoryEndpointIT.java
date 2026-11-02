// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.userdirectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.business.model.userdirectory.UserDirectoryEntry;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.userdirectory.UserDirectoryEntryType;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = UserDirectoryWebTestApplication.class)
class UserDirectoryEndpointIT extends AbstractWebIT {

  private static final String ISSUER = "https://directory-it";

  /** Fixed so date-filter tests are not sensitive to when the suite runs. */
  private static final Instant DEFAULT_CREATED_AT = Instant.parse("2026-02-15T12:00:00Z");

  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin();
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM iam.user_group WHERE idp_issuer = ?", ISSUER);
    jdbcTemplate.update("DELETE FROM opencop_user WHERE idp_issuer = ?", ISSUER);
  }

  private void seedUser(String username) {
    seedUser(username, username + "@dirit.test", "First", "Last", false, true, DEFAULT_CREATED_AT);
  }

  private void seedUser(
      String username,
      String email,
      String firstName,
      String lastName,
      boolean admin,
      boolean active,
      Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email,
          first_name, last_name, display_name, administrator, active, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        "idp-" + id,
        username,
        ISSUER,
        username,
        email,
        firstName,
        lastName,
        firstName == null ? null : firstName + " " + lastName,
        admin,
        active,
        OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
  }

  private void seedGroup(String name) {
    seedGroup(name, DEFAULT_CREATED_AT);
  }

  private void seedGroup(String name, Instant createdAt) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.user_group (id, name, idp_issuer, idp_group_id, created_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        name,
        ISSUER,
        "idp-group-" + name,
        OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
  }

  private void seedSampleDirectory() {
    seedUser("dirit_u_alice");
    seedUser("dirit_u_bob");
    seedGroup("dirit_g_engineering");
    seedGroup("dirit_g_operations");
  }

  @Test
  void directory_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/admin/user-directory")).andExpect(status().isUnauthorized());
  }

  @Test
  void directory_forNonAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(get("/v1/admin/user-directory").with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void directory_forAdmin_returnsCombinedUsersAndGroups() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&sort=name,asc");

    assertEquals(4, page.totalElements());
    assertTrue(page.content().stream().anyMatch(e -> e.type() == UserDirectoryEntryType.USER));
    assertTrue(page.content().stream().anyMatch(e -> e.type() == UserDirectoryEntryType.GROUP));
  }

  @Test
  void directory_typeFilter_narrowsToUsers() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=type,equals,USER");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.USER));
  }

  @Test
  void directory_typeFilter_narrowsToGroups() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&sort=name,asc&filter=type,equals,GROUP");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.GROUP));
    // Group rows carry the group name and no user-only attributes.
    assertEquals("dirit_g_engineering", page.content().getFirst().name());
    assertTrue(page.content().stream().allMatch(e -> e.admin() == null));
  }

  @Test
  void directory_filterByType_notEqualsNarrowsToTheOtherKind() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=type,notEquals,GROUP");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.USER));
  }

  @Test
  void directory_typeIsNotACoarseRequestParam() throws Exception {
    // `type` and `active` were removed as standalone params; narrowing goes through the filter
    // contract. An unknown param is ignored by Spring, so the guard is that it does not narrow.
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&type=USER&active=true");

    assertEquals(4, page.totalElements());
  }

  @Test
  void directory_paginatesAcrossTheUnion() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=2&sort=name,asc");

    assertEquals(4, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(2, page.size());
    assertEquals(2, page.content().size());
  }

  @Test
  void directory_treatsLikeWildcardsLiterally() throws Exception {
    seedUser("dirit_wild_one");
    seedUser("dirit_wildxone");

    // The `_` must match a literal underscore, not act as a single-char wildcard.
    var page = queryAsAdmin("search=dirit_wild_o&page=0&size=10&sort=name,asc");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_wild_one", page.content().getFirst().name());
  }

  @Test
  void directory_appliesDefaultNameOrderWhenNoSortGiven() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10");

    assertEquals(
        List.of("dirit_g_engineering", "dirit_g_operations", "dirit_u_alice", "dirit_u_bob"),
        page.content().stream().map(UserDirectoryEntry::name).toList());
  }

  @Test
  void directory_paginationIsDisjointAndCompleteWhenNamesTie() throws Exception {
    seedUser("dirit_tie");
    seedGroup("dirit_tie");
    seedUser("dirit_alpha");
    seedGroup("dirit_omega");

    var probe = queryAsAdmin("search=dirit_&page=0&size=2&sort=name,asc");
    assertEquals(4, probe.totalElements());
    assertEquals(2, probe.totalPages());

    List<UUID> ids = new ArrayList<>();
    for (int p = 0; p < probe.totalPages(); p++) {
      var page = queryAsAdmin("search=dirit_&page=" + p + "&size=2&sort=name,asc");
      page.content().forEach(e -> ids.add(e.id()));
    }

    assertEquals(4, ids.size());
    assertEquals(4, Set.copyOf(ids).size());
  }

  // --- Free-text search is substring, not prefix -------------------------------------------------

  @Test
  void directory_search_matchesSubstringNotJustPrefix() throws Exception {
    seedUser("dirit_aaron.wyman");
    seedUser("dirit_abbie.crona");

    // The whole point of the change: an admin typing a surname finds the row.
    var page = queryAsAdmin("search=wyman&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_aaron.wyman", page.content().getFirst().name());
  }

  @Test
  void directory_search_matchesSubstringOfEmail() throws Exception {
    seedUser(
        "dirit_carol", "carol@substr.test", "Carol", "Danvers", false, true, DEFAULT_CREATED_AT);

    var page = queryAsAdmin("search=substr&page=0&size=10");

    assertEquals(1, page.totalElements());
  }

  // --- Per-column filters: happy path ------------------------------------------------------------

  @Test
  void directory_filterByType_narrowsToGroups() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=type,equals,GROUP");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.GROUP));
  }

  @Test
  void directory_singleFilterParamIsNotCommaSplit() throws Exception {
    // Regression guard: a @RequestParam List<String> binding would shred this lone param into
    // three tokens and 400. Exactly one filter is the common case, so it must round-trip.
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=name,contains,alice");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_u_alice", page.content().getFirst().name());
  }

  @Test
  void directory_filterByEmail_containsMatchesMidString() throws Exception {
    seedUser(
        "dirit_dana",
        "dana.prosacco@dirit.test",
        "Dana",
        "Prosacco",
        false,
        true,
        DEFAULT_CREATED_AT);
    seedUser(
        "dirit_erin", "erin.smith@dirit.test", "Erin", "Smith", false, true, DEFAULT_CREATED_AT);

    var page = queryAsAdmin("page=0&size=10&filter=email,contains,prosacco");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_dana", page.content().getFirst().name());
  }

  @Test
  void directory_filterByEmail_isCaseInsensitive() throws Exception {
    seedUser("dirit_frank", "Frank@DirIt.Test", "Frank", "Castle", false, true, DEFAULT_CREATED_AT);

    var page = queryAsAdmin("page=0&size=10&filter=email,contains,FRANK@dirit");

    assertEquals(1, page.totalElements());
  }

  @Test
  void directory_filterByName_startsWithMatchesPrefixOnly() throws Exception {
    seedSampleDirectory();

    // Anchored at the start, so a mid-string occurrence must not match — which is what separates
    // this from `contains`.
    var page = queryAsAdmin("page=0&size=10&filter=name,startsWith,dirit_u_a");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_u_alice", page.content().getFirst().name());
  }

  @Test
  void directory_filterByName_startsWithDoesNotMatchMidString() throws Exception {
    seedUser("dirit_alpha_tail");

    var page = queryAsAdmin("page=0&size=10&filter=name,startsWith,alpha");

    assertEquals(0, page.totalElements());
  }

  @Test
  void directory_filterByActive_isTrueKeepsGroups() throws Exception {
    // Unlike `admin` (NULL for groups, so admin,isTrue drops them), the union view hardcodes
    // active = TRUE for group rows — so an active filter keeps them. Pinning the asymmetry.
    //
    // Deliberate, and the grid shows "-" in the Status cell for those rows. Deactivation is a
    // user-account concept with no group equivalent, so a group is never inactive; hiding groups
    // from an Active filter would make them look deleted. Reconsider only alongside the grid,
    // since the two halves have to agree.
    seedUser("dirit_on", "on@dirit.test", "On", "Line", false, true, DEFAULT_CREATED_AT);
    seedUser("dirit_off", "off@dirit.test", "Off", "Line", false, false, DEFAULT_CREATED_AT);
    seedGroup("dirit_g_ops");

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=active,isTrue");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().anyMatch(e -> e.type() == UserDirectoryEntryType.GROUP));
  }

  @Test
  void directory_filterByAdmin_isFalseExcludesGroups() throws Exception {
    seedUser("dirit_admin", "admin@dirit.test", "Ada", "Root", true, true, DEFAULT_CREATED_AT);
    seedUser("dirit_plain", "plain@dirit.test", "Pat", "User", false, true, DEFAULT_CREATED_AT);
    seedGroup("dirit_g_ops");

    // admin is NULL for groups, so three-valued logic drops them from both directions.
    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=admin,isFalse");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_plain", page.content().getFirst().name());
  }

  @Test
  void directory_filterByEmail_notContainsExcludesGroupsViaNullSemantics() throws Exception {
    seedSampleDirectory();

    // NOT LIKE against a NULL email is unknown, not true — so groups drop out rather than matching
    // "has no email containing x", which is the surprising-but-correct reading.
    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=email,notContains,zzz");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.USER));
  }

  @Test
  void directory_filterByName_equalsIsCaseInsensitive() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("page=0&size=10&filter=name,equals,DIRIT_U_ALICE");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_u_alice", page.content().getFirst().name());
  }

  @Test
  void directory_filterByEmail_treatsLikeWildcardsLiterally() throws Exception {
    seedUser("dirit_wild", "a_b@dirit.test", "Wild", "Card", false, true, DEFAULT_CREATED_AT);
    seedUser("dirit_tame", "axb@dirit.test", "Tame", "Card", false, true, DEFAULT_CREATED_AT);

    // `_` must match a literal underscore, not act as a single-character wildcard.
    var page = queryAsAdmin("page=0&size=10&filter=email,contains,a_b@");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_wild", page.content().getFirst().name());
  }

  @Test
  void directory_filterByAdmin_returnsOnlyAdminsAndDropsGroups() throws Exception {
    seedUser("dirit_admin", "admin@dirit.test", "Ada", "Root", true, true, DEFAULT_CREATED_AT);
    seedUser("dirit_plain", "plain@dirit.test", "Pat", "User", false, true, DEFAULT_CREATED_AT);
    seedGroup("dirit_g_ops");

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=admin,isTrue");

    assertEquals(1, page.totalElements());
    assertEquals(UserDirectoryEntryType.USER, page.content().getFirst().type());
    assertEquals("dirit_admin", page.content().getFirst().name());
  }

  @Test
  void directory_filterByAdmin_equalsTrueMatchesIsTrue() throws Exception {
    // The web serializer sends `admin,equals,true` so the chip can read "Role: Equals Admin".
    seedUser("dirit_admin", "admin@dirit.test", "Ada", "Root", true, true, DEFAULT_CREATED_AT);
    seedUser("dirit_plain", "plain@dirit.test", "Pat", "User", false, true, DEFAULT_CREATED_AT);

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=admin,equals,true");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_admin", page.content().getFirst().name());
  }

  @Test
  void directory_filterByActive_falseExcludesGroups() throws Exception {
    seedUser("dirit_off", "off@dirit.test", "Off", "Line", false, false, DEFAULT_CREATED_AT);
    seedUser("dirit_on", "on@dirit.test", "On", "Line", false, true, DEFAULT_CREATED_AT);
    seedGroup("dirit_g_ops");

    // Group rows are hard-coded active in the union view, so they cannot match.
    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=active,equals,false");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_off", page.content().getFirst().name());
  }

  @Test
  void directory_filterOnUserOnlyColumn_dropsGroupRowsSilently() throws Exception {
    seedSampleDirectory();

    // Groups have no email, so a user-only filter excludes them through SQL NULL semantics.
    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=email,contains,dirit");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.USER));
  }

  @Test
  void directory_filterByEmail_isEmptyMatchesGroups() throws Exception {
    // The one operator where NULL must pass: a group genuinely has no email.
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=email,isEmpty,");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.GROUP));
  }

  @Test
  void directory_filterByEmail_isNotEmptyExcludesGroups() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=email,isNotEmpty,");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == UserDirectoryEntryType.USER));
  }

  @Test
  void directory_filterByName_notContainsExcludesMatches() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=name,notContains,alice");

    assertEquals(3, page.totalElements());
    assertTrue(page.content().stream().noneMatch(e -> e.name().contains("alice")));
  }

  @Test
  void directory_multipleCriteriaAndTogether() throws Exception {
    seedSampleDirectory();

    var page =
        queryAsAdmin(
            "search=dirit_&page=0&size=10&filter=type,equals,USER&filter=name,contains,alice");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_u_alice", page.content().getFirst().name());
  }

  @Test
  void directory_twoCriteriaOnTheSameFieldAndTogether() throws Exception {
    seedSampleDirectory();

    // Narrowing, not widening: names containing "dirit_u_" that are not exactly alice.
    var page =
        queryAsAdmin(
            "page=0&size=10&filter=name,contains,dirit_u_&filter=name,notEquals,dirit_u_alice");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_u_bob", page.content().getFirst().name());
  }

  @Test
  void directory_filterValueMayContainCommas() throws Exception {
    seedUser("dirit_doe", "doe@dirit.test", "Doe", "Doe,John", false, true, DEFAULT_CREATED_AT);
    seedUser("dirit_roe", "roe@dirit.test", "Roe", "Jane", false, true, DEFAULT_CREATED_AT);

    // The wire split is limited to 3 tokens, so the value keeps its own comma rather than being
    // read as a fourth token. (No space in the value — MockMvc leaves %20 undecoded, which would
    // make this assert the encoding rather than the split.)
    var page = queryAsAdmin("page=0&size=10&filter=lastName,equals,Doe,John");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_doe", page.content().getFirst().name());
  }

  @Test
  void directory_filterByCreatedAt_beforeUsesUtcDayBoundary() throws Exception {
    seedUser(
        "dirit_early",
        "e@dirit.test",
        "E",
        "E",
        false,
        true,
        Instant.parse("2026-01-01T23:59:00Z"));
    seedUser(
        "dirit_late", "l@dirit.test", "L", "L", false, true, Instant.parse("2026-01-02T00:00:00Z"));

    // "before 2026-01-02" excludes the whole of the 2nd.
    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=createdAt,before,2026-01-02");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_early", page.content().getFirst().name());
  }

  @Test
  void directory_filterByCreatedAt_afterExcludesTheNamedDay() throws Exception {
    seedUser(
        "dirit_onday",
        "o@dirit.test",
        "O",
        "O",
        false,
        true,
        Instant.parse("2026-01-02T10:00:00Z"));
    seedUser(
        "dirit_next", "n@dirit.test", "N", "N", false, true, Instant.parse("2026-01-03T10:00:00Z"));

    var page = queryAsAdmin("search=dirit_&page=0&size=10&filter=createdAt,after,2026-01-02");

    assertEquals(1, page.totalElements());
    assertEquals("dirit_next", page.content().getFirst().name());
  }

  @Test
  void directory_filterByCreatedAt_betweenIncludesBothEndpointDays() throws Exception {
    seedUser(
        "dirit_a", "a@dirit.test", "A", "A", false, true, Instant.parse("2025-12-31T23:00:00Z"));
    seedUser(
        "dirit_b", "b@dirit.test", "B", "B", false, true, Instant.parse("2026-01-01T00:30:00Z"));
    seedUser(
        "dirit_c", "c@dirit.test", "C", "C", false, true, Instant.parse("2026-01-03T23:30:00Z"));
    seedUser(
        "dirit_d", "d@dirit.test", "D", "D", false, true, Instant.parse("2026-01-04T00:30:00Z"));

    var page =
        queryAsAdmin(
            "search=dirit_&page=0&size=10&filter=createdAt,between,2026-01-01..2026-01-03");

    assertEquals(2, page.totalElements());
    assertEquals(
        List.of("dirit_b", "dirit_c"),
        page.content().stream().map(UserDirectoryEntry::name).toList());
  }

  @Test
  void directory_filterMatchingNothing_returnsEmptyPageNotError() throws Exception {
    seedSampleDirectory();

    var page = queryAsAdmin("page=0&size=10&filter=email,contains,zzzznomatch");

    assertEquals(0, page.totalElements());
    assertTrue(page.content().isEmpty());
  }

  @Test
  void directory_filteredPaginationIsDisjointAndComplete() throws Exception {
    for (int i = 0; i < 5; i++) {
      seedUser(
          "dirit_match_" + i, "m" + i + "@match.test", "M", "M", false, true, DEFAULT_CREATED_AT);
    }
    for (int i = 0; i < 5; i++) {
      seedUser(
          "dirit_other_" + i, "o" + i + "@other.test", "O", "O", false, true, DEFAULT_CREATED_AT);
    }

    var probe = queryAsAdmin("page=0&size=2&sort=name,asc&filter=email,contains,@match.test");
    assertEquals(5, probe.totalElements());
    assertEquals(3, probe.totalPages());

    List<UUID> ids = new ArrayList<>();
    for (int p = 0; p < probe.totalPages(); p++) {
      var page =
          queryAsAdmin("page=" + p + "&size=2&sort=name,asc&filter=email,contains,@match.test");
      page.content().forEach(e -> ids.add(e.id()));
    }

    assertEquals(5, ids.size());
    assertEquals(5, Set.copyOf(ids).size());
  }

  @Test
  void directory_filterCombinesWithSearchAndSort() throws Exception {
    seedSampleDirectory();

    var page =
        queryAsAdmin("search=dirit_u_&page=0&size=10&sort=name,desc&filter=type,equals,USER");

    assertEquals(2, page.totalElements());
    assertEquals(
        List.of("dirit_u_bob", "dirit_u_alice"),
        page.content().stream().map(UserDirectoryEntry::name).toList());
  }

  // --- Per-column filters: rejected input --------------------------------------------------------

  @Test
  void directory_unknownSortField_returnsBadRequest() throws Exception {
    // Previously reached query construction and surfaced as a 500.
    queryAsAdminExpectingBadRequest("sort=notAColumn,asc");
  }

  @Test
  void directory_sortFieldOutsideTheAllowlist_returnsBadRequest() throws Exception {
    // A real entity property that the grid has no column for is still not sortable.
    queryAsAdminExpectingBadRequest("sort=displayName,asc");
  }

  @Test
  void directory_gridColumnIdIsNotASortField_returnsBadRequest() throws Exception {
    // The grid maps its own ids (rowType/isAdmin) to type/admin before sending. If that mapping is
    // ever bypassed, the request must fail loudly rather than 500.
    queryAsAdminExpectingBadRequest("sort=rowType,asc");
  }

  @Test
  void directory_allowedSortFieldsAreAccepted() throws Exception {
    seedSampleDirectory();

    for (String field :
        List.of("name", "type", "admin", "active", "firstName", "lastName", "email", "createdAt")) {
      var page = queryAsAdmin("search=dirit_&page=0&size=10&sort=" + field + ",asc");
      assertEquals(4, page.totalElements(), "sort by " + field + " should be accepted");
    }
  }

  @Test
  void directory_unknownFilterField_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=emial,equals,x");
  }

  @Test
  void directory_unknownFilterOperator_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=email,matches,x");
  }

  @Test
  void directory_displayNameIsNotAFilterableColumn_returnsBadRequest() throws Exception {
    // The grid has no display-name column, so a filter on it would be unreachable from the UI.
    // It stays matched by the free-text `search` term — see
    // directory_search_matchesSubstringOfEmail
    // and friends — just not as a per-column filter.
    queryAsAdminExpectingBadRequest("filter=displayName,equals,Doe");
  }

  @Test
  void directory_operatorTokenIsCaseSensitive_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=email,startswith,x");
  }

  @Test
  void directory_operatorNotSupportedForField_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=name,before,2026-01-01");
  }

  @Test
  void directory_containsOnANonStringField_returnsBadRequest() throws Exception {
    // Rejected loudly rather than silently returning unfiltered rows.
    queryAsAdminExpectingBadRequest("filter=type,contains,GRO");
  }

  @Test
  void directory_malformedFilterParam_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=email");
  }

  @Test
  void directory_missingValueForValueOperator_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=email,equals,");
  }

  @Test
  void directory_invalidEnumValue_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=type,equals,PERSON");
  }

  /**
   * The grid renders createdAt in the viewer's configured timezone, so a filtered day must mean
   * that viewer's day. This row displays as 2026-03-02 in Asia/Taipei (UTC+8) while sitting on
   * 2026-03-01 in UTC -- resolving the boundary in UTC would drop a row the admin can see.
   */
  @Test
  void directory_dateFilterResolvesDayBoundariesInTheRequestedZone() throws Exception {
    seedUser(
        "dirit_tz", "tz@dirit.test", "T", "Z", false, true, Instant.parse("2026-03-01T18:00:00Z"));

    var inZone =
        queryAsAdmin(
            "search=dirit_tz&page=0&size=10&timezone=Asia/Taipei"
                + "&filter=createdAt,between,2026-03-02..2026-03-02");
    assertEquals(1, inZone.totalElements());

    // Same request without a zone keeps the documented UTC default, where the row is on the 1st.
    var inUtc =
        queryAsAdmin(
            "search=dirit_tz&page=0&size=10&filter=createdAt,between,2026-03-02..2026-03-02");
    assertEquals(0, inUtc.totalElements());
  }

  @Test
  void directory_unknownTimezone_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=createdAt,before,2026-01-02&timezone=Not/AZone");
  }

  @Test
  void directory_invalidDateValue_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=createdAt,before,notadate");
  }

  @Test
  void directory_incompleteBetweenRange_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=createdAt,between,2026-01-01");
  }

  @Test
  void directory_reversedBetweenRange_returnsBadRequest() throws Exception {
    // Contradictory bounds would otherwise return an ordinary empty page, which reads as "no
    // matches" rather than "your range is backwards".
    queryAsAdminExpectingBadRequest("filter=createdAt,between,2026-03-01..2026-01-01");
  }

  @Test
  void directory_dateWhoseNextDayBoundaryOverflows_returnsBadRequest() throws Exception {
    // The predicate builder advances the exclusive upper bound a day; at LocalDate.MAX that would
    // throw past the parse-time catch and surface as a 500.
    queryAsAdminExpectingBadRequest("filter=createdAt,after,+999999999-12-31");
  }

  @Test
  void directory_tooManyFilters_returnsBadRequest() throws Exception {
    StringBuilder query = new StringBuilder("page=0&size=10");
    for (int i = 0; i <= 10; i++) {
      query.append("&filter=email,contains,v").append(i);
    }
    queryAsAdminExpectingBadRequest(query.toString());
  }

  @Test
  void directory_overlongFilterValue_returnsBadRequest() throws Exception {
    queryAsAdminExpectingBadRequest("filter=email,contains," + "x".repeat(101));
  }

  private PageResponse<UserDirectoryEntry> queryAsAdmin(String query) throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/admin/user-directory?" + query).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<PageResponse<UserDirectoryEntry>>() {});
  }

  private void queryAsAdminExpectingBadRequest(String query) throws Exception {
    mockMvc
        .perform(get("/v1/admin/user-directory?" + query).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest());
  }
}
