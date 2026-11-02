// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.sdk.component.TestFixture;
import com.opencop.core.sdk.model.notification.NotificationSeverityType;
import com.opencop.core.sdk.model.notification.OpencopNotification;
import com.opencop.core.sdk.model.notification.content.AccessOperationType;
import com.opencop.core.sdk.model.notification.content.LayerAccessNotification;
import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import com.opencop.core.sdk.ports.gateway.db.notification.NotificationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class NotificationFixtures implements TestFixture {

  private final JdbcTemplate jdbcTemplate;
  private final NotificationService notificationService;

  public NotificationFixtures(JdbcTemplate jdbcTemplate, NotificationService notificationService) {
    this.jdbcTemplate = jdbcTemplate;
    this.notificationService = notificationService;
  }

  @Override
  public void reset() {
    jdbcTemplate.update("DELETE FROM opencop_notification");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
  }

  public void persistNotification(OpencopNotification notification) throws Exception {
    seedUser(notification.issuerUserId());
    seedUser(notification.targetUserId());
    notificationService.save(notification);
  }

  public Instant loadReadAt(UUID notificationId) {
    return jdbcTemplate.queryForObject(
        "SELECT read_at FROM opencop_notification WHERE id = ?", Instant.class, notificationId);
  }

  public Map<String, Object> loadNotificationRow(UUID notificationId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM opencop_notification WHERE id = ?", notificationId);
  }

  public void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.com",
        "Test",
        "User",
        "Test User");
  }

  public static OpencopNotification defaultUnreadNotification(
      UUID notificationId, UUID issuerUserId, UUID targetUserId, String role, Instant createdAt) {
    return new OpencopNotification(
        notificationId,
        null,
        issuerUserId,
        targetUserId,
        NotificationSeverityType.INFO,
        new LayerAccessNotification(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440151"),
            AccessOperationType.ADDED,
            role),
        NotificationStatus.CREATED,
        createdAt,
        false,
        null);
  }

  public static OpencopNotification defaultReadNotification(
      UUID notificationId,
      UUID issuerUserId,
      UUID targetUserId,
      String role,
      Instant createdAt,
      Instant readAt) {
    return new OpencopNotification(
        notificationId,
        null,
        issuerUserId,
        targetUserId,
        NotificationSeverityType.INFO,
        new LayerAccessNotification(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440151"),
            AccessOperationType.MODIFIED,
            role),
        NotificationStatus.SENT,
        createdAt,
        true,
        readAt);
  }
}
