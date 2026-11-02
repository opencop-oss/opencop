// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.Remark;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = EventWebTestApplication.class)
class RemarksEndpointIT extends AbstractWebIT {

  private static final UUID SOURCE_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440130");
  private static final UUID SOURCE_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440131");
  private static final String OBJECT_ID = "event-123";
  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440132");

  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private UserSpecificManager userSpecificManager;
  @MockitoBean private DataSourceRepository dataSourceRepository;

  private EventFixtures eventFixtures;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM geo.remarks");
    jdbcTemplate.update("DELETE FROM event_attachment");
    jdbcTemplate.update("DELETE FROM media_file");
    jdbcTemplate.update("DELETE FROM media_attachment");
    jdbcTemplate.update("DELETE FROM geo.features");
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");

    seedUser(USER_ID);
    eventFixtures = new EventFixtures(jdbcTemplate, objectMapper);
    eventFixtures.insertFeature(
        UUID.randomUUID(), OBJECT_ID, SOURCE_A, Instant.parse("2026-03-12T10:00:00Z"));
    eventFixtures.insertFeature(
        UUID.randomUUID(), OBJECT_ID, SOURCE_B, Instant.parse("2026-03-12T10:05:00Z"));

    when(userSpecificManager.getUserServers(USER_ID))
        .thenReturn(List.of(new SourceRole(SOURCE_A, ServerRole.ADMIN, null)));
    when(userSpecificManager.getUserLayers(USER_ID)).thenReturn(List.of());
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(Optional.of(dataSourceWithArchivedAt(SOURCE_A, null)));
    when(dataSourceRepository.findById(eq(SOURCE_B)))
        .thenReturn(Optional.of(dataSourceWithArchivedAt(SOURCE_B, null)));
  }

  @Test
  void addRemark_persistsSourceScopedRow() throws Exception {
    mockMvc
        .perform(
            post("/v1/events/{sourceId}/{objectId}/remarks", SOURCE_A, OBJECT_ID)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID)))
                .contentType("application/json")
                .content("{\"content\":\"alpha\"}"))
        .andExpect(status().isOk());

    var row =
        jdbcTemplate.queryForMap(
            "SELECT source_id, object_id, content FROM geo.remarks WHERE source_id = ? AND object_id = ?",
            SOURCE_A,
            OBJECT_ID);

    assertEquals(SOURCE_A, row.get("source_id"));
    assertEquals(OBJECT_ID, row.get("object_id"));
    assertEquals("alpha", row.get("content"));
  }

  @Test
  void retrieveRemarks_filtersBySourceId() throws Exception {
    insertRemark(SOURCE_A, OBJECT_ID, "alpha");
    insertRemark(SOURCE_B, OBJECT_ID, "beta");

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/remarks", SOURCE_A, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var remarks = readResponse(result, new TypeReference<List<Remark>>() {});
    assertEquals(1, remarks.size());
    assertEquals("alpha", remarks.getFirst().content());
    assertEquals(OBJECT_ID, remarks.getFirst().objectId());
  }

  @Test
  void addRemark_whenSourceArchived_returnsConflictWithoutPersisting() throws Exception {
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(
            Optional.of(
                dataSourceWithArchivedAt(
                    SOURCE_A, Instant.parse("2026-03-12T10:00:00Z").plus(Duration.ofHours(1)))));

    mockMvc
        .perform(
            post("/v1/events/{sourceId}/{objectId}/remarks", SOURCE_A, OBJECT_ID)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(USER_ID)))
                .contentType("application/json")
                .content("{\"content\":\"alpha\"}"))
        .andExpect(status().isConflict());

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM geo.remarks WHERE source_id = ? AND object_id = ?",
            Integer.class,
            SOURCE_A,
            OBJECT_ID);
    assertEquals(0, count);
  }

  @Test
  void retrieveRemarks_whenSourceArchived_returnsOkWithPersistedRemark() throws Exception {
    when(dataSourceRepository.findById(eq(SOURCE_A)))
        .thenReturn(
            Optional.of(
                dataSourceWithArchivedAt(
                    SOURCE_A, Instant.parse("2026-03-12T10:00:00Z").plus(Duration.ofHours(1)))));
    insertRemark(SOURCE_A, OBJECT_ID, "alpha");

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/{objectId}/remarks", SOURCE_A, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(TestAuthentications.oidcUser(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var remarks = readResponse(result, new TypeReference<List<Remark>>() {});
    assertEquals(1, remarks.size());
    assertEquals("alpha", remarks.getFirst().content());
  }

  private void insertRemark(UUID sourceId, String objectId, String content) {
    jdbcTemplate.update(
        "INSERT INTO geo.remarks (id, source_id, object_id, content, user_id) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        sourceId,
        objectId,
        content,
        USER_ID);
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

  private DataSource dataSourceWithArchivedAt(UUID sourceId, Instant archivedAt) {
    return new SocketDataSource(
        sourceId,
        "source-" + sourceId,
        "tenant-a",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ,
        true,
        null,
        archivedAt,
        USER_ID,
        Instant.parse("2026-03-12T10:00:00Z"),
        null,
        Instant.parse("2026-03-12T10:00:00Z"),
        null,
        null,
        true);
  }
}
