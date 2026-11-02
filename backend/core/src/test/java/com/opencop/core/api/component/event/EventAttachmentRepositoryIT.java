// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentAssociation;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@ComponentWebIT(classes = EventStorageTestApplication.class)
class EventAttachmentRepositoryIT extends AbstractDatabaseIT {

  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440110");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440111");
  private static final String OBJECT_A = "event-123";
  private static final String OBJECT_B = "event-456";
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440112");

  @Autowired private EventAttachmentRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    EventStorageCleanup.reset(jdbcTemplate);
    seedUser(USER_ID);
  }

  @AfterEach
  void tearDown() {
    EventStorageCleanup.reset(jdbcTemplate);
  }

  @Test
  void findAttachmentIdsByObjectId_returnsOnlyRowsForMatchingSourceAndObjectId() {
    var mediaA = UUID.randomUUID();
    var mediaB = UUID.randomUUID();
    var mediaC = UUID.randomUUID();
    seedAttachment(mediaA);
    seedAttachment(mediaB);
    seedAttachment(mediaC);

    repository.save(SOURCE_A, OBJECT_A, mediaA);
    repository.save(SOURCE_B, OBJECT_A, mediaB);
    repository.save(SOURCE_A, OBJECT_B, mediaC);

    var result = repository.findAttachmentIdsByObjectId(SOURCE_A, OBJECT_A);

    assertEquals(List.of(mediaA), result);
  }

  @Test
  void findAssociationsByAttachmentId_returnsAllSourceObjectAssociationsForSharedAttachment() {
    var attachmentId = UUID.randomUUID();
    var unrelatedAttachmentId = UUID.randomUUID();
    seedAttachment(attachmentId);
    seedAttachment(unrelatedAttachmentId);

    repository.save(SOURCE_A, OBJECT_A, attachmentId);
    repository.save(SOURCE_B, OBJECT_A, attachmentId);
    repository.save(SOURCE_A, OBJECT_B, unrelatedAttachmentId);

    var result = repository.findAssociationsByAttachmentId(attachmentId);

    assertEquals(
        List.of(
            new EventAttachmentAssociation(SOURCE_A, OBJECT_A, attachmentId),
            new EventAttachmentAssociation(SOURCE_B, OBJECT_A, attachmentId)),
        result);
  }

  @Test
  void existsAndDelete_areScopedBySourceAndObjectId() {
    var attachmentId = UUID.randomUUID();
    seedAttachment(attachmentId);
    repository.save(SOURCE_B, OBJECT_A, attachmentId);

    assertFalse(repository.exists(SOURCE_A, OBJECT_A, attachmentId));
    assertTrue(repository.exists(SOURCE_B, OBJECT_A, attachmentId));

    repository.delete(SOURCE_A, OBJECT_A, attachmentId);
    assertTrue(repository.exists(SOURCE_B, OBJECT_A, attachmentId));

    repository.delete(SOURCE_B, OBJECT_A, attachmentId);
    assertFalse(repository.exists(SOURCE_B, OBJECT_A, attachmentId));
  }

  @Test
  void save_persistsSourceIdAndObjectIdColumns() {
    var attachmentId = UUID.randomUUID();
    seedAttachment(attachmentId);

    repository.save(SOURCE_A, OBJECT_A, attachmentId);

    var row =
        jdbcTemplate.queryForMap(
            "SELECT source_id, object_id, attachment_id FROM event_attachment WHERE source_id = ? AND object_id = ? AND attachment_id = ?",
            SOURCE_A,
            OBJECT_A,
            attachmentId);

    assertEquals(SOURCE_A, row.get("source_id"));
    assertEquals(OBJECT_A, row.get("object_id"));
    assertEquals(attachmentId, row.get("attachment_id"));
  }

  @Test
  void save_allowsSameAttachmentIdAcrossDifferentSources() {
    var attachmentId = UUID.randomUUID();
    seedAttachment(attachmentId);

    repository.save(SOURCE_A, OBJECT_A, attachmentId);
    repository.save(SOURCE_B, OBJECT_A, attachmentId);

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_attachment WHERE object_id = ? AND attachment_id = ?",
            Integer.class,
            OBJECT_A,
            attachmentId);

    assertEquals(2, count);
  }

  @Test
  void save_isIdempotentForDuplicateAssociationOnSameSourceAndObject() {
    var attachmentId = UUID.randomUUID();
    seedAttachment(attachmentId);

    repository.save(SOURCE_A, OBJECT_A, attachmentId);
    repository.save(SOURCE_A, OBJECT_A, attachmentId);

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM event_attachment WHERE source_id = ? AND object_id = ? AND attachment_id = ?",
            Integer.class,
            SOURCE_A,
            OBJECT_A,
            attachmentId);

    assertEquals(1, count);
  }

  private void seedUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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

  private void seedAttachment(UUID attachmentId) {
    jdbcTemplate.update(
        """
        INSERT INTO media_attachment (
          id, creator_id, owner_id, access, created_at, usage
        ) VALUES (?, ?, ?, CAST(? AS media_access), ?, CAST(? AS media_usage))
        """,
        attachmentId,
        USER_ID,
        USER_ID,
        "SCOPED",
        Timestamp.from(Instant.now()),
        "EVENT_DETAIL");
  }
}
