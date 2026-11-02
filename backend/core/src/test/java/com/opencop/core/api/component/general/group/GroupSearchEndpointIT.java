// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.group.GroupManagementSource;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = GroupSearchWebTestApplication.class)
class GroupSearchEndpointIT extends AbstractWebIT {

  /** Prefix on every seeded name so queries and cleanup never touch pre-existing/seed data. */
  private static final String PREFIX = "gseit_";

  /** A plain authenticated caller. Not an admin, which is the whole point of this route. */
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM iam.user_group WHERE name LIKE ?", PREFIX + "%");
  }

  private void seedGroup(String name, String description, String idpIssuer, String idpGroupId) {
    jdbcTemplate.update(
        """
        INSERT INTO iam.user_group (id, name, description, idp_issuer, idp_group_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        name,
        description,
        idpIssuer,
        idpGroupId,
        Timestamp.from(Instant.now()));
  }

  private void seedGroup(String name) {
    seedGroup(name, name + " description", null, null);
  }

  @Test
  void search_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/user-groups/search")).andExpect(status().isUnauthorized());
  }

  /**
   * The capability this route exists for: a caller without {@code ROLE_admin} gets a populated
   * pool. The admin listing answers 403 for that same caller, which is why the picker needed it.
   */
  @Test
  void search_forAuthenticatedNonAdmin_returnsPrefixMatchesSortedByName() throws Exception {
    seedGroup(PREFIX + "joanna");
    seedGroup(PREFIX + "johnny");
    seedGroup(PREFIX + "bob");

    var page = search("search=" + PREFIX + "jo&page=0&size=10&sort=name,asc");

    assertEquals(2, page.totalElements());
    assertEquals(PREFIX + "joanna", page.content().get(0).name());
    assertEquals(PREFIX + "johnny", page.content().get(1).name());
  }

  /**
   * Pins the premise the route rests on. The same principal that gets a populated pool here is
   * refused by the admin listing. Without this test, "non-admin" is an assumption, not a fact.
   */
  @Test
  void adminListing_staysForbiddenForTheSamePrincipal() throws Exception {
    mockMvc
        .perform(get("/v1/admin/user-groups").with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  /**
   * {@code hasSearch()} guards on {@code isBlank()} as well as null, so a whitespace-only term has
   * to behave like an absent one. Asserted against the unfiltered total rather than a fixed count,
   * so the result does not depend on how many groups the database happens to hold.
   */
  @Test
  void search_withBlankSearchTerm_isTreatedAsNoFilter() throws Exception {
    seedGroup(PREFIX + "alice");
    seedGroup(PREFIX + "bob");

    var unfiltered = search("page=0&size=1");
    // .param() rather than a raw query string, so a literal space survives to the controller.
    var blankResult =
        mockMvc
            .perform(
                get("/v1/user-groups/search")
                    .param("search", "   ")
                    .param("page", "0")
                    .param("size", "1")
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    PageResponse<GroupSearchResult> blank =
        readResponse(blankResult, new TypeReference<PageResponse<GroupSearchResult>>() {});

    assertEquals(unfiltered.totalElements(), blank.totalElements());
    // Both counts must include the two rows just seeded, or neither call queried anything.
    assertTrue(blank.totalElements() >= 2);
  }

  @Test
  void search_pageBeyondLastPage_returnsEmptyContentNotError() throws Exception {
    seedGroup(PREFIX + "alice");

    var page = search("search=" + PREFIX + "&page=99&size=10");

    assertEquals(0, page.content().size());
    assertEquals(1, page.totalElements());
  }

  @Test
  void search_paginatesWithBoundedPageSize() throws Exception {
    seedGroup(PREFIX + "a");
    seedGroup(PREFIX + "b");
    seedGroup(PREFIX + "c");

    var page = search("search=" + PREFIX + "&page=0&size=2&sort=name,asc");

    assertEquals(3, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(2, page.size());
    assertEquals(0, page.page());
    assertEquals(2, page.content().size());
  }

  @Test
  void search_matchesCaseInsensitively() throws Exception {
    seedGroup(PREFIX + "OpsTeam");

    var page = search("search=" + PREFIX + "opste&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals(PREFIX + "OpsTeam", page.content().getFirst().name());
  }

  @Test
  void search_treatsLikeWildcardsLiterally() throws Exception {
    seedGroup(PREFIX + "wild_one");
    seedGroup(PREFIX + "wildxone");

    // The `_` must match a literal underscore rather than act as a single-char wildcard.
    // Otherwise "wildxone" would match too, with x sitting where the escaped underscore is.
    var page = search("search=" + PREFIX + "wild_o&page=0&size=10&sort=name,asc");

    assertEquals(1, page.totalElements());
    assertEquals(PREFIX + "wild_one", page.content().getFirst().name());
  }

  @Test
  void search_returnsOnlyPickerFields_notInternalGroupRepresentation() throws Exception {
    seedGroup(PREFIX + "min", "a description", "https://idp.test", "idp-group-1");

    // The picker needs only id + name; everything else must stay off the wire.
    mockMvc
        .perform(
            get("/v1/user-groups/search?search=" + PREFIX + "min&page=0&size=10")
                .with(authentication(USER_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].name").value(PREFIX + "min"))
        .andExpect(jsonPath("$.content[0].description").doesNotExist())
        .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].idpIssuer").doesNotExist())
        .andExpect(jsonPath("$.content[0].idpGroupId").doesNotExist())
        .andExpect(jsonPath("$.content[0].members").doesNotExist());
  }

  @Test
  void search_includesIdpProvisionedGroups() throws Exception {
    seedGroup(PREFIX + "scim", "from the idp", "https://idp.test", "idp-group-2");

    // SCIM-provisioned groups are ordinary grant targets, so the picker must offer them.
    var page = search("search=" + PREFIX + "scim&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals(PREFIX + "scim", page.content().getFirst().name());
  }

  @Test
  void search_appliesDefaultNameOrderWhenNoSortGiven() throws Exception {
    seedGroup(PREFIX + "charlie");
    seedGroup(PREFIX + "alice");
    seedGroup(PREFIX + "bob");

    var page = search("search=" + PREFIX + "&page=0&size=10");

    assertEquals(
        List.of(PREFIX + "alice", PREFIX + "bob", PREFIX + "charlie"),
        page.content().stream().map(GroupSearchResult::name).toList());
  }

  @Test
  void search_paginationIsDisjointAndComplete() throws Exception {
    seedGroup(PREFIX + "p1");
    seedGroup(PREFIX + "p2");
    seedGroup(PREFIX + "p3");
    seedGroup(PREFIX + "p4");

    var probe = search("search=" + PREFIX + "p&page=0&size=2&sort=name,asc");
    assertEquals(4, probe.totalElements());
    assertEquals(2, probe.totalPages());

    List<UUID> ids = new ArrayList<>();
    for (int p = 0; p < probe.totalPages(); p++) {
      var page = search("search=" + PREFIX + "p&page=" + p + "&size=2&sort=name,asc");
      page.content().forEach(g -> ids.add(g.id()));
    }

    assertEquals(4, ids.size());
    assertEquals(4, Set.copyOf(ids).size());
  }

  /**
   * Since V23 a name is unique only within its scope, so an admin-created group and a SCIM group
   * may share one. Sorting by name alone is therefore not a total order, and offset paging across
   * the tie is free to repeat or drop a row.
   *
   * <p>This pins the observable contract, not the mechanism. Postgres returns two tied rows in a
   * stable order at this scale whether or not the {@code id} tie-breaker is applied, so removing it
   * from {@code AppGroupService.getPage} does not fail this test. Treat the tie-breaker as required
   * by the schema rather than as something this suite enforces.
   */
  @Test
  void search_pagesTiedNamesWithoutRepeatingOrDroppingRows() throws Exception {
    seedGroup(PREFIX + "dup", "admin-created", null, null);
    seedGroup(PREFIX + "dup", "scim-provisioned", "https://idp.test", "idp-group-tie");

    var probe = search("search=" + PREFIX + "dup&page=0&size=1&sort=name,asc");
    assertEquals(2, probe.totalElements());
    assertEquals(2, probe.totalPages());

    List<UUID> ids = new ArrayList<>();
    for (int p = 0; p < probe.totalPages(); p++) {
      var page = search("search=" + PREFIX + "dup&page=" + p + "&size=1&sort=name,asc");
      page.content().forEach(g -> ids.add(g.id()));
    }

    assertEquals(2, ids.size());
    assertEquals(2, Set.copyOf(ids).size());
  }

  /**
   * The reason the projection carries a source at all. Two same-name groups are schema-legal, and
   * the picker identifies a candidate by name, so without this the operator cannot tell which
   * population a grant would reach.
   */
  @Test
  void search_distinguishesSameNameGroupsByManagementSource() throws Exception {
    seedGroup(PREFIX + "shared", "admin-created", null, null);
    seedGroup(PREFIX + "shared", "scim-provisioned", "https://idp.test", "idp-group-shared");

    var page = search("search=" + PREFIX + "shared&page=0&size=10&sort=name,asc");

    assertEquals(2, page.totalElements());
    assertEquals(
        Set.of(GroupManagementSource.OPENCOP, GroupManagementSource.SCIM),
        page.content().stream().map(GroupSearchResult::source).collect(Collectors.toSet()));
  }

  /** The classification is on the wire; the issuer behind it is not. */
  @Test
  void search_exposesSourceButNotTheIssuerItself() throws Exception {
    seedGroup(PREFIX + "scimmed", "from the idp", "https://idp.test", "idp-group-3");

    mockMvc
        .perform(
            get("/v1/user-groups/search?search=" + PREFIX + "scimmed&page=0&size=10")
                .with(authentication(USER_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].source").value("SCIM"))
        .andExpect(jsonPath("$.content[0].idpIssuer").doesNotExist())
        .andExpect(jsonPath("$.content[0].idpGroupId").doesNotExist());
  }

  @Test
  void search_unknownSortField_returnsBadRequest() throws Exception {
    seedGroup(PREFIX + "alice");

    searchExpectingBadRequest("search=" + PREFIX + "&sort=notAField,asc");
  }

  /**
   * The picker projection hides description, timestamps and IdP identifiers, so ordering by any of
   * them would disclose their relative values to a caller that cannot read a single one. They are
   * valid entity properties, which is exactly why the allowlist has to be narrower than the entity.
   */
  @Test
  void search_sortByFieldTheProjectionHides_returnsBadRequest() throws Exception {
    seedGroup(PREFIX + "alice");

    searchExpectingBadRequest("search=" + PREFIX + "&sort=description,asc");
    searchExpectingBadRequest("search=" + PREFIX + "&sort=createdAt,desc");
    searchExpectingBadRequest("search=" + PREFIX + "&sort=idpIssuer,asc");
  }

  @Test
  void search_visibleSortFieldsAreAccepted() throws Exception {
    seedGroup(PREFIX + "alice");

    for (String field : List.of("id", "name")) {
      mockMvc
          .perform(
              get("/v1/user-groups/search?search=" + PREFIX + "&sort=" + field + ",asc")
                  .with(authentication(USER_AUTH)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void search_sortsByNameDescendingWhenRequested() throws Exception {
    seedGroup(PREFIX + "alice");
    seedGroup(PREFIX + "bob");
    seedGroup(PREFIX + "carol");

    var page = search("search=" + PREFIX + "&sort=name,desc");

    assertEquals(
        List.of(PREFIX + "carol", PREFIX + "bob", PREFIX + "alice"),
        page.content().stream().map(GroupSearchResult::name).toList());
  }

  @Test
  void search_withNoSearchTerm_returnsEveryGroup() throws Exception {
    seedGroup(PREFIX + "alice");
    seedGroup(PREFIX + "bob");

    var page = search("page=0&size=100&sort=name,asc");

    assertEquals(
        2,
        page.content().stream()
            .map(GroupSearchResult::name)
            .filter(n -> n.startsWith(PREFIX))
            .count());
  }

  private void searchExpectingBadRequest(String query) throws Exception {
    mockMvc
        .perform(get("/v1/user-groups/search?" + query).with(authentication(USER_AUTH)))
        .andExpect(status().isBadRequest());
  }

  private PageResponse<GroupSearchResult> search(String query) throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/user-groups/search?" + query).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<PageResponse<GroupSearchResult>>() {});
  }
}
