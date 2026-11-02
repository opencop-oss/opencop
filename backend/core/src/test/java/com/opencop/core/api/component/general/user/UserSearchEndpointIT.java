// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.endpoint.user.UserSearchResult;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.sql.Timestamp;
import java.time.Instant;
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

@ComponentWebIT(classes = UserSearchWebTestApplication.class)
class UserSearchEndpointIT extends AbstractWebIT {

  /** Marks rows this test owns so cleanup/queries never touch pre-existing/seed data. */
  private static final String ISSUER = "https://user-search-it";

  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM opencop_user WHERE idp_issuer = ?", ISSUER);
  }

  private void seedUser(
      String username,
      String firstName,
      String lastName,
      String email,
      boolean admin,
      boolean active,
      boolean deleted) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email,
          first_name, last_name, display_name, administrator, active, deleted_at
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
        firstName + " " + lastName,
        admin,
        active,
        deleted ? Timestamp.from(Instant.now()) : null);
  }

  @Test
  void search_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/users/search")).andExpect(status().isUnauthorized());
  }

  @Test
  void search_forAuthenticatedNonAdmin_returnsPrefixMatchesSortedByUsername() throws Exception {
    seedUser("ussit_johnny", "John", "Doe", "john@ussit.test", false, true, false);
    seedUser("ussit_joanna", "Joanna", "Smith", "joanna@ussit.test", false, true, false);
    seedUser("ussit_bob", "Bob", "Jones", "bob@ussit.test", false, true, false);

    var page = search("search=ussit_jo&page=0&size=10&sort=username,asc");

    assertEquals(2, page.totalElements());
    assertEquals("ussit_joanna", page.content().get(0).username());
    assertEquals("ussit_johnny", page.content().get(1).username());
  }

  @Test
  void search_paginatesWithBoundedPageSize() throws Exception {
    seedUser("ussit_a", "A", "A", "a@ussit.test", false, true, false);
    seedUser("ussit_b", "B", "B", "b@ussit.test", false, true, false);
    seedUser("ussit_c", "C", "C", "c@ussit.test", false, true, false);

    var page = search("search=ussit_&page=0&size=2&sort=username,asc");

    assertEquals(3, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(2, page.size());
    assertEquals(0, page.page());
    assertEquals(2, page.content().size());
  }

  @Test
  void search_excludesSoftDeletedUsers() throws Exception {
    seedUser("ussit_active", "Amy", "Active", "amy@ussit.test", false, true, false);
    seedUser("ussit_deleted", "Dan", "Deleted", "dan@ussit.test", false, true, true);

    var page = search("search=ussit_&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals("ussit_active", page.content().getFirst().username());
  }

  @Test
  void search_filtersByAdminFlag() throws Exception {
    seedUser("ussit_admin", "Alice", "Admin", "alice@ussit.test", true, true, false);
    seedUser("ussit_plain", "Pete", "Plain", "pete@ussit.test", false, true, false);

    var page = search("search=ussit_&admin=true&page=0&size=10");

    assertEquals(1, page.totalElements());
    assertEquals("ussit_admin", page.content().getFirst().username());
  }

  @Test
  void search_treatsLikeWildcardsLiterally() throws Exception {
    seedUser("ussit_wild_one", "First", "Last", "one@ussit.test", false, true, false);
    seedUser("ussit_wildxone", "First", "Last", "two@ussit.test", false, true, false);

    // The `_` must match a literal underscore, not act as a single-char wildcard — otherwise
    // "ussit_wildxone" (x where the escaped underscore sits) would also match.
    var page = search("search=ussit_wild_o&page=0&size=10&sort=username,asc");

    assertEquals(1, page.totalElements());
    assertEquals("ussit_wild_one", page.content().getFirst().username());
  }

  @Test
  void search_returnsOnlyPickerFields_notInternalUserRepresentation() throws Exception {
    seedUser("ussit_min", "Mina", "Minimal", "mina@ussit.test", true, true, false);

    // The picker needs only id + username + displayName; internal fields must not reach the wire.
    mockMvc
        .perform(
            get("/v1/users/search?search=ussit_min&page=0&size=10").with(authentication(USER_AUTH)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].username").value("ussit_min"))
        .andExpect(jsonPath("$.content[0].displayName").value("Mina Minimal"))
        .andExpect(jsonPath("$.content[0].idpUserId").doesNotExist())
        .andExpect(jsonPath("$.content[0].idpUsername").doesNotExist())
        .andExpect(jsonPath("$.content[0].issuer").doesNotExist())
        .andExpect(jsonPath("$.content[0].email").doesNotExist())
        .andExpect(jsonPath("$.content[0].admin").doesNotExist())
        .andExpect(jsonPath("$.content[0].administrator").doesNotExist())
        .andExpect(jsonPath("$.content[0].active").doesNotExist())
        .andExpect(jsonPath("$.content[0].createdAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].deletedAt").doesNotExist())
        .andExpect(jsonPath("$.content[0].sessionGeneration").doesNotExist())
        .andExpect(jsonPath("$.content[0].accountAccessRevision").doesNotExist());
  }

  @Test
  void search_appliesDefaultUsernameOrderWhenNoSortGiven() throws Exception {
    seedUser("ussit_charlie", "C", "C", "c@ussit.test", false, true, false);
    seedUser("ussit_alice", "A", "A", "a@ussit.test", false, true, false);
    seedUser("ussit_bob", "B", "B", "b@ussit.test", false, true, false);

    var page = search("search=ussit_&page=0&size=10");

    assertEquals(
        List.of("ussit_alice", "ussit_bob", "ussit_charlie"),
        page.content().stream().map(UserSearchResult::username).toList());
  }

  @Test
  void search_paginationIsDisjointAndCompleteWhenDisplayNamesTie() throws Exception {
    seedUser("ussit_p1", "Dup", "Name", "p1@ussit.test", false, true, false);
    seedUser("ussit_p2", "Dup", "Name", "p2@ussit.test", false, true, false);
    seedUser("ussit_p3", "Dup", "Name", "p3@ussit.test", false, true, false);
    seedUser("ussit_p4", "Dup", "Name", "p4@ussit.test", false, true, false);

    var probe = search("search=ussit_p&page=0&size=2&sort=displayName,asc");
    assertEquals(4, probe.totalElements());
    assertEquals(2, probe.totalPages());

    List<UUID> ids = new ArrayList<>();
    for (int p = 0; p < probe.totalPages(); p++) {
      var page = search("search=ussit_p&page=" + p + "&size=2&sort=displayName,asc");
      page.content().forEach(u -> ids.add(u.id()));
    }

    assertEquals(4, ids.size());
    assertEquals(4, Set.copyOf(ids).size());
  }

  @Test
  void search_unknownSortField_returnsBadRequest() throws Exception {
    seedUser("ussit_alice", "A", "A", "a@ussit.test", false, true, false);

    searchExpectingBadRequest("search=ussit_&sort=notAField,asc");
  }

  /**
   * The picker projection hides email, so ordering by it would disclose the pool's email ordering
   * to a caller that cannot read a single address. It is a valid entity property, which is exactly
   * why the allowlist has to be narrower than the entity.
   */
  @Test
  void search_sortByFieldTheProjectionHides_returnsBadRequest() throws Exception {
    seedUser("ussit_alice", "A", "A", "a@ussit.test", false, true, false);

    searchExpectingBadRequest("search=ussit_&sort=email,asc");
    searchExpectingBadRequest("search=ussit_&sort=administrator,desc");
    searchExpectingBadRequest("search=ussit_&sort=createdAt,asc");
  }

  @Test
  void search_visibleSortFieldsAreAccepted() throws Exception {
    seedUser("ussit_alice", "A", "A", "a@ussit.test", false, true, false);

    for (String field : List.of("id", "username", "displayName")) {
      mockMvc
          .perform(
              get("/v1/users/search?search=ussit_&sort=" + field + ",asc")
                  .with(authentication(USER_AUTH)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void search_sortsByDisplayNameDescendingWhenRequested() throws Exception {
    seedUser("ussit_alice", "A", "A", "a@ussit.test", false, true, false);
    seedUser("ussit_bob", "B", "B", "b@ussit.test", false, true, false);
    seedUser("ussit_carol", "C", "C", "c@ussit.test", false, true, false);

    var page = search("search=ussit_&sort=username,desc");

    assertEquals(
        List.of("ussit_carol", "ussit_bob", "ussit_alice"),
        page.content().stream().map(UserSearchResult::username).toList());
  }

  private void searchExpectingBadRequest(String query) throws Exception {
    mockMvc
        .perform(get("/v1/users/search?" + query).with(authentication(USER_AUTH)))
        .andExpect(status().isBadRequest());
  }

  private PageResponse<UserSearchResult> search(String query) throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/users/search?" + query).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<PageResponse<UserSearchResult>>() {});
  }
}
