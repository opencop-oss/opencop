// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static com.opencop.core.api.component.support.assertion.EventAssertions.assertHistorySummary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = EventWebTestApplication.class)
class RetrieveEventHistoryIT extends AbstractWebIT {

  private static final String OBJECT_ID = "event-123";
  private static final UUID FIRST_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final UUID SECOND_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
  private static final Instant FROM = Instant.parse("2026-03-12T10:00:00Z");
  private static final Instant TO = Instant.parse("2026-03-12T11:00:00Z");
  private static final Instant BEFORE_RANGE = Instant.parse("2026-03-12T09:45:00Z");
  private static final Instant FIRST_EVENT_TIME = Instant.parse("2026-03-12T10:15:00Z");
  private static final Instant SECOND_EVENT_TIME = Instant.parse("2026-03-12T10:45:00Z");
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");
  private static final Authentication ADMIN_AUTH = adminAuthentication(USER_ID);
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);

  private EventFixtures eventFixtures;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private UserSpecificManager userSpecificManager;

  @BeforeEach
  void setUpData() {
    eventFixtures = new EventFixtures(jdbcTemplate, objectMapper);
    eventFixtures.reset();
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(FIRST_SOURCE_ID, ServerRole.VIEWER, null)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());
  }

  @Test
  void viewerCanGetBucketedHistoryForAuthorizedSource() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, FIRST_EVENT_TIME);
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, SECOND_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventHistoryResponse.class);
    assertHistorySummary(response, OBJECT_ID, "first_last_bucket", 1000, 2, 2, 2);
    assertEquals(
        OBJECT_ID,
        ((java.util.Map<?, ?>)
                response.data().getFeatures().getFirst().getProperties().get("opencop"))
            .get("objectId"));
  }

  @Test
  void adminCanGetBucketedHistoryWithoutMembership() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, FIRST_EVENT_TIME);
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SECOND_SOURCE_ID, SECOND_EVENT_TIME);
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventHistoryResponse.class);
    assertHistorySummary(response, OBJECT_ID, "first_last_bucket", 1000, 1, 1, 1);
  }

  @Test
  void getHistory_whenRequestedMaxPointsExceedsLimit_capsAtTwoThousand() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, FIRST_EVENT_TIME);
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, SECOND_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString())
                    .queryParam("maxPoints", "5000"))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventHistoryResponse.class);
    assertHistorySummary(response, OBJECT_ID, "first_last_bucket", 2000, 2, 2, 2);
  }

  @Test
  void getHistory_whenObjectExistsButRangeIsEmpty_returnsEmptyHistory() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, BEFORE_RANGE);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventHistoryResponse.class);
    assertHistorySummary(response, OBJECT_ID, 1000, 0, 0, 0);
  }

  @Test
  void getHistory_whenUserHasNoMatchingAccess_returnsForbidden() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SECOND_SOURCE_ID, FIRST_EVENT_TIME);
    when(userSpecificManager.getUserServers(USER_ID)).thenReturn(List.of());

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", SECOND_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isForbidden())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().contains("not authorized"));
  }

  @Test
  void getHistory_whenObjectDoesNotExist_returnsNotFound() throws Exception {
    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(FIRST_SOURCE_ID, ServerRole.VIEWER, null)));

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Event not found", error.message());
  }

  @Test
  void getHistory_whenFromIsInvalid_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .queryParam("from", "not-an-instant")
                .queryParam("to", TO.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getHistory_whenFromIsMissing_returnsBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("to", TO.toString()))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("MISSING_PARAMETER", error.code());
  }

  @Test
  void getHistory_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                .with(csrf())
                .queryParam("from", FROM.toString())
                .queryParam("to", TO.toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getHistory_filtersOutRowsFromOtherSourcesWithSameObjectId() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, FIRST_SOURCE_ID, FIRST_EVENT_TIME);
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SECOND_SOURCE_ID, SECOND_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/history", FIRST_SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .queryParam("from", FROM.toString())
                    .queryParam("to", TO.toString()))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, EventHistoryResponse.class);
    assertHistorySummary(response, OBJECT_ID, "first_last_bucket", 1000, 1, 1, 1);
  }

  private static UsernamePasswordAuthenticationToken adminAuthentication(UUID userId) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(userId, true);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));
  }
}
