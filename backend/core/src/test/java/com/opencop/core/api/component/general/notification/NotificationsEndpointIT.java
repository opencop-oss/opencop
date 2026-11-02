// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.NotificationFixtures;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = NotificationWebTestApplication.class)
class NotificationsEndpointIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440141");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440142");
  private static final UUID ISSUER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440143");
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NotificationService notificationService;

  private NotificationFixtures notificationFixtures;

  @BeforeEach
  void setUp() {
    notificationFixtures = new NotificationFixtures(jdbcTemplate, notificationService);
    notificationFixtures.reset();
  }

  // Auth
  @Test
  void getNotifications_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/notifications")).andExpect(status().isUnauthorized());
  }

  // Happy path
  @Test
  void getNotifications_returnsPagedNotificationsForAuthenticatedUser() throws Exception {
    var olderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440144");
    var newestId = UUID.fromString("550e8400-e29b-41d4-a716-446655440145");
    var otherUsersId = UUID.fromString("550e8400-e29b-41d4-a716-446655440146");

    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            olderId, ISSUER_ID, USER_ID, "MEMBER", Instant.parse("2026-03-16T08:00:00Z")));
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            newestId, ISSUER_ID, USER_ID, "ADMIN", Instant.parse("2026-03-16T09:00:00Z")));
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            otherUsersId,
            ISSUER_ID,
            OTHER_USER_ID,
            "OWNER",
            Instant.parse("2026-03-16T10:00:00Z")));

    var result =
        mockMvc
            .perform(get("/v1/notifications?page=0&size=1").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var page = readResponse(result, new TypeReference<PageResponse<OpencopNotification>>() {});
    assertEquals(2, page.totalElements());
    assertEquals(2, page.totalPages());
    assertEquals(1, page.size());
    assertEquals(0, page.page());
    assertEquals(1, page.content().size());
    assertNotification(page.content().getFirst(), newestId, USER_ID, false, null, "ADMIN");
  }

  @Test
  void getNotifications_whenUnreadOnly_returnsOnlyUnreadNotifications() throws Exception {
    var unreadId = UUID.fromString("550e8400-e29b-41d4-a716-446655440147");
    var readId = UUID.fromString("550e8400-e29b-41d4-a716-446655440148");

    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            unreadId, ISSUER_ID, USER_ID, "VIEWER", Instant.parse("2026-03-16T09:30:00Z")));
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultReadNotification(
            readId,
            ISSUER_ID,
            USER_ID,
            "ADMIN",
            Instant.parse("2026-03-16T09:00:00Z"),
            Instant.parse("2026-03-16T09:15:00Z")));

    var result =
        mockMvc
            .perform(get("/v1/notifications?unreadOnly=true").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var page = readResponse(result, new TypeReference<PageResponse<OpencopNotification>>() {});
    assertEquals(1, page.totalElements());
    assertEquals(1, page.content().size());
    assertNotification(page.content().getFirst(), unreadId, USER_ID, false, null, "VIEWER");
  }

  // Other edge cases
  @Test
  void markNotificationAsRead_marksUnreadNotificationInDatabase() throws Exception {
    var notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440149");
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            notificationId, ISSUER_ID, USER_ID, "MEMBER", Instant.parse("2026-03-16T09:45:00Z")));

    mockMvc
        .perform(
            post("/v1/notifications/{notificationId}/read", notificationId)
                .with(csrf())
                .with(authentication(USER_AUTH)))
        .andExpect(status().isOk());

    assertNotNull(notificationFixtures.loadReadAt(notificationId));
  }

  @Test
  void markNotificationAsRead_withoutCsrf_returnsForbiddenAndDoesNotMutateDatabase()
      throws Exception {
    var notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440150");
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            notificationId, ISSUER_ID, USER_ID, "MEMBER", Instant.parse("2026-03-16T09:50:00Z")));

    mockMvc
        .perform(
            post("/v1/notifications/{notificationId}/read", notificationId)
                .with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());

    assertNull(notificationFixtures.loadReadAt(notificationId));
  }

  @Test
  void markNotificationAsRead_whenNotificationDoesNotExist_returnsBadRequest() throws Exception {
    var missingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440152");

    var result =
        mockMvc
            .perform(
                post("/v1/notifications/{notificationId}/read", missingId)
                    .with(csrf())
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Notification not found or not owned by requestor: " + missingId, error.message());
  }

  @Test
  void markNotificationAsRead_whenOwnedByAnotherUser_returnsBadRequestAndLeavesItUnread()
      throws Exception {
    var notificationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440153");
    notificationFixtures.persistNotification(
        NotificationFixtures.defaultUnreadNotification(
            notificationId,
            ISSUER_ID,
            OTHER_USER_ID,
            "MEMBER",
            Instant.parse("2026-03-16T09:55:00Z")));

    var result =
        mockMvc
            .perform(
                post("/v1/notifications/{notificationId}/read", notificationId)
                    .with(csrf())
                    .with(authentication(USER_AUTH)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Already marked as read: " + notificationId, error.message());
    assertNull(notificationFixtures.loadReadAt(notificationId));
  }

  private static void assertNotification(
      OpencopNotification notification,
      UUID expectedId,
      UUID expectedTargetUserId,
      boolean expectedRead,
      Instant expectedReadAt,
      String expectedRole) {
    assertEquals(expectedId, notification.notificationId());
    assertEquals(expectedTargetUserId, notification.targetUserId());
    assertEquals(expectedRead, notification.read());
    assertEquals(expectedReadAt, notification.readAt());
    assertEquals(
        expectedRole,
        ((com.opencop.core.sdk.model.notification.content.LayerAccessNotification)
                notification.content())
            .role());
  }
}
