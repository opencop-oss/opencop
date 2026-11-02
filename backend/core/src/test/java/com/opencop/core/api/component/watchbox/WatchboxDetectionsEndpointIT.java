// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.*;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository;
import com.opencop.core.api.watchbox.technical.endpoint.WatchboxDetectionResponse;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = WatchboxWebTestApplication.class)
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxDetectionsEndpointIT extends AbstractWebIT {
  private static final UUID VIEWER_ID = UUID.randomUUID(),
      ADMIN_ID = UUID.randomUUID(),
      SOURCE_ID = UUID.randomUUID(),
      HIDDEN_SOURCE_ID = UUID.randomUUID();
  private static final Authentication VIEWER = TestAuthentications.oidcUser(VIEWER_ID),
      ADMIN = TestAuthentications.oidcGlobalAdmin(ADMIN_ID);
  private static final Instant TIME = Instant.parse("2026-09-10T12:00:00Z");
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private IamServerLayerRepository serverLayerRepository;
  private ConnectionFixtures connections;
  private LayerFixtures layers;
  private EventFixtures events;
  private UUID boxA, boxB, boxHidden, boxArchived;

  @BeforeEach
  void seed() {
    connections = new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    layers =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    events = new EventFixtures(jdbcTemplate, objectMapper);
    events.reset();
    layers.reset();
    connections.reset();
    insertUser(VIEWER_ID);
    insertUser(ADMIN_ID);
    connections.seedDefaultMapper(WatchboxTestMapperIdentity.MAPPER_ID);
    connections.persistTcpSource(
        SOURCE_ID, "visible-source", WatchboxTestMapperIdentity.MAPPER_ID, true, null);
    connections.persistTcpSource(
        HIDDEN_SOURCE_ID, "hidden-source", WatchboxTestMapperIdentity.MAPPER_ID, true, null);
    var visible = layers.persistLayerWithMembers("visible", Map.of(VIEWER_ID, LayerRole.MEMBER));
    var hidden = layers.persistLayerWithMembers("hidden", Map.of());
    layers.addServerAccess(SOURCE_ID, visible, ServerRole.VIEWER);
    layers.addServerAccess(HIDDEN_SOURCE_ID, hidden, ServerRole.VIEWER);
    boxA = insertWatchbox("A", List.of(visible));
    boxB = insertWatchbox("B", List.of(visible));
    boxHidden = insertWatchbox("Hidden", List.of(visible, hidden));
    boxArchived = insertWatchbox("Archived", List.of(visible));
    jdbcTemplate.update(
        "UPDATE watchbox.watchbox SET archived_at=now(), archived_by=? WHERE id=?",
        ADMIN_ID,
        boxArchived);
  }

  @AfterEach
  void tearDown() {
    // The archived box references a user row; suites that run later reset users with a bare
    // DELETE, so leave the shared database as this suite found it.
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_detection_heartbeat");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    events.reset();
    layers.reset();
    connections.reset();
  }

  @Test
  void list_returnsNewestFirstAcrossVisibleBoxes_andPages() throws Exception {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 5; i++) ids.add(detection(i < 3 ? boxA : boxB, i));
    detection(boxHidden, 10);
    detection(boxHidden, 11);
    detection(boxArchived, 12);
    var result =
        mockMvc
            .perform(get("/v1/watchbox-detections?page=0&size=2").with(authentication(VIEWER)))
            .andExpect(status().isOk())
            .andReturn();
    var page =
        readResponse(result, new TypeReference<PageResponse<WatchboxDetectionResponse>>() {});
    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.totalPages()).isEqualTo(3);
    assertThat(page.content())
        .extracting(WatchboxDetectionResponse::id)
        .containsExactly(ids.get(4), ids.get(3));
    assertThat(page.content().getFirst())
        .isEqualTo(
            new WatchboxDetectionResponse(
                ids.get(4),
                boxB,
                1,
                SOURCE_ID,
                "track-4",
                ids.get(4),
                TIME,
                WatchboxTransitionKind.ENTER,
                "Truck 4",
                TIME.plusSeconds(4)));
    assertThat(list("?page=2&size=2").at("/content/0/id").asText())
        .isEqualTo(ids.getFirst().toString());
  }

  @Test
  void list_withWatchboxIdFilter_returnsOnlyThatBox() throws Exception {
    var id = detection(boxA, 1);
    detection(boxB, 2);
    var body = list("?watchboxId=" + boxA);
    assertThat(body.path("totalElements").asInt()).isEqualTo(1);
    assertThat(body.at("/content/0/id").asText()).isEqualTo(id.toString());
  }

  @Test
  void list_withHiddenWatchboxIdFilter_returnsWatchboxNotFound() throws Exception {
    missing("/v1/watchbox-detections?watchboxId=" + boxHidden);
  }

  @Test
  void list_withArchivedWatchboxIdFilter_returnsNotFound() throws Exception {
    missing("/v1/watchbox-detections?watchboxId=" + boxArchived);
  }

  @Test
  void list_withUnknownWatchboxIdFilter_returnsNotFound() throws Exception {
    missing("/v1/watchbox-detections?watchboxId=" + UUID.randomUUID());
  }

  @Test
  void list_includesOlderEpochRows_withTheirConfigEpoch() throws Exception {
    detection(boxA, 1);
    var id = UUID.randomUUID();
    insertDetection(id, boxA, 2, SOURCE_ID, "new", id, "ENTER", TIME, null, TIME.plusSeconds(2));
    jdbcTemplate.update("UPDATE watchbox.watchbox SET config_epoch=2 WHERE id=?", boxA);
    var body = list("");
    assertThat(body.at("/content/0/configEpoch").asInt()).isEqualTo(2);
    assertThat(body.at("/content/1/configEpoch").asInt()).isEqualTo(1);
  }

  @Test
  void list_pagingBounds_followTheRepositoryConvention() throws Exception {
    assertThat(list("?size=0").path("size").asInt()).isEqualTo(25);
    assertThat(list("?page=-1").path("page").asInt()).isZero();
    assertThat(list("?size=5000").path("size").asInt()).isEqualTo(2000);
  }

  @Test
  void list_withSort_returnsInvalidRequest() throws Exception {
    invalid("?sort=detectedAt,desc", "sort");
  }

  @Test
  void list_withUnknownKind_returnsInvalidRequest() throws Exception {
    invalid("?kind=BOGUS", "kind");
  }

  @Test
  void list_withKindFilter_returnsOnlyThoseKinds() throws Exception {
    int i = 0;
    for (var kind : WatchboxTransitionKind.values()) {
      var id = UUID.randomUUID();
      insertDetection(
          id,
          i++ % 2 == 0 ? boxA : boxB,
          1,
          SOURCE_ID,
          "track-" + i,
          id,
          kind.name(),
          TIME,
          null,
          TIME.plusSeconds(i));
    }
    var body = list("?kind=ENTER&kind=LEAVE");
    assertThat(body.path("totalElements").asInt()).isEqualTo(2);
    for (var row : body.path("content"))
      assertThat(row.path("kind").asText()).isIn("ENTER", "LEAVE");
    assertThat(list("?kind=ENTER&kind=LEAVE&watchboxId=" + boxA).path("totalElements").asInt())
        .isEqualTo(1);
  }

  @Test
  void list_asGlobalAdmin_seesEveryActiveBox() throws Exception {
    detection(boxA, 1);
    detection(boxB, 2);
    detection(boxHidden, 3);
    detection(boxArchived, 4);
    assertThat(request("/v1/watchbox-detections", ADMIN, 200).path("totalElements").asInt())
        .isEqualTo(3);
  }

  @Test
  void get_returnsRowWithBothEmbeds_andGeometry() throws Exception {
    var older = UUID.randomUUID();
    var newer = UUID.randomUUID();
    feature(older, SOURCE_ID, TIME, -97, 32);
    feature(newer, SOURCE_ID, TIME.plusSeconds(60), -98, 33);
    var id = eventDetection(SOURCE_ID, older);
    var body = request("/v1/watchbox-detections/" + id, VIEWER, 200);
    assertThat(body.at("/triggeringEvent/id").asText()).isEqualTo(older.toString());
    assertThat(body.at("/triggeringEvent/geometry/coordinates/0").asDouble()).isEqualTo(-97);
    assertThat(body.at("/triggeringEvent/geometry/coordinates/1").asDouble()).isEqualTo(32);
    assertThat(body.at("/latestEvent/id").asText()).isEqualTo(newer.toString());
    for (var key :
        List.of(
            "id",
            "watchboxId",
            "configEpoch",
            "sourceId",
            "objectId",
            "eventId",
            "effectiveEventTime",
            "kind",
            "objectDisplayName",
            "detectedAt")) assertThat(body.has(key)).as(key).isTrue();
  }

  @Test
  void get_whenTriggeringRevisionMissing_returnsNullEmbed() throws Exception {
    feature(UUID.randomUUID(), SOURCE_ID, TIME, 1, 2);
    var body =
        request(
            "/v1/watchbox-detections/" + eventDetection(SOURCE_ID, UUID.randomUUID()), VIEWER, 200);
    assertThat(body.path("triggeringEvent").isNull()).isTrue();
    assertThat(body.path("latestEvent").isObject()).isTrue();
  }

  @Test
  void get_whenNoFeatureRows_returnsBothEmbedsNull() throws Exception {
    assertNullEmbeds(
        request(
            "/v1/watchbox-detections/" + eventDetection(SOURCE_ID, UUID.randomUUID()),
            VIEWER,
            200));
  }

  @Test
  void get_whenStoredRevisionIsUnreadable_returnsRowWithNullEmbeds() throws Exception {
    var revision = UUID.randomUUID();
    feature(revision, SOURCE_ID, TIME, 1, 2);
    jdbcTemplate.update(
        "UPDATE geo.features SET payload = payload - 'properties' WHERE id=?", revision);
    assertNullEmbeds(
        request("/v1/watchbox-detections/" + eventDetection(SOURCE_ID, revision), VIEWER, 200));
  }

  @Test
  void get_whenStoredRevisionHasAnInvalidSourceValue_returnsRowWithNullEmbeds() throws Exception {
    var revision = UUID.randomUUID();
    feature(revision, SOURCE_ID, TIME, 1, 2);
    jdbcTemplate.update(
        "UPDATE geo.features SET payload = jsonb_set(payload, '{properties,opencop,source}', '\"not-a-uuid\"') WHERE id=?",
        revision);
    assertNullEmbeds(
        request("/v1/watchbox-detections/" + eventDetection(SOURCE_ID, revision), VIEWER, 200));
  }

  @Test
  void get_whenStoredFeatureIsMalformed_returnsRowWithNullEmbeds() throws Exception {
    var revision = UUID.randomUUID();
    feature(revision, SOURCE_ID, TIME, 1, 2);
    // Valid JSON that is not a GeoJSON feature: the row mapper cannot decode it.
    jdbcTemplate.update(
        "UPDATE geo.features SET payload = '{\"type\":\"Feature\",\"geometry\":\"nope\"}'::jsonb WHERE id=?",
        revision);
    assertNullEmbeds(
        request("/v1/watchbox-detections/" + eventDetection(SOURCE_ID, revision), VIEWER, 200));
  }

  @Test
  void get_whenLatestPayloadDisagreesWithItsColumns_returnsOnlyTheTriggeringEmbed()
      throws Exception {
    var triggering = UUID.randomUUID();
    var latest = UUID.randomUUID();
    feature(triggering, SOURCE_ID, TIME, 1, 2);
    feature(latest, SOURCE_ID, TIME.plusSeconds(60), 3, 4);
    jdbcTemplate.update(
        "UPDATE geo.features SET payload = jsonb_set(payload, '{properties,opencop,source}', to_jsonb(?::text)) WHERE id=?",
        UUID.randomUUID().toString(),
        latest);
    var body =
        request("/v1/watchbox-detections/" + eventDetection(SOURCE_ID, triggering), VIEWER, 200);
    assertThat(body.path("triggeringEvent").isObject()).isTrue();
    assertThat(body.path("latestEvent").isNull()).isTrue();
  }

  @Test
  void list_withEqualCreatedAt_ordersByIdDescending() throws Exception {
    var low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    var high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    insertDetection(low, boxA, 1, SOURCE_ID, "track-low", low, "ENTER", TIME, null, TIME);
    insertDetection(high, boxA, 1, SOURCE_ID, "track-high", high, "ENTER", TIME, null, TIME);
    var ids = new ArrayList<String>();
    list("").path("content").forEach(row -> ids.add(row.path("id").asText()));
    assertThat(ids).containsExactly(high.toString(), low.toString());
  }

  @Test
  void list_withCommaSeparatedKinds_filtersToThoseKinds() throws Exception {
    var i = 0;
    for (var kind : List.of("ENTER", "LEAVE", "FILTER_MATCH_GAINED")) {
      var id = UUID.randomUUID();
      insertDetection(
          id, boxA, 1, SOURCE_ID, "track-" + i, id, kind, TIME, null, TIME.plusSeconds(i++));
    }
    var body = list("?kind=ENTER,LEAVE");
    assertThat(body.path("totalElements").asInt()).isEqualTo(2);
    body.path("content")
        .forEach(row -> assertThat(row.path("kind").asText()).isIn("ENTER", "LEAVE"));
  }

  @Test
  void list_withBlankKind_appliesNoKindFilter() throws Exception {
    detection(boxA, 1);
    assertThat(list("?kind=").path("totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void get_whenCallerCannotViewSource_returnsRowWithNullEmbeds() throws Exception {
    var revision = UUID.randomUUID();
    feature(revision, HIDDEN_SOURCE_ID, TIME, 1, 2);
    var id = UUID.randomUUID();
    insertDetection(
        id, boxA, 1, HIDDEN_SOURCE_ID, "track-1", revision, "ENTER", TIME, "Truck hidden", TIME);
    var viewer = request("/v1/watchbox-detections/" + id, VIEWER, 200);
    assertNullEmbeds(viewer);
    // Row fields, including the display name, inherit box visibility; only the embeds are gated.
    assertThat(viewer.path("objectDisplayName").asText()).isEqualTo("Truck hidden");
    var admin = request("/v1/watchbox-detections/" + id, ADMIN, 200);
    assertThat(admin.path("triggeringEvent").isObject()).isTrue();
    assertThat(admin.path("latestEvent").isObject()).isTrue();
  }

  @Test
  void get_ofHiddenBoxDetection_returnsNotFound() throws Exception {
    missing("/v1/watchbox-detections/" + detection(boxHidden, 1));
  }

  @Test
  void get_ofArchivedBoxDetection_returnsNotFound() throws Exception {
    missing("/v1/watchbox-detections/" + detection(boxArchived, 1));
  }

  @Test
  void get_unknownId_returnsNotFound() throws Exception {
    missing("/v1/watchbox-detections/" + UUID.randomUUID());
  }

  @Test
  void get_whenWatchboxRowDeleted_cascadeRemovesDetection_returnsNotFound() throws Exception {
    var id = detection(boxB, 1);
    jdbcTemplate.update("DELETE FROM watchbox.watchbox WHERE id=?", boxB);
    missing("/v1/watchbox-detections/" + id);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM watchbox.watchbox_detection WHERE id=?", Integer.class, id))
        .isZero();
  }

  @Test
  void endpoints_whenUnauthenticated_returnUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/watchbox-detections")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/v1/watchbox-detections/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void generatedOpenApi_documentsPageAndSizeWithoutSort() throws Exception {
    var parameters =
        request("/v3/api-docs", VIEWER, 200)
            .path("paths")
            .path("/v1/watchbox-detections")
            .path("get")
            .path("parameters");
    var byName = new HashMap<String, JsonNode>();
    parameters.forEach(parameter -> byName.put(parameter.path("name").asText(), parameter));
    assertThat(byName.keySet()).containsExactlyInAnyOrder("watchboxId", "kind", "page", "size");
    assertThat(byName.get("page").path("schema").path("default").asInt()).isEqualTo(0);
    assertThat(byName.get("size").path("schema").path("default").asInt()).isEqualTo(25);
    assertThat(byName.get("size").path("schema").path("maximum").asInt()).isEqualTo(2000);
  }

  @Test
  void generatedOpenApi_detailExampleShowsTheEmbeddedEventGeometry() throws Exception {
    // The detail response is documented by its example only, so the example is where a reader
    // sees that an embedded event carries a geometry.
    var example =
        request("/v3/api-docs", VIEWER, 200)
            .path("paths")
            .path("/v1/watchbox-detections/{detectionId}")
            .path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("*/*")
            .path("example");
    assertThat(example.isMissingNode()).isFalse();
    assertThat(example.at("/latestEvent/geometry/type").asText()).isEqualTo("Point");
    assertThat(example.at("/latestEvent/geometry/coordinates").isArray()).isTrue();
  }

  private JsonNode list(String query) throws Exception {
    return request("/v1/watchbox-detections" + query, VIEWER, 200);
  }

  private JsonNode request(String url, Authentication auth, int expected) throws Exception {
    var result =
        mockMvc
            .perform(get(url).with(authentication(auth)))
            .andExpect(status().is(expected))
            .andReturn();
    return readResponse(result, JsonNode.class);
  }

  private void missing(String url) throws Exception {
    var result =
        mockMvc
            .perform(get(url).with(authentication(VIEWER)))
            .andExpect(status().isNotFound())
            .andReturn();
    assertThat(readResponse(result, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_NOT_FOUND);
  }

  private void invalid(String query, String field) throws Exception {
    var body = request("/v1/watchbox-detections" + query, VIEWER, 400);
    assertThat(body.path("code").asText()).isEqualTo("WATCHBOX_INVALID_REQUEST");
    assertThat(body.path("fields").toString()).contains(field);
  }

  private void assertNullEmbeds(JsonNode body) {
    assertThat(body.path("triggeringEvent").isNull()).isTrue();
    assertThat(body.path("latestEvent").isNull()).isTrue();
  }

  private UUID detection(UUID box, int ordinal) {
    var id = UUID.randomUUID();
    insertDetection(
        id,
        box,
        1,
        SOURCE_ID,
        "track-" + ordinal,
        id,
        "ENTER",
        TIME,
        "Truck " + ordinal,
        TIME.plusSeconds(ordinal));
    return id;
  }

  private UUID eventDetection(UUID source, UUID revision) {
    var id = UUID.randomUUID();
    insertDetection(id, boxA, 1, source, "track-1", revision, "ENTER", TIME, null, TIME);
    return id;
  }

  private void feature(UUID id, UUID source, Instant time, double lon, double lat) {
    events.insertFeature(id, "track-1", source, time, time, lon, lat);
    jdbcTemplate.update(
        "UPDATE geo.features SET payload=jsonb_set(payload,'{properties,opencop,id}',to_jsonb(?::text)) WHERE id=?",
        id.toString(),
        id);
  }

  private void insertDetection(
      UUID id,
      UUID watchboxId,
      long epoch,
      UUID sourceId,
      String objectId,
      UUID eventId,
      String kind,
      Instant eventTime,
      String displayName,
      Instant createdAt) {
    jdbcTemplate.update(
        "INSERT INTO watchbox.watchbox_detection (id,watchbox_id,config_epoch,source_id,object_id,event_id,transition,event_time,object_display_name,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
        id,
        watchboxId,
        epoch,
        sourceId,
        objectId,
        eventId,
        kind,
        Timestamp.from(eventTime),
        displayName,
        Timestamp.from(createdAt));
  }

  private UUID insertWatchbox(String name, List<UUID> layerIds) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO watchbox.watchbox (id, name, direction, polygon_json)
        VALUES (?, ?, 'BOTH', '{"type":"Polygon","coordinates":[[[0,0],[0,1],[1,1],[1,0],[0,0]]]}'::jsonb)
        """,
        id,
        name);
    for (UUID layerId : layerIds) {
      jdbcTemplate.update(
          "INSERT INTO watchbox.watchbox_layer (watchbox_id, layer_id) VALUES (?, ?)", id, layerId);
    }
    return id;
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId);
  }
}
