// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.layerdirectory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryEntry;
import com.opencop.core.sdk.model.iam.layer.LayerDirectoryType;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

/**
 * Component IT for {@code GET /v1/admin/layer-directory} — the combined layers+layer-groups grid.
 * Validates the Hibernate {@code @Subselect} UNION over {@code iam.layer} + {@code iam.layer_group}
 * against a real (embedded) Postgres, plus {@code LayerDirectorySpecifications} filtering and admin
 * gating.
 */
@ComponentWebIT(classes = LayerDirectoryWebTestApplication.class)
class LayerDirectoryEndpointIT extends AbstractWebIT {

  private static final String MARKER = "ldit_";
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin();
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String ISSUER = "https://ldit";

  @BeforeEach
  void setUp() {
    // Layers reference the archiver via a FK, so delete them before the users.
    jdbcTemplate.update("DELETE FROM iam.layer WHERE name LIKE ?", MARKER + "%");
    jdbcTemplate.update("DELETE FROM iam.layer_group WHERE name LIKE ?", MARKER + "%");
    jdbcTemplate.update("DELETE FROM opencop_user WHERE idp_issuer = ?", ISSUER);
  }

  private UUID seedUser() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        "idp-" + id,
        "u-" + id,
        ISSUER,
        "u-" + id,
        "u@ldit.test",
        "First",
        "Last",
        "First Last");
    return id;
  }

  /**
   * Seeds a layer. Archived layers must satisfy {@code ck_layer_lifecycle_archive_consistency}
   * (lifecycle ARCHIVED ⇔ archived_at set) and the paired archived_at/archived_by FK to a user.
   */
  private void seedLayer(String name, boolean archived, UUID archivedBy) {
    seedLayerWithLifecycle(name, archived ? "ARCHIVED" : "ACTIVE", archivedBy);
  }

  /**
   * Seeds a layer in an explicit lifecycle state. {@code ck_layer_lifecycle_archive_consistency}
   * requires archived_at (and the paired archived_by FK) to be set iff the state is {@code
   * ARCHIVED}; every other state — {@code ACTIVE} and the transitional merge states — leaves them
   * null.
   */
  private void seedLayerWithLifecycle(String name, String lifecycleState, UUID archivedBy) {
    seedLayer(name, lifecycleState, archivedBy, "PRIVATE", Instant.now());
  }

  /** Seeds a layer with an explicit access scope, so the scope filter can be exercised. */
  private void seedLayer(String name, String accessScope) {
    seedLayer(name, "ACTIVE", null, accessScope, Instant.now());
  }

  /** Seeds a layer at an explicit creation instant, so the date filters can be exercised. */
  private void seedLayer(String name, Instant createdAt) {
    seedLayer(name, "ACTIVE", null, "PRIVATE", createdAt);
  }

  private void seedLayer(
      String name, String lifecycleState, UUID archivedBy, String accessScope, Instant createdAt) {
    boolean archived = "ARCHIVED".equals(lifecycleState);
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer (
          id, name, access_scope, lifecycle_state, default_visible, created_at, archived_at, archived_by
        ) VALUES (?, ?, ?::iam.layer_access_scope, ?::iam.layer_lifecycle_state, true, ?, ?, ?)
        """,
        UUID.randomUUID(),
        name,
        accessScope,
        lifecycleState,
        Timestamp.from(createdAt),
        archived ? Timestamp.from(Instant.now()) : null,
        archived ? archivedBy : null);
  }

  private void seedGroup(String name) {
    seedGroup(name, "PRIVATE", Instant.now());
  }

  private void seedGroup(String name, String accessScope) {
    seedGroup(name, accessScope, Instant.now());
  }

  private void seedGroup(String name, Instant createdAt) {
    seedGroup(name, "PRIVATE", createdAt);
  }

  /**
   * Still writes {@code description}, which the directory read no longer projects — so a row that
   * carries one is proof the column really has left the entity rather than merely gone unasserted.
   */
  private void seedGroup(String name, String accessScope, Instant createdAt) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.layer_group (id, name, description, access_scope, created_at)
        VALUES (?, ?, ?, ?::iam.layer_group_access_scope, ?)
        """,
        UUID.randomUUID(),
        name,
        "desc of " + name,
        accessScope,
        Timestamp.from(createdAt));
  }

  /**
   * Stamps {@code updated_at} on an already-seeded layer, standing in for a real edit. Seeded rows
   * leave it null, matching a production row that has never been updated, so a null-ordering
   * assertion needs at least one row that carries a value to sort ahead of them.
   */
  private void touchLayer(String name, Instant updatedAt) {
    jdbcTemplate.update(
        "UPDATE iam.layer SET updated_at = ? WHERE name = ?", Timestamp.from(updatedAt), name);
  }

  private void seedActiveSample() {
    seedLayer("ldit_l_alpha", false, null);
    seedLayer("ldit_l_beta", false, null);
    seedGroup("ldit_g_one");
    seedGroup("ldit_g_two");
  }

  /** Four rows where a layer and a group share a name, so the primary `name` sort ties. */
  private void seedTiedSample() {
    seedLayer("ldit_dup", false, null);
    seedGroup("ldit_dup");
    seedLayer("ldit_a", false, null);
    seedGroup("ldit_z");
  }

  /** Pages through the whole result set and returns each row's {@code type:id} key, in order. */
  private ArrayList<String> collectKeysAcrossPages(String query) throws Exception {
    int totalPages = queryAsAdmin(query + "&page=0").totalPages();
    var keys = new ArrayList<String>();
    for (int p = 0; p < totalPages; p++) {
      queryAsAdmin(query + "&page=" + p).content().forEach(e -> keys.add(e.type() + ":" + e.id()));
    }
    return keys;
  }

  @Test
  void layerDirectory_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/admin/layer-directory")).andExpect(status().isUnauthorized());
  }

  @Test
  void layerDirectory_forNonAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(get("/v1/admin/layer-directory").with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void layerDirectory_forAdmin_returnsCombinedLayersAndGroups() throws Exception {
    seedActiveSample();

    var page = queryAsAdmin("search=ldit_&page=0&size=10&sort=name,asc");

    assertEquals(4, page.totalElements());
    assertTrue(page.content().stream().anyMatch(e -> e.type() == LayerDirectoryType.LAYER));
    assertTrue(page.content().stream().anyMatch(e -> e.type() == LayerDirectoryType.LAYER_GROUP));
  }

  @Test
  void layerDirectory_filterByType_narrowsToLayers() throws Exception {
    seedActiveSample();

    var page = queryAsAdmin("filter=type,equals,LAYER&search=ldit_&page=0&size=10");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == LayerDirectoryType.LAYER));
  }

  @Test
  void layerDirectory_filterByType_narrowsToGroups() throws Exception {
    seedActiveSample();

    var page =
        queryAsAdmin("filter=type,equals,LAYER_GROUP&search=ldit_&page=0&size=10&sort=name,asc");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == LayerDirectoryType.LAYER_GROUP));
    // Group rows are never archived.
    assertEquals("ldit_g_one", page.content().getFirst().name());
    assertTrue(page.content().stream().noneMatch(LayerDirectoryEntry::archived));
  }

  @Test
  void layerDirectory_filterByType_notEqualsNarrowsToTheOtherKind() throws Exception {
    seedActiveSample();

    var page = queryAsAdmin("filter=type,notEquals,LAYER&search=ldit_&page=0&size=10");

    assertEquals(2, page.totalElements());
    assertTrue(page.content().stream().allMatch(e -> e.type() == LayerDirectoryType.LAYER_GROUP));
  }

  @Test
  void layerDirectory_typeIsNotACoarseRequestParam() throws Exception {
    // Narrowing by kind is a criterion now, so there is one way to say it. A stray `type` param is
    // an unbound request param and must not silently filter.
    seedActiveSample();

    var page = queryAsAdmin("type=LAYER&search=ldit_&page=0&size=10");

    assertEquals(4, page.totalElements());
  }

  @Test
  void layerDirectory_search_matchesSubstringNotJustPrefix() throws Exception {
    // Search moved from prefix to substring, matching the Users grid.
    seedLayer("ldit_alpha_route_overlay", false, null);

    assertEquals(Set.of("ldit_alpha_route_overlay"), names(queryAsAdmin("search=route&size=10")));
  }

  @Test
  void layerDirectory_filterByName_containsMatchesMidString() throws Exception {
    seedActiveSample();

    assertEquals(
        Set.of("ldit_l_alpha"), names(queryAsAdmin("filter=name,contains,_l_alp&size=10")));
  }

  @Test
  void layerDirectory_filterByName_startsWithDoesNotMatchMidString() throws Exception {
    seedActiveSample();

    assertEquals(Set.of(), names(queryAsAdmin("filter=name,startsWith,l_alpha&size=10")));
    assertEquals(
        Set.of("ldit_l_alpha"), names(queryAsAdmin("filter=name,startsWith,ldit_l_alpha&size=10")));
  }

  @Test
  void layerDirectory_filterByName_equalsIsCaseInsensitive() throws Exception {
    seedActiveSample();

    assertEquals(
        Set.of("ldit_l_alpha"), names(queryAsAdmin("filter=name,equals,LDIT_L_ALPHA&size=10")));
  }

  @Test
  void layerDirectory_treatsLikeWildcardsLiterally() throws Exception {
    seedLayer("ldit_wild_one", false, null);
    seedLayer("ldit_wildxone", false, null);

    // `_` must match a literal underscore, not act as a single-character wildcard — on the
    // free-text search and on the name filter alike, since both go through LikePatterns.
    assertEquals(Set.of("ldit_wild_one"), names(queryAsAdmin("search=wild_o&size=10")));
    assertEquals(
        Set.of("ldit_wild_one"), names(queryAsAdmin("filter=name,contains,wild_o&size=10")));
  }

  @Test
  void layerDirectory_singleFilterParamIsNotCommaSplit() throws Exception {
    // Guards the getParameterValues binding: a lone filter param whose value contains commas must
    // reach the parser whole, not be shredded by Spring's collection converter.
    seedLayer("ldit_a, b", false, null);

    assertEquals(Set.of("ldit_a, b"), names(queryAsAdmin("filter=name,equals,ldit_a, b&size=10")));
  }

  @Test
  void layerDirectory_filterByAccessScope_narrowsAcrossBothUnionBranches() throws Exception {
    // Seeds a GLOBAL layer AND a GLOBAL group: the two branches carry different Postgres enum
    // types cast to text, so this proves both sides compare against the same token.
    seedLayer("ldit_l_global", "GLOBAL");
    seedGroup("ldit_g_global", "GLOBAL");
    seedLayer("ldit_l_private", "PRIVATE");
    seedGroup("ldit_g_private", "PRIVATE");

    assertEquals(
        Set.of("ldit_l_global", "ldit_g_global"),
        names(queryAsAdmin("filter=accessScope,equals,GLOBAL&search=ldit_&size=10")));
    assertEquals(
        Set.of("ldit_l_private", "ldit_g_private"),
        names(queryAsAdmin("filter=accessScope,notEquals,GLOBAL&search=ldit_&size=10")));
  }

  @Test
  void layerDirectory_filterByCreatedAt_beforeExcludesTheNamedDay() throws Exception {
    seedLayer("ldit_old", Instant.parse("2026-01-10T12:00:00Z"));
    seedLayer("ldit_boundary", Instant.parse("2026-02-01T00:30:00Z"));

    assertEquals(
        Set.of("ldit_old"),
        names(queryAsAdmin("filter=createdAt,before,2026-02-01&search=ldit_&size=10")));
  }

  @Test
  void layerDirectory_filterByCreatedAt_afterExcludesTheNamedDay() throws Exception {
    seedLayer("ldit_boundary", Instant.parse("2026-02-01T23:30:00Z"));
    seedLayer("ldit_next", Instant.parse("2026-02-02T00:30:00Z"));

    assertEquals(
        Set.of("ldit_next"),
        names(queryAsAdmin("filter=createdAt,after,2026-02-01&search=ldit_&size=10")));
  }

  @Test
  void layerDirectory_filterByCreatedAt_betweenIncludesBothEndpointDays() throws Exception {
    seedLayer("ldit_before", Instant.parse("2026-01-31T23:30:00Z"));
    seedLayer("ldit_from", Instant.parse("2026-02-01T00:30:00Z"));
    seedGroup("ldit_to", Instant.parse("2026-02-03T23:30:00Z"));
    seedLayer("ldit_after", Instant.parse("2026-02-04T00:30:00Z"));

    assertEquals(
        Set.of("ldit_from", "ldit_to"),
        names(
            queryAsAdmin("filter=createdAt,between,2026-02-01..2026-02-03&search=ldit_&size=10")));
  }

  @Test
  void layerDirectory_dateFilterResolvesDayBoundariesInTheRequestedZone() throws Exception {
    // Mirrors the user directory: both grids must answer "created on X" the same way, since they
    // share the predicate translator and both render timestamps in the viewer's zone.
    seedLayer("ldit_tz", Instant.parse("2026-03-01T18:00:00Z"));

    var inZone =
        queryAsAdmin(
            "search=ldit_tz&size=10&timezone=Asia/Taipei"
                + "&filter=createdAt,between,2026-03-02..2026-03-02");
    assertEquals(1, inZone.totalElements());

    // Same request without a zone keeps the documented UTC default, where the row is on the 1st.
    var inUtc =
        queryAsAdmin("search=ldit_tz&size=10&filter=createdAt,between,2026-03-02..2026-03-02");
    assertEquals(0, inUtc.totalElements());
  }

  @Test
  void layerDirectory_unknownTimezone_returnsBadRequest() throws Exception {
    expectBadRequest("filter=createdAt,before,2026-01-02&timezone=Not/AZone");
  }

  @Test
  void layerDirectory_criteriaCombineWithSearchAndRecordVisibility() throws Exception {
    UUID archiver = seedUser();
    seedLayer("ldit_l_archived", true, archiver);
    seedGroup("ldit_g_one");

    // Groups never archive, so narrowing the archived view to groups is legitimately empty.
    assertEquals(
        Set.of(),
        names(
            queryAsAdmin(
                "recordVisibility=ARCHIVED&filter=type,equals,LAYER_GROUP&search=ldit_&size=10")));
    assertEquals(
        Set.of("ldit_l_archived"),
        names(
            queryAsAdmin(
                "recordVisibility=ARCHIVED&filter=type,equals,LAYER&search=ldit_&size=10")));
  }

  @Test
  void layerDirectory_twoCriteriaOnTheSameFieldAndTogether() throws Exception {
    seedLayer("ldit_alpha_bravo", false, null);
    seedLayer("ldit_alpha_only", false, null);

    assertEquals(
        Set.of("ldit_alpha_bravo"),
        names(queryAsAdmin("filter=name,contains,alpha&filter=name,contains,bravo&size=10")));
  }

  @Test
  void layerDirectory_archivedVisibility_returnsOnlyArchivedLayers() throws Exception {
    UUID archiver = seedUser();
    seedLayer("ldit_l_active", false, null);
    seedLayer("ldit_l_archived", true, archiver);
    seedGroup("ldit_g_group");

    var page = queryAsAdmin("recordVisibility=ARCHIVED&search=ldit_&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals("ldit_l_archived", page.content().getFirst().name());
    assertEquals(LayerDirectoryType.LAYER, page.content().getFirst().type());
    assertTrue(page.content().getFirst().archived());
  }

  @Test
  void layerDirectory_recordVisibility_lifecycleStateMatrix() throws Exception {
    // One layer in each of the five lifecycle states. ACTIVE/ARCHIVED match exact states, so the
    // three transitional merge states surface only under ALL (mirrors LayerSpecifications).
    UUID archiver = seedUser();
    seedLayerWithLifecycle("ldit_ls_active", "ACTIVE", null);
    seedLayerWithLifecycle("ldit_ls_locked", "MERGE_SOURCE_LOCKED", null);
    seedLayerWithLifecycle("ldit_ls_pending", "PENDING_MERGE", null);
    seedLayerWithLifecycle("ldit_ls_failed", "MERGE_FAILED", null);
    seedLayerWithLifecycle("ldit_ls_archived", "ARCHIVED", archiver);

    assertEquals(
        Set.of("ldit_ls_active"),
        names(queryAsAdmin("recordVisibility=ACTIVE&search=ldit_ls_&page=0&size=10")));
    assertEquals(
        Set.of("ldit_ls_archived"),
        names(queryAsAdmin("recordVisibility=ARCHIVED&search=ldit_ls_&page=0&size=10")));

    var all = queryAsAdmin("recordVisibility=ALL&search=ldit_ls_&page=0&size=10");
    assertEquals(
        Set.of(
            "ldit_ls_active",
            "ldit_ls_locked",
            "ldit_ls_pending",
            "ldit_ls_failed",
            "ldit_ls_archived"),
        names(all));
    // archived is true only for the ARCHIVED layer; the transitional states report false.
    for (LayerDirectoryEntry entry : all.content()) {
      assertEquals(
          "ldit_ls_archived".equals(entry.name()),
          entry.archived(),
          "archived should be true only for the ARCHIVED layer, got: " + entry.name());
    }
  }

  @Test
  void layerDirectory_paginatesAcrossTheUnion() throws Exception {
    seedActiveSample();

    var page = queryAsAdmin("search=ldit_&page=0&size=2&sort=name,asc");

    assertEquals(4, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(2, page.size());
    assertEquals(2, page.content().size());
  }

  @Test
  void layerDirectory_defaultOrder_isTotalAndDeterministicAcrossPages() throws Exception {
    // A layer and a group share a name, so they tie on the primary `name` sort; the (type, id)
    // tie-breakers must still produce disjoint, complete pages under offset paging.
    seedTiedSample();

    // No caller sort — exercises the deterministic default order.
    var seen = collectKeysAcrossPages("search=ldit_&size=2");

    assertEquals(4, seen.size(), "every row appears exactly once across the traversal");
    assertEquals(4, Set.copyOf(seen).size(), "no row is repeated across pages");
  }

  @Test
  void layerDirectory_callerSort_isTotalAndDeterministicAcrossPages() throws Exception {
    // The same tie exists under a caller-selected primary sort (`name`); the appended (type, id)
    // tie-breakers must still yield disjoint, complete pages.
    seedTiedSample();

    var seen = collectKeysAcrossPages("search=ldit_&size=2&sort=name,asc");

    assertEquals(4, seen.size(), "every row appears exactly once across the traversal");
    assertEquals(4, Set.copyOf(seen).size(), "no row is repeated across pages");
  }

  @Test
  void layerDirectory_sortByArchived_returnsBadRequest() throws Exception {
    // archived is a two-group boolean derived from the five-valued lifecycleState, so it is not a
    // sortable field: sorting the underlying label would not reproduce the boolean grouping. It is
    // rejected with 400 rather than silently mis-ordering.
    mockMvc
        .perform(
            get("/v1/admin/layer-directory?sort=archived,asc").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT"));
  }

  @Test
  void layerDirectory_sortByAccessScope_isAccepted() throws Exception {
    // accessScope is a real projected column the grid sorts on; it must resolve (200), not 400.
    seedActiveSample();

    var page = queryAsAdmin("search=ldit_&page=0&size=10&sort=accessScope,asc");

    assertEquals(4, page.totalElements());
  }

  @Test
  void layerDirectory_sortByUpdatedAt_isAccepted() throws Exception {
    // updatedAt is a projected column the grid sorts on; it must resolve (200), not 400.
    seedActiveSample();

    var page = queryAsAdmin("search=ldit_&page=0&size=10&sort=updatedAt,desc");

    assertEquals(4, page.totalElements());
  }

  @ParameterizedTest
  @ValueSource(strings = {"asc", "desc"})
  void layerDirectory_sortByUpdatedAt_ordersNullsLastInBothDirections(String direction)
      throws Exception {
    // A never-updated layer keeps a null updated_at for good, and Postgres' native ordering puts
    // NULLs first on DESC — so "most recently updated" would open on never-updated rows. The
    // sample gives each direction two distinct timestamps to order and two rows with none: the
    // group projection has no updated_at at all, so those nulls are structural rather than seeded.
    seedActiveSample();
    touchLayer("ldit_l_alpha", Instant.now().minus(Duration.ofHours(2)));
    touchLayer("ldit_l_beta", Instant.now().minus(Duration.ofHours(1)));

    var page = queryAsAdmin("search=ldit_&page=0&size=10&sort=updatedAt," + direction);

    assertEquals(4, page.totalElements());
    var expectedLeading =
        "asc".equals(direction)
            ? List.of("ldit_l_alpha", "ldit_l_beta")
            : List.of("ldit_l_beta", "ldit_l_alpha");
    assertEquals(
        expectedLeading,
        page.content().stream().limit(2).map(LayerDirectoryEntry::name).toList(),
        "rows carrying a timestamp lead, ordered by the requested direction");
    assertTrue(
        page.content().stream().skip(2).allMatch(entry -> entry.updatedAt() == null),
        "rows carrying no updated_at sort last in both directions");
  }

  @Test
  void layerDirectory_unsupportedSortField_returnsBadRequest() throws Exception {
    // A sort name that does not map to a public field (typo or an internal property) is rejected
    // with 400 rather than surfacing as a 500 from query construction.
    mockMvc
        .perform(get("/v1/admin/layer-directory?sort=bogus,asc").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "filter=bogus,equals,x", // unknown field
        "filter=description,contains,x", // removed from the read model
        "filter=archived,equals,true", // derived, not a column
        "filter=lifecycleState,equals,ACTIVE", // behind the recordVisibility view mode
        "filter=email,contains,jo", // belongs to the user directory's catalog, not this one
        "filter=name,matches,x", // unknown operator
        "filter=name,startswith,x", // operator tokens are case-sensitive
        "filter=name,before,2026-01-01", // operator not supported for the field
        "filter=name,isEmpty,", // deliberately not offered — both name columns are NOT NULL
        "filter=type,contains,LAY", // text operator on a closed-domain field
        "filter=name", // malformed: no operator
        "filter=name,equals,", // missing a required value
        "filter=type,equals,BOGUS", // outside the type domain
        "filter=accessScope,equals,INTERNAL", // outside the access-scope domain
        "filter=createdAt,before,notadate",
        "filter=createdAt,between,2026-01-01", // incomplete range
        "filter=createdAt,between,2026-03-01..2026-01-01", // reversed range
        "filter=createdAt,after,+999999999-12-31" // next-day bound would overflow
      })
  void layerDirectory_rejectsAnInvalidFilter(String query) throws Exception {
    expectBadRequest(query);
  }

  @Test
  void layerDirectory_rejectsMoreFiltersThanTheCap() throws Exception {
    String tooMany =
        IntStream.rangeClosed(0, 10)
            .mapToObj(i -> "filter=name,contains,v" + i)
            .collect(Collectors.joining("&"));

    expectBadRequest(tooMany);
  }

  @Test
  void layerDirectory_rejectsAnOverlongFilterValue() throws Exception {
    expectBadRequest("filter=name,contains," + "x".repeat(101));
  }

  private void expectBadRequest(String query) throws Exception {
    mockMvc
        .perform(get("/v1/admin/layer-directory?" + query).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
  }

  private static Set<String> names(PageResponse<LayerDirectoryEntry> page) {
    return page.content().stream().map(LayerDirectoryEntry::name).collect(Collectors.toSet());
  }

  private PageResponse<LayerDirectoryEntry> queryAsAdmin(String query) throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/admin/layer-directory?" + query).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<PageResponse<LayerDirectoryEntry>>() {});
  }
}
