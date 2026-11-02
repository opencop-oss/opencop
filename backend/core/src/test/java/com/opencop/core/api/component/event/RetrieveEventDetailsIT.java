// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.event;

import static com.opencop.core.api.component.support.assertion.EventAssertions.assertEventDetails;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.EventFixtures;
import com.opencop.core.api.iam.ports.usecase.access.me.UserSpecificManager;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.source.SourceRole;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.technical.gateway.audit.LastEventAtCoalescer;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ComponentWebIT(classes = EventWebTestApplication.class)
class RetrieveEventDetailsIT extends AbstractWebIT {

  private static final String OBJECT_ID = "event-123";
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
  private static final Instant EARLIER_EVENT_TIME = Instant.parse("2026-03-12T10:00:00Z");
  private static final Instant LATEST_EVENT_TIME = Instant.parse("2026-03-12T11:00:00Z");
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin();

  private EventFixtures eventFixtures;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private LastEventAtCoalescer layerLastEventRecorder;
  @MockitoBean private UserSpecificManager userSpecificManager;

  @BeforeEach
  void setUpData() {
    eventFixtures = new EventFixtures(jdbcTemplate, objectMapper);
    eventFixtures.reset();
    when(userSpecificManager.getUserServers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new SourceRole(SOURCE_ID, ServerRole.VIEWER, null)));
    when(userSpecificManager.getUserLayers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
  }

  // Happy path
  @Test
  void nonAdminCanGetEventDetails() throws Exception {
    var auth = TestAuthentications.oidcUser(UUID.randomUUID());
    var featureId = UUID.randomUUID();
    eventFixtures.insertFeature(featureId, OBJECT_ID, SOURCE_ID, EARLIER_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{id}/details", featureId).with(csrf()).with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, OpenCopProperties.class);
    assertEventDetails(response, OBJECT_ID, SOURCE_ID);
  }

  @Test
  void adminCanGetEventDetails() throws Exception {
    var featureId = UUID.randomUUID();
    eventFixtures.insertFeature(featureId, OBJECT_ID, SOURCE_ID, EARLIER_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{id}/details", featureId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, OpenCopProperties.class);
    assertEventDetails(response, OBJECT_ID, SOURCE_ID);
  }

  // Missing resource
  @Test
  void getEventDetails_whenMissing_returnsNotFound() throws Exception {
    var auth = TestAuthentications.oidcUser(UUID.randomUUID());

    var result =
        mockMvc
            .perform(
                get("/v1/events/{id}/details", UUID.randomUUID())
                    .with(csrf())
                    .with(authentication(auth)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().contains("Does not exist"));
  }

  @Test
  void nonAdminCanGetLatestEventDetails() throws Exception {
    var auth = TestAuthentications.oidcUser(UUID.randomUUID());
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SOURCE_ID, EARLIER_EVENT_TIME);
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        OBJECT_ID,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        LATEST_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/object/{objectId}/details/latest", SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, OpenCopProperties.class);
    assertEventDetails(response, OBJECT_ID, SOURCE_ID);
  }

  @Test
  void adminCanGetLatestEventDetails() throws Exception {
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SOURCE_ID, EARLIER_EVENT_TIME);
    eventFixtures.insertFeature(
        UUID.randomUUID(),
        OBJECT_ID,
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        LATEST_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/object/{objectId}/details/latest", SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, OpenCopProperties.class);
    assertEventDetails(response, OBJECT_ID, SOURCE_ID);
  }

  // Missing resource
  @Test
  void getLatestEventDetails_whenMissing_returnsNotFound() throws Exception {
    var auth = TestAuthentications.oidcUser(UUID.randomUUID());

    var result =
        mockMvc
            .perform(
                get(
                        "/v1/events/{sourceId}/object/{objectId}/details/latest",
                        SOURCE_ID,
                        "missing-event")
                    .with(csrf())
                    .with(authentication(auth)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertTrue(error.message().contains("No feature found for object_id"));
  }

  // Auth
  @Test
  void getEventDetails_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/v1/events/{id}/details", UUID.randomUUID()).with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getLatestEventDetails_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/v1/events/{sourceId}/object/{objectId}/details/latest", SOURCE_ID, OBJECT_ID)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getLatestEventDetails_filtersBySourceWhenObjectIdExistsInAnotherSource() throws Exception {
    var auth = TestAuthentications.oidcUser(UUID.randomUUID());
    var otherSource = UUID.randomUUID();
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, otherSource, LATEST_EVENT_TIME);
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SOURCE_ID, EARLIER_EVENT_TIME);

    var result =
        mockMvc
            .perform(
                get("/v1/events/{sourceId}/object/{objectId}/details/latest", SOURCE_ID, OBJECT_ID)
                    .with(csrf())
                    .with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, OpenCopProperties.class);
    assertEventDetails(response, OBJECT_ID, SOURCE_ID);
  }

  // ── Layer usage marking ────────────────────────────────────────────────────

  @Test
  void openingAnObjectMarksItsLayerAsUsed() throws Exception {
    var layerId = insertLayer();
    when(userSpecificManager.getUserServers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new SourceRole(layerId, ServerRole.VIEWER, null)));
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, layerId, LATEST_EVENT_TIME);

    mockMvc
        .perform(
            get("/v1/events/{sourceId}/object/{objectId}/details/latest", layerId, OBJECT_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
    layerLastEventRecorder.flushAll();

    assertNotNull(layerLastEventAt(layerId));
  }

  @Test
  void openingAnObjectByIdMarksItsLayerAsUsed() throws Exception {
    // The by-id endpoint carries its own copy of the authorize-then-record block, so it needs its
    // own proof — a change to one handler must not be assumed to cover the other.
    var layerId = insertLayer();
    var featureId = UUID.randomUUID();
    when(userSpecificManager.getUserServers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new SourceRole(layerId, ServerRole.VIEWER, null)));
    eventFixtures.insertFeature(featureId, OBJECT_ID, layerId, LATEST_EVENT_TIME);

    mockMvc
        .perform(
            get("/v1/events/{id}/details", featureId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
    layerLastEventRecorder.flushAll();

    assertNotNull(layerLastEventAt(layerId));
  }

  @Test
  void openingAnObjectDoesNotForgeAnEditOnItsLayer() throws Exception {
    var layerId = insertLayer();
    when(userSpecificManager.getUserServers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(new SourceRole(layerId, ServerRole.VIEWER, null)));
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, layerId, LATEST_EVENT_TIME);

    mockMvc
        .perform(
            get("/v1/events/{sourceId}/object/{objectId}/details/latest", layerId, OBJECT_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
    layerLastEventRecorder.flushAll();

    assertNull(
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM iam.layer WHERE id = ?", Timestamp.class, layerId),
        "viewing a layer is not editing it; going through JPA here would stamp updated_at");
  }

  @Test
  void anUnauthorizedRequestMarksNothing() throws Exception {
    var layerId = insertLayer();
    // The caller holds no role on this layer, so assertMember rejects before any marking runs.
    when(userSpecificManager.getUserServers(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, layerId, LATEST_EVENT_TIME);

    mockMvc
        .perform(
            get("/v1/events/{sourceId}/object/{objectId}/details/latest", layerId, OBJECT_ID)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser(UUID.randomUUID()))))
        .andExpect(status().isForbidden());
    layerLastEventRecorder.flushAll();

    assertNull(layerLastEventAt(layerId));
  }

  @Test
  void openingAnObjectOnADataSourceMarksNoLayer() throws Exception {
    var layerId = insertLayer();
    eventFixtures.insertFeature(UUID.randomUUID(), OBJECT_ID, SOURCE_ID, LATEST_EVENT_TIME);

    // Event sources are polymorphic. A data source id reaches the same writer and must simply
    // match no layer row rather than fail the request.
    mockMvc
        .perform(
            get("/v1/events/{sourceId}/object/{objectId}/details/latest", SOURCE_ID, OBJECT_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
    layerLastEventRecorder.flushAll();

    assertNull(layerLastEventAt(layerId));
  }

  private UUID insertLayer() {
    var layerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO iam.layer (id, name) VALUES (?, ?)", layerId, "layer-" + layerId);
    return layerId;
  }

  /**
   * Reads a layer's activity the way the production readers do — outward from {@code iam.layer} —
   * so a layer with no activity yields null rather than no row at all. Querying the projection
   * directly would make {@code queryForObject} throw for the never-active case the tests assert on.
   */
  private Timestamp layerLastEventAt(UUID layerId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT ra.last_event_at
          FROM iam.layer l
          LEFT JOIN activity.resource_activity ra
                 ON ra.resource_kind = 'LAYER'
                AND ra.resource_id = l.id
         WHERE l.id = ?
        """,
        Timestamp.class,
        layerId);
  }
}
