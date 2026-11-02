// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.opencop.core.api.event.ports.business.usecase.RemarkService;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.technical.model.opencop.PersistRemarkRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@ComponentWebIT(classes = EventStorageTestApplication.class)
class RemarkRepositoryIT extends AbstractDatabaseIT {

  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440120");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440121");
  private static final String OBJECT_A = "event-123";
  private static final String OBJECT_B = "event-456";
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440122");

  @Autowired private RemarkService remarkService;
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
  void addRemark_persistsSourceIdAndObjectId() {
    remarkService.addRemark(new PersistRemarkRequest(SOURCE_A, OBJECT_A, "alpha", USER_ID));

    var row =
        jdbcTemplate.queryForMap(
            "SELECT source_id, object_id, content, user_id FROM geo.remarks WHERE source_id = ? AND object_id = ?",
            SOURCE_A,
            OBJECT_A);

    assertEquals(SOURCE_A, row.get("source_id"));
    assertEquals(OBJECT_A, row.get("object_id"));
    assertEquals("alpha", row.get("content"));
    assertEquals(USER_ID, row.get("user_id"));
  }

  @Test
  void retrieveRemarks_returnsOnlyMatchingSourceAndObjectId() throws Exception {
    remarkService.addRemark(new PersistRemarkRequest(SOURCE_A, OBJECT_A, "alpha", USER_ID));
    remarkService.addRemark(new PersistRemarkRequest(SOURCE_B, OBJECT_A, "beta", USER_ID));
    remarkService.addRemark(new PersistRemarkRequest(SOURCE_A, OBJECT_B, "gamma", USER_ID));

    var result = remarkService.retrieveRemarks(SOURCE_A, OBJECT_A);

    assertEquals(1, result.size());
    assertEquals(OBJECT_A, result.getFirst().objectId());
    assertEquals("alpha", result.getFirst().content());
    assertNotNull(result.getFirst().contact());
    assertEquals(USER_ID, result.getFirst().contact().getUserId());
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
}
