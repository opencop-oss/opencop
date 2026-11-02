// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.MapTileFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.ports.usecase.map.MapTileExceptionCodes;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileSecretRef;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileMemberRepository;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileUserGroupRepository;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.RasterConfig;
import com.opencop.core.sdk.business.model.map.tile.TileKind;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import com.opencop.core.sdk.business.model.map.tile.VectorConfig;
import com.opencop.core.sdk.business.model.map.tile.WeatherTileExtension;
import com.opencop.core.sdk.business.model.map.tile.WmtsConfig;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileMember;
import com.opencop.core.sdk.business.model.map.tile.access.MapTileUserGroupGrant;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTileConfigPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.business.model.map.tile.patch.RasterConfigPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.WeatherTileExtensionPatch;
import com.opencop.core.sdk.business.model.map.tile.weather.WeatherFeatureConfig;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = MapTileWebTestApplication.class)
class MapTileEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_USER_ID = UUID.randomUUID();

  /**
   * ROLE_admin satisfies the @PreAuthorize routes. The principal's own global-administrator flag is
   * what isGlobalAdmin() reads, and the per-tile checks use that, so both are needed.
   */
  private static final Authentication ADMIN_AUTH =
      new UsernamePasswordAuthenticationToken(
          TestPrincipals.oidcPrincipal(ADMIN_USER_ID, true),
          null,
          List.of(new SimpleGrantedAuthority(Roles.ADMIN)));

  private static final Authentication USER_AUTH = TestAuthentications.oidcUser();

  /** A non-admin who exists in opencop_user, so grants to them can be listed with identity. */
  private static final UUID MEMBER_USER_ID = UUID.randomUUID();

  private static final Authentication MEMBER_AUTH = TestAuthentications.oidcUser(MEMBER_USER_ID);

  @Autowired private MapTileRepository mapTileRepository;

  @Autowired private MapTileMemberRepository memberRepository;

  @Autowired private MapTileUserGroupRepository userGroupRepository;

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

  @MockitoBean private SecretStore secretStore;

  private MapTileFixtures mapTileFixtures;

  @BeforeEach
  void setUp() {
    mapTileFixtures = new MapTileFixtures(mapTileRepository);
    mapTileFixtures.reset();
    jdbc.update("DELETE FROM iam.user_group_member");
    jdbc.update("DELETE FROM iam.user_group");
    UserFixtures.releaseUserReferences(jdbc);
    jdbc.update("DELETE FROM opencop_user");
    insertUser(MEMBER_USER_ID);
    insertUser(ADMIN_USER_ID);
  }

  private void insertUser(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email,
          first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "https://issuer.test",
        "user-" + userId,
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  private UUID insertUserGroup(String name) {
    var groupId = UUID.randomUUID();
    jdbc.update("INSERT INTO iam.user_group (id, name) VALUES (?, ?)", groupId, name);
    return groupId;
  }

  private void insertGroupMembership(UUID groupId, UUID userId) {
    jdbc.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)", groupId, userId);
  }

  // Auth
  @Test
  void getAllTiles_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/map/tiles")).andExpect(status().isUnauthorized());
  }

  @Test
  void createTile_withoutCsrf_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/map/tiles")
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(MapTileFixtures.defaultRasterCreate())))
        .andExpect(status().isForbidden());
  }

  @Test
  void createTile_asNonAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/map/tiles")
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(MapTileFixtures.defaultRasterCreate())))
        .andExpect(status().isForbidden());
  }

  // Happy path
  @Test
  void adminCanCreateTileAndReadItBack() throws Exception {
    var create = MapTileFixtures.defaultRasterCreate();

    var createResult =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    var created = readResponse(createResult, MapTile.class);
    var createdId = uuidFromLocation(createResult);
    var tokenRef = "opencop/core/secrets/map/tiles/" + createdId;

    assertEquals(createdId, created.getId());
    // Not promoted: this fixture creates a PRIVATE tile, and only a GLOBAL basemap is
    // auto-promoted.
    // Promotion has its own tests below.
    assertFalse(created.isPrimary());
    assertEquals("Operations raster", created.getName());
    assertEquals("Operations base raster", created.getDescription());
    assertEquals("https://tiles.example.com/{z}/{x}/{y}.png", created.getUrl());
    assertInstanceOf(RasterConfig.class, created.getConfig().getTile());
    assertNull(created.getToken());

    verify(secretStore).patch(eq(tokenRef), eq(Map.of("token", "super-secret-token")), eq(null));

    when(secretStore.read(tokenRef)).thenReturn(Map.of("token", "super-secret-token"));

    var readResult =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", createdId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var readBack = readResponse(readResult, MapTile.class);
    assertEquals(createdId, readBack.getId());
    assertEquals("super-secret-token", readBack.getToken());
  }

  @Test
  void adminCanPatchTile() throws Exception {
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Before",
            "Before description",
            "https://tiles.example.com/before/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            MapTileSecretRef.fromTileId(UUID.randomUUID()));

    var patch =
        new MapTilePatch(
            Optional.of("After"),
            Optional.of("https://tiles.example.com/after/{z}/{x}/{y}.png"),
            Optional.of("After description"),
            Optional.of("rotated-token"),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new MapTileConfigPatch(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(
                        new RasterConfigPatch(
                            Optional.of(512), Optional.empty(), Optional.of(20))))),
            Optional.empty());

    mockMvc
        .perform(
            patch("/v1/map/tiles/{id}", tileId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patch)))
        .andExpect(status().isOk());

    verify(secretStore)
        .patch(
            eq("opencop/core/secrets/map/tiles/" + tileId),
            eq(Map.of("token", "rotated-token")),
            eq(null));

    var entity = mapTileFixtures.load(tileId);
    assertEquals("After", entity.getName());
    assertEquals("After description", entity.getDescription());
    assertEquals("https://tiles.example.com/after/{z}/{x}/{y}.png", entity.getUrl());
    // The row keeps only the secret's reference, never the token itself.
    assertEquals("opencop/core/secrets/map/tiles/" + tileId, entity.getTokenRef());
    assertInstanceOf(RasterConfig.class, entity.getConfig().getTile());
    assertEquals(512, ((RasterConfig) entity.getConfig().getTile()).getTileSize());
    assertEquals(20, ((RasterConfig) entity.getConfig().getTile()).getMaxZoom());
  }

  @Test
  void adminCanCreateTileWithAttributionAndReadItBack() throws Exception {
    var base = MapTileFixtures.defaultRasterCreate();
    var create =
        new MapTileCreate(
            base.name(),
            base.url(),
            base.description(),
            null,
            base.accessScope(),
            base.config(),
            "  <a href=\"https://maps.example.org/copyright\">© Example Maps contributors</a> ");

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    var expected = "<a href=\"https://maps.example.org/copyright\">© Example Maps contributors</a>";
    var created = readResponse(result, MapTile.class);
    assertEquals(expected, created.getAttribution());
    assertEquals(expected, mapTileFixtures.load(created.getId()).getAttribution());

    var readBack =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", created.getId()).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(expected, readResponse(readBack, MapTile.class).getAttribution());
  }

  @Test
  void attributionPatchSetsKeepsAndClearsWithABlankString() throws Exception {
    var tileId = persistAttributionTile();

    assertEquals(200, patchRaw(tileId, "{\"attribution\":\"© Example imagery\"}"));
    assertEquals("© Example imagery", mapTileFixtures.load(tileId).getAttribution());

    assertEquals(200, patchRaw(tileId, "{}"));
    assertEquals("© Example imagery", mapTileFixtures.load(tileId).getAttribution());

    // JSON null reads as an absent field on this API, so it leaves the credit alone.
    assertEquals(200, patchRaw(tileId, "{\"attribution\":null}"));
    assertEquals("© Example imagery", mapTileFixtures.load(tileId).getAttribution());

    assertEquals(200, patchRaw(tileId, "{\"attribution\":\"  \"}"));
    assertNull(mapTileFixtures.load(tileId).getAttribution());
  }

  @Test
  void attributionLongerThanTheLimitIsRejected() throws Exception {
    var tileId = persistAttributionTile();

    var result =
        mockMvc
            .perform(
                patch("/v1/map/tiles/{id}", tileId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"attribution\":\""
                            + "x".repeat(MapTile.MAX_ATTRIBUTION_LENGTH + 1)
                            + "\"}"))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.ATTRIBUTION_TOO_LONG.code(), error.code());
    assertNull(mapTileFixtures.load(tileId).getAttribution());
  }

  @Test
  void renamingToATakenNameIsAConflictAndLeavesTheStoredTokenAlone() throws Exception {
    mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        false,
        "Taken name",
        "Taken description",
        "https://tiles.example.com/taken/{z}/{x}/{y}.png",
        new RasterConfig(256, 0, 18),
        null);
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        false,
        "Keyed",
        "Keyed description",
        "https://tiles.example.com/keyed/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        tokenRef);

    var result = patchJson(tileId, "{\"name\":\"Taken name\",\"token\":\"\"}");

    assertEquals(409, result.getResponse().getStatus());
    assertEquals(
        MapTileExceptionCodes.NAME_TAKEN.code(),
        readResponse(result, GlobalErrorResponse.class).code());
    verify(secretStore, never()).deleteAllVersions(tokenRef);
    var entity = mapTileFixtures.load(tileId);
    assertEquals("Keyed", entity.getName());
    assertEquals(tokenRef, entity.getTokenRef());

    // A tile keeps its own name without a conflict.
    assertEquals(200, patchRaw(tileId, "{\"name\":\"Keyed\"}"));
  }

  @Test
  void creatingATileWithATakenNameIsAConflictAndWritesNoSecret() throws Exception {
    var create = MapTileFixtures.defaultRasterCreate();
    mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        false,
        create.name(),
        "Existing description",
        "https://tiles.example.com/existing/{z}/{x}/{y}.png",
        new RasterConfig(256, 0, 18),
        null);

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isConflict())
            .andReturn();

    assertEquals(
        MapTileExceptionCodes.NAME_TAKEN.code(),
        readResponse(result, GlobalErrorResponse.class).code());
    assertEquals(1, mapTileRepository.count());
    verify(secretStore, never()).patch(any(), any(), any());
  }

  @Test
  void aClearedTokenReadsAsNoneEvenIfASecretIsStillStored() throws Exception {
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        true,
        "Cleared",
        "Cleared description",
        "https://tiles.example.com/cleared/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        tokenRef);
    assertEquals(200, patchRaw(tileId, "{\"token\":\"\"}"));
    // As if deleting the secret had failed after the row was saved.
    when(secretStore.read(tokenRef)).thenReturn(Map.of("token", "stale-secret"));

    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(readResponse(result, MapTile.class).getToken());
  }

  @Test
  void aReferenceWhoseSecretIsGoneReadsAsNoToken() throws Exception {
    // As if the secret was deleted and the transaction that cleared the row then failed.
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        true,
        "Orphaned reference",
        "Orphaned reference description",
        "https://tiles.example.com/orphaned/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        tokenRef);

    for (var secret :
        Arrays.<Map<String, String>>asList(null, Map.of(), Map.of("other", "value"))) {
      when(secretStore.read(tokenRef)).thenReturn(secret);
      var result =
          mockMvc
              .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(ADMIN_AUTH)))
              .andExpect(status().isOk())
              .andReturn();
      assertNull(readResponse(result, MapTile.class).getToken(), "secret " + secret);
    }
  }

  @Test
  void blankTokenClearsTheStoredTokenAndItsReference() throws Exception {
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        true,
        "Token clear",
        "Token clear description",
        "https://tiles.example.com/token/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        tokenRef);

    // JSON null reads as an absent field, so it leaves the token alone.
    assertEquals(200, patchRaw(tileId, "{\"token\":null}"));
    assertEquals(tokenRef, mapTileFixtures.load(tileId).getTokenRef());
    verify(secretStore, never()).deleteAllVersions(tokenRef);

    assertEquals(200, patchRaw(tileId, "{\"token\":\"\"}"));
    assertNull(mapTileFixtures.load(tileId).getTokenRef());
    verify(secretStore).deleteAllVersions(tokenRef);

    // Whitespace alone is blank too.
    assertEquals(200, patchRaw(tileId, "{\"token\":\"   \"}"));
    assertNull(mapTileFixtures.load(tileId).getTokenRef());
    verify(secretStore, times(2)).deleteAllVersions(tokenRef);
    verify(secretStore, never()).patch(any(), any(), any());
  }

  @Test
  void aBlankTokenOnCreateStoresNoSecretAndNoReference() throws Exception {
    var base = MapTileFixtures.defaultRasterCreate();
    for (var blank : List.of("", "   ")) {
      var create =
          new MapTileCreate(
              base.name() + " " + blank.length(),
              base.url(),
              base.description(),
              blank,
              base.accessScope(),
              base.config(),
              null);

      var result =
          mockMvc
              .perform(
                  post("/v1/admin/map/tiles")
                      .with(csrf())
                      .with(authentication(ADMIN_AUTH))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsBytes(create)))
              .andExpect(status().isCreated())
              .andReturn();

      var tileId = uuidFromLocation(result);
      assertNull(mapTileFixtures.load(tileId).getTokenRef());
      var readBack =
          mockMvc
              .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(ADMIN_AUTH)))
              .andExpect(status().isOk())
              .andReturn();
      assertNull(readResponse(readBack, MapTile.class).getToken());
    }
    verify(secretStore, never()).patch(any(), any(), any());
  }

  @Test
  void anOverlongCreditOnCreateIsRejectedBeforeAnythingIsStored() throws Exception {
    var base = MapTileFixtures.defaultRasterCreate();
    var create =
        new MapTileCreate(
            base.name(),
            base.url(),
            base.description(),
            base.token(),
            base.accessScope(),
            base.config(),
            "x".repeat(1001));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.ATTRIBUTION_TOO_LONG.code(), error.code());
    assertEquals(0, mapTileRepository.count());
    verify(secretStore, never()).patch(any(), any(), any());
  }

  @Test
  void aBlankCreditOnCreateIsStoredAsNone() throws Exception {
    var base = MapTileFixtures.defaultRasterCreate();
    var create =
        new MapTileCreate(
            base.name(),
            base.url(),
            base.description(),
            null,
            base.accessScope(),
            base.config(),
            "   ");

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    assertNull(readResponse(result, MapTile.class).getAttribution());
    assertNull(mapTileFixtures.load(uuidFromLocation(result)).getAttribution());
  }

  @Test
  void aCreditAtTheLimitIsAcceptedOnceStripped() throws Exception {
    // Literal on purpose: these pin the API contract independently of the shared constant.
    var tileId = persistAttributionTile();
    var atLimit = "x".repeat(1000);
    assertEquals(200, patchRaw(tileId, "{\"attribution\":\"" + atLimit + "\"}"));
    assertEquals(atLimit, mapTileFixtures.load(tileId).getAttribution());

    var padded = "y".repeat(1000);
    assertEquals(200, patchRaw(tileId, "{\"attribution\":\"  " + padded + "  \"}"));
    assertEquals(padded, mapTileFixtures.load(tileId).getAttribution());
  }

  // These prove the flushed row edit rolls back when the secret store then fails; they say
  // nothing about the secret store's own state.
  @Test
  void aFailedSecretDeleteRollsBackTheRowEdit() throws Exception {
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        false,
        "Keyed",
        "Keyed description",
        "https://tiles.example.com/keyed/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        tokenRef);
    doThrow(new IllegalStateException("secret store unavailable"))
        .when(secretStore)
        .deleteAllVersions(tokenRef);

    var result = patchJson(tileId, "{\"name\":\"Renamed\",\"token\":\"\"}");

    assertEquals(500, result.getResponse().getStatus());
    assertEquals("INTERNAL_ERROR", readResponse(result, GlobalErrorResponse.class).code());
    verify(secretStore).deleteAllVersions(tokenRef);
    var entity = mapTileFixtures.load(tileId);
    assertEquals("Keyed", entity.getName());
    assertEquals(tokenRef, entity.getTokenRef());
  }

  @Test
  void aFailedSecretWriteRollsBackTheRowEdit() throws Exception {
    var tileId = UUID.randomUUID();
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    mapTileFixtures.persistMapTile(
        tileId,
        false,
        "Unkeyed",
        "Unkeyed description",
        "https://tiles.example.com/unkeyed/{z}/{x}/{y}.png?key={key}",
        new RasterConfig(256, 0, 18),
        null);
    doThrow(new IllegalStateException("secret store unavailable"))
        .when(secretStore)
        .patch(eq(tokenRef), any(), any());

    var result = patchJson(tileId, "{\"name\":\"Renamed\",\"token\":\"new-token\"}");

    assertEquals(500, result.getResponse().getStatus());
    assertEquals("INTERNAL_ERROR", readResponse(result, GlobalErrorResponse.class).code());
    verify(secretStore).patch(eq(tokenRef), eq(Map.of("token", "new-token")), eq(null));
    var entity = mapTileFixtures.load(tileId);
    assertEquals("Unkeyed", entity.getName());
    assertNull(entity.getTokenRef());
  }

  private UUID persistAttributionTile() {
    return mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        true,
        "Attribution",
        "Attribution description",
        "https://tiles.example.com/attribution/{z}/{x}/{y}.png",
        new RasterConfig(256, 0, 18),
        null);
  }

  private int patchRaw(UUID tileId, String json) throws Exception {
    return patchJson(tileId, json).getResponse().getStatus();
  }

  private MvcResult patchJson(UUID tileId, String json) throws Exception {
    return mockMvc
        .perform(
            patch("/v1/map/tiles/{id}", tileId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  @Test
  void adminCanDeleteTile() throws Exception {
    var primaryId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/primary/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            "opencop/core/secrets/map/tiles/primary");
    var fallbackId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            "Fallback vector",
            "Fallback vector description",
            "https://tiles.example.com/fallback/{z}/{x}/{y}.pbf",
            new VectorConfig(512, 1, 16),
            null);

    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", primaryId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    verify(secretStore).deleteAllVersions("opencop/core/secrets/map/tiles/" + primaryId);
    assertFalse(mapTileRepository.findById(primaryId).isPresent());
    assertEquals(fallbackId, mapTileFixtures.loadPrimary().getId());
  }

  @Test
  void adminCanSetPrimaryTile() throws Exception {
    var primaryId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Current primary",
            "Current primary description",
            "https://tiles.example.com/current/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);
    var targetId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            "Target primary",
            "Target primary description",
            "https://tiles.example.com/target/{z}/{x}/{y}.png",
            new RasterConfig(512, 2, 19),
            null);

    mockMvc
        .perform(
            put("/v1/admin/map/tiles/{id}/primary", targetId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    assertFalse(mapTileFixtures.load(primaryId).isPrimary());
    assertTrue(mapTileFixtures.load(targetId).isPrimary());
  }

  @Test
  void adminCannotSetWeatherTileAsPrimary() throws Exception {
    var primaryId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Current primary",
            "Current primary description",
            "https://tiles.example.com/current/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);
    var weatherId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            "Weather layer",
            "Weather layer description",
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
            weatherWmtsConfig(),
            null);

    var result =
        mockMvc
            .perform(
                put("/v1/admin/map/tiles/{id}/primary", weatherId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP.code(), error.code());
    assertEquals("Primary tile must be a basemap", error.message());
    assertTrue(mapTileFixtures.load(primaryId).isPrimary());
    assertFalse(mapTileFixtures.load(weatherId).isPrimary());
  }

  // Access edge cases
  @Test
  void authenticatedUserCanReadSanitizedTiles() throws Exception {
    var tokenRef = "opencop/core/secrets/map/tiles/" + UUID.randomUUID();
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/public/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            tokenRef);

    var allResult =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var tiles = readResponse(allResult, new TypeReference<List<MapTile>>() {});
    assertEquals(1, tiles.size());
    assertEquals(tileId, tiles.getFirst().getId());
    assertNull(tiles.getFirst().getToken());

    var primaryResult =
        mockMvc
            .perform(get("/v1/map/tiles/primary").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var primary = readResponse(primaryResult, MapTile.class);
    assertEquals(tileId, primary.getId());
    assertEquals(tokenRef, primary.getToken());
  }

  @Test
  void getOneLoadsSecretTokenForAuthenticatedUser() throws Exception {
    var tileId = UUID.randomUUID();
    var tokenRef = "opencop/core/secrets/map/tiles/" + tileId;
    mapTileFixtures.persistMapTile(
        tileId,
        true,
        "Secured raster",
        "Secured raster description",
        "https://tiles.example.com/secure/{z}/{x}/{y}.png",
        new RasterConfig(256, 0, 18),
        tokenRef);
    when(secretStore.read(tokenRef)).thenReturn(Map.of("token", "resolved-secret"));

    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var tile = readResponse(result, MapTile.class);
    assertEquals(tileId, tile.getId());
    assertEquals("resolved-secret", tile.getToken());
  }

  // Validation failures
  @Test
  void createTile_withInvalidUrl_returnsBadRequest() throws Exception {
    var create =
        new MapTileCreate(
            "Broken raster",
            "ftp://tiles.example.com/a",
            "Bad",
            null,
            MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.BAD_URL.code(), error.code());
    assertEquals("Bad url", error.message());
    assertEquals(0, mapTileRepository.count());
  }

  @Test
  void patchTile_withInvalidUrl_returnsBadRequest() throws Exception {
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/primary/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);

    var patch =
        new MapTilePatch(
            Optional.empty(),
            Optional.of("ftp://tiles.example.com/bad"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    var result =
        mockMvc
            .perform(
                patch("/v1/map/tiles/{id}", tileId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.BAD_URL.code(), error.code());
    assertEquals("Bad url", error.message());
  }

  @Test
  void createWeatherTile_withBlankFeatureId_returnsBadRequest() throws Exception {
    var create =
        weatherCreate(
            java.util.Set.of(
                new WeatherFeatureConfig(
                    "   ",
                    "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.WEATHER_FEATURE_REQUIRED.code(), error.code());
    assertEquals("Weather feature id is required", error.message());
  }

  @Test
  void createWeatherTile_withBlankFeatureUrl_returnsBadRequest() throws Exception {
    var create =
        weatherCreate(
            java.util.Set.of(new WeatherFeatureConfig("IMERG_Precipitation_Rate_30min", "  ")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.WEATHER_FEATURE_URL_INVALID.code(), error.code());
    assertEquals("Weather feature url must be nonblank and valid", error.message());
  }

  @Test
  void createWeatherTile_withInvalidFeatureUrl_returnsBadRequest() throws Exception {
    var create =
        weatherCreate(
            java.util.Set.of(
                new WeatherFeatureConfig(
                    "IMERG_Precipitation_Rate_30min", "ftp://tiles.example.com")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.WEATHER_FEATURE_URL_INVALID.code(), error.code());
    assertEquals("Weather feature url must be nonblank and valid", error.message());
  }

  // Missing resource
  @Test
  void getOne_whenTileMissing_returnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", UUID.randomUUID()).with(authentication(USER_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Tile not found", error.message());
  }

  // Other edge cases
  @Test
  void patchTile_whenKindChangesWithoutConfig_returnsBadRequest() throws Exception {
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/primary/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);

    var patch =
        new MapTilePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new MapTileConfigPatch(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(TileKind.VECTOR),
                    Optional.empty())),
            Optional.empty());

    var result =
        mockMvc
            .perform(
                patch("/v1/map/tiles/{id}", tileId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.KIND_CHANGE_REQUIRES_CONFIG.code(), error.code());
    assertEquals("Config is required when kind is changed", error.message());
  }

  @Test
  void patchPrimaryTileToWeather_returnsBadRequest() throws Exception {
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/primary/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);

    var patch =
        new MapTilePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new MapTileConfigPatch(
                    Optional.of(TileType.WEATHER),
                    Optional.of(
                        new WeatherTileExtensionPatch(
                            Optional.of(
                                java.util.Set.of(
                                    new WeatherFeatureConfig(
                                        "IMERG_Precipitation_Rate_30min",
                                        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"))))),
                    Optional.empty(),
                    Optional.empty())),
            Optional.empty());

    var result =
        mockMvc
            .perform(
                patch("/v1/map/tiles/{id}", tileId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.PRIMARY_MUST_BE_BASEMAP.code(), error.code());
    assertEquals("Primary tile must be a basemap", error.message());
  }

  @Test
  void patchWeatherTile_withDuplicateFeatureIds_returnsBadRequest() throws Exception {
    var tileId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            "Weather layer",
            "Weather layer description",
            "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
            weatherWmtsConfig(),
            null);

    var patch =
        new MapTilePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new MapTileConfigPatch(
                    Optional.of(TileType.WEATHER),
                    Optional.of(
                        new WeatherTileExtensionPatch(
                            Optional.of(
                                java.util.Set.of(
                                    new WeatherFeatureConfig(
                                        "IMERG_Precipitation_Rate_30min",
                                        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"),
                                    new WeatherFeatureConfig(
                                        "IMERG_Precipitation_Rate_30min",
                                        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/MERRA2_Total_Precipitable_Water_Vapor_Monthly/default/default/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"))))),
                    Optional.empty(),
                    Optional.empty())),
            Optional.empty());

    var result =
        mockMvc
            .perform(
                patch("/v1/map/tiles/{id}", tileId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.DUPLICATE_WEATHER_FEATURE.code(), error.code());
    assertEquals("Weather feature ids must be unique within a tile", error.message());
  }

  @Test
  void deleteTile_whenMissing_returnsNotFound() throws Exception {
    var result =
        mockMvc
            .perform(
                delete("/v1/map/tiles/{id}", UUID.randomUUID())
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isNotFound())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Tile not found", error.message());
  }

  private static com.opencop.core.sdk.business.model.map.tile.MapTileConfig weatherWmtsConfig() {
    var tile =
        new WmtsConfig(
            "1.0.0",
            "IMERG_Precipitation_Rate_30min",
            "default",
            "image/png",
            "GoogleMapsCompatible_Level6",
            null);
    return MapTileFixtures.weatherConfig(
        tile,
        new WeatherTileExtension(
            java.util.Set.of(
                new WeatherFeatureConfig(
                    "IMERG_Precipitation_Rate_30min",
                    "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"))));
  }

  // Weather tile
  @Test
  void adminCanCreateWeatherTileAndReadItBack() throws Exception {
    var create = MapTileFixtures.defaultGibsWeatherCreate();

    var createResult =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn();

    var created = readResponse(createResult, MapTile.class);
    var createdId = uuidFromLocation(createResult);

    assertEquals(createdId, created.getId());
    assertFalse(created.isPrimary());
    assertInstanceOf(WmtsConfig.class, created.getConfig().getTile());
    assertEquals(TileType.WEATHER, created.getConfig().tileType());
    assertInstanceOf(WeatherTileExtension.class, created.getConfig().getExtension());
    assertEquals(
        java.util.Set.of("IMERG_Precipitation_Rate_30min"),
        ((WeatherTileExtension) created.getConfig().getExtension()).enabledFeatures());
    assertEquals(1, ((WeatherTileExtension) created.getConfig().getExtension()).features().size());

    // Read back as admin, like the basemap sibling. New tiles are PRIVATE, so a non-admin with no
    // grant is correctly refused. Access control has its own tests below.
    var readResult =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", createdId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var readBack = readResponse(readResult, MapTile.class);
    assertEquals(TileType.WEATHER, readBack.getConfig().tileType());
  }

  @Test
  void adminCanCreateWeatherTileWithProviderDefinedFeatureId() throws Exception {
    var create =
        weatherCreate(
            java.util.Set.of(
                new WeatherFeatureConfig(
                    "provider.wind.speed.night",
                    "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png")));

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(result, MapTile.class);
    assertEquals(
        java.util.Set.of("provider.wind.speed.night"),
        ((WeatherTileExtension) created.getConfig().getExtension()).enabledFeatures());
  }

  @Test
  void getAll_returnsWeatherAndBasemapTilesTogether() throws Exception {
    mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        true,
        "Primary raster",
        "Primary raster description",
        "https://tiles.example.com/{z}/{x}/{y}.png",
        new RasterConfig(256, 0, 18),
        null);
    mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        false,
        "GIBS Precipitation",
        "GIBS precip layer",
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
        weatherWmtsConfig(),
        null);

    var result =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var tiles = readResponse(result, new TypeReference<List<MapTile>>() {});
    assertEquals(2, tiles.size());
    var types = tiles.stream().map(t -> t.getConfig().tileType()).toList();
    assertTrue(types.contains(TileType.BASEMAP));
    assertTrue(types.contains(TileType.WEATHER));
  }

  // Persistence/readback
  @Test
  void deletingPrimaryPromotesNextSupportedTile() throws Exception {
    var primaryId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            true,
            "Primary raster",
            "Primary raster description",
            "https://tiles.example.com/primary/{z}/{x}/{y}.png",
            new RasterConfig(256, 0, 18),
            null);
    var nextId =
        mapTileFixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            "Secondary vector",
            "Secondary vector description",
            "https://tiles.example.com/secondary/{z}/{x}/{y}.pbf",
            new VectorConfig(512, 1, 16),
            null);

    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", primaryId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    var primaryResult =
        mockMvc
            .perform(get("/v1/map/tiles/primary").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var promoted = readResponse(primaryResult, MapTile.class);
    assertEquals(nextId, promoted.getId());
    assertTrue(promoted.isPrimary());
  }

  // Enabled / disabled
  @Test
  void getAllTiles_omitsDisabledTiles() throws Exception {
    var enabledId = persistBasemap(true, true, "Enabled raster", "enabled");
    var disabledId = persistBasemap(false, false, "Disabled raster", "disabled");

    var result =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var tiles = readResponse(result, new TypeReference<List<MapTile>>() {});
    assertEquals(List.of(enabledId), tiles.stream().map(MapTile::getId).toList());
    assertTrue(tiles.getFirst().isEnabled());
    assertTrue(mapTileRepository.existsById(disabledId));
  }

  @Test
  void adminTileList_includesDisabledTiles() throws Exception {
    persistBasemap(true, true, "Enabled raster", "enabled");
    var disabledId = persistBasemap(false, false, "Disabled raster", "disabled");

    var result =
        mockMvc
            .perform(get("/v1/admin/map/tiles").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var tiles = readResponse(result, new TypeReference<List<MapTile>>() {});
    assertEquals(2, tiles.size());
    var disabled = tiles.stream().filter(tile -> tile.getId().equals(disabledId)).findFirst();
    assertTrue(disabled.isPresent());
    assertFalse(disabled.get().isEnabled());
  }

  @Test
  void adminTileList_asNonAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(get("/v1/admin/map/tiles").with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchTile_disablingNonPrimaryTile_withholdsItFromOperatorsUntilReEnabled() throws Exception {
    persistBasemap(true, true, "Primary raster", "primary");
    var secondaryId = persistBasemap(false, true, "Secondary raster", "secondary");

    patchEnabled(secondaryId, false).andExpect(status().isOk());
    assertFalse(operatorTileIds().contains(secondaryId));

    patchEnabled(secondaryId, true).andExpect(status().isOk());
    assertTrue(operatorTileIds().contains(secondaryId));
  }

  @Test
  void patchTile_disablingPrimaryTile_returnsBadRequest() throws Exception {
    var primaryId = persistBasemap(true, true, "Primary raster", "primary");

    var result = patchEnabled(primaryId, false).andExpect(status().isBadRequest()).andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED.code(), error.code());
    assertTrue(mapTileFixtures.load(primaryId).isEnabled());
  }

  @Test
  void setPrimary_onDisabledTile_returnsBadRequest() throws Exception {
    persistBasemap(true, true, "Primary raster", "primary");
    var disabledId = persistBasemap(false, false, "Disabled raster", "disabled");

    var result =
        mockMvc
            .perform(
                put("/v1/admin/map/tiles/{id}/primary", disabledId)
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH)))
            .andExpect(status().isBadRequest())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals(MapTileExceptionCodes.PRIMARY_MUST_BE_ENABLED.code(), error.code());
    assertFalse(mapTileFixtures.load(disabledId).isPrimary());
  }

  @Test
  void deletingPrimaryTile_promotesAnEnabledBasemapOverADisabledOne() throws Exception {
    var primaryId = persistBasemap(true, true, "Primary raster", "primary");
    persistBasemap(false, false, "Disabled raster", "disabled");
    var enabledId = persistBasemap(false, true, "Standby raster", "standby");

    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", primaryId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    assertEquals(enabledId, mapTileFixtures.loadPrimary().getId());
  }

  @Test
  void deletingPrimaryTile_promotesAGlobalBasemapOverAPrivateOne() throws Exception {
    var primaryId = persistBasemap(true, true, "Primary raster", "primary");
    var privateId = persistPrivateBasemap("Private raster", "private");
    var globalId = persistBasemap(false, true, "Standby raster", "standby");

    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", primaryId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isNoContent());

    assertEquals(globalId, mapTileFixtures.loadPrimary().getId());
    assertFalse(mapTileFixtures.load(privateId).isPrimary());

    // The point of the rule: the successor has to be usable by someone holding no grant.
    // Promoting the PRIVATE tile would leave this caller with a 403 and no basemap.
    mockMvc
        .perform(get("/v1/map/tiles/primary").with(authentication(USER_AUTH)))
        .andExpect(status().isOk());
  }

  /**
   * No primary at all beats an unusable one: a PRIVATE primary would 403 for every ungranted
   * operator, leaving them with no basemap and no way to pick another. Rather than delete into that
   * state, the delete is refused so the catalog always keeps a usable primary.
   */
  @Test
  void deletingPrimaryTile_whenOnlyPrivateBasemapsRemain_isRefused() throws Exception {
    var primaryId = persistBasemap(true, true, "Primary raster", "primary");
    var privateId = persistPrivateBasemap("Private raster", "private");

    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", primaryId).with(csrf()).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isConflict());

    assertTrue(mapTileFixtures.load(primaryId).isPrimary());
    assertFalse(mapTileFixtures.load(privateId).isPrimary());
  }

  private UUID persistBasemap(boolean primary, boolean enabled, String name, String slug) {
    return mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        primary,
        enabled,
        name,
        name + " description",
        "https://tiles.example.com/" + slug + "/{z}/{x}/{y}.png",
        MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)),
        null);
  }

  private org.springframework.test.web.servlet.ResultActions patchEnabled(UUID id, boolean enabled)
      throws Exception {
    var patch =
        new MapTilePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(enabled),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    return mockMvc.perform(
        patch("/v1/map/tiles/{id}", id)
            .with(csrf())
            .with(authentication(ADMIN_AUTH))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(patch)));
  }

  private List<UUID> operatorTileIds() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<List<MapTile>>() {}).stream()
        .map(MapTile::getId)
        .toList();
  }

  /** What a global admin gets from the operator route: the tiles they may put on their own map. */
  private List<UUID> adminOperatorTileIds() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<List<MapTile>>() {}).stream()
        .map(MapTile::getId)
        .toList();
  }

  /** The full catalogue behind the imagery admin screen, unnarrowed by access or enablement. */
  private List<UUID> adminCatalogueTileIds() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/admin/map/tiles").with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<List<MapTile>>() {}).stream()
        .map(MapTile::getId)
        .toList();
  }

  // Access control
  @Test
  void privateTile_isWithheldFromAUserWithNoGrant() throws Exception {
    var tileId = persistPrivateBasemap("Restricted raster", "restricted");

    assertFalse(operatorTileIds().contains(tileId));
    mockMvc
        .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(USER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void privateTile_becomesVisibleOnceTheUserIsGranted() throws Exception {
    var tileId = persistPrivateBasemap("Restricted raster", "restricted");

    grantMember(tileId, MEMBER_USER_ID);

    assertTrue(memberTileIds().contains(tileId));
    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    assertEquals(MapTileAccessScope.PRIVATE, readResponse(result, MapTile.class).getAccessScope());
  }

  @Test
  void privateTile_becomesVisibleThroughAUserGroupGrant() throws Exception {
    var tileId = persistPrivateBasemap("Group raster", "group");
    var groupId = insertUserGroup("imagery-viewers");
    insertGroupMembership(groupId, MEMBER_USER_ID);

    mockMvc
        .perform(
            post("/v1/map/tiles/{id}/user-groups", tileId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(groupId))))
        .andExpect(status().isNoContent());

    assertTrue(memberTileIds().contains(tileId));
  }

  /**
   * Administering a tile and carrying it on your own map are separate acts. The admin flag opens
   * the catalogue and every management route; it does not put another team's imagery in the admin's
   * own basemap picker, which is how effective layer access already treats an operator's layer
   * list.
   */
  @Test
  void privateTile_isWithheldFromAGlobalAdminWithNoGrant() throws Exception {
    var tileId = persistPrivateBasemap("Someone else's raster", "someone-else");

    assertFalse(adminOperatorTileIds().contains(tileId));
    // The tile is not hidden from the admin, only from their map: it stays in the catalogue the
    // imagery admin screen reads, and readable by id so the drawer can still edit it.
    assertTrue(adminCatalogueTileIds().contains(tileId));
    mockMvc
        .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
  }

  @Test
  void privateTile_reachesAGlobalAdminOnceGranted() throws Exception {
    var tileId = persistPrivateBasemap("Granted raster", "granted-admin");

    assertFalse(adminOperatorTileIds().contains(tileId));

    grantMember(tileId, ADMIN_USER_ID);

    assertTrue(adminOperatorTileIds().contains(tileId));
  }

  @Test
  void globalTile_reachesAGlobalAdminWithoutAGrant() throws Exception {
    var tileId = persistBasemap(false, true, "Global raster", "global-admin-visible");

    assertTrue(adminOperatorTileIds().contains(tileId));
  }

  /**
   * The safety net under the rule above. Withholding ungranted tiles from an administrator's map is
   * only tolerable because the primary basemap is forced GLOBAL and enabled, so it survives the
   * filtering and every administrator still renders a map. If that invariant or the GLOBAL branch
   * of reachability ever goes, an administrator holding no grants gets a blank map and nothing else
   * here would catch it.
   */
  @Test
  void primaryBasemap_staysAvailableToAGlobalAdminHoldingNoGrants() throws Exception {
    var primaryId = persistBasemap(true, true, "Primary raster", "primary-admin");
    persistPrivateBasemap("Someone else's raster", "unreachable");

    assertTrue(adminOperatorTileIds().contains(primaryId));
    mockMvc
        .perform(get("/v1/map/tiles/primary").with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
  }

  /**
   * A GLOBAL tile hands every authenticated user an implicit VIEWER role. If the roster were
   * viewer-readable, that would let anyone enumerate any global tile's grants, usernames and emails
   * included.
   */
  @Test
  void rosterReads_areRefusedToAnOrdinaryUserOfAGlobalTile() throws Exception {
    var tileId = persistBasemap(false, true, "Global roster", "global-roster");

    mockMvc
        .perform(get("/v1/map/tiles/{id}/members", tileId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/v1/map/tiles/{id}/user-groups", tileId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void rosterReads_areRefusedToAGrantedUser() throws Exception {
    var tileId = persistPrivateBasemap("Viewer roster", "viewer-roster");
    grantMember(tileId, MEMBER_USER_ID);

    mockMvc
        .perform(get("/v1/map/tiles/{id}/members", tileId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isForbidden());
  }

  @Test
  void listingMembers_returnsTheGrantEnrichedWithIdentity() throws Exception {
    var tileId = persistPrivateBasemap("Enriched", "enriched");
    grantMember(tileId, MEMBER_USER_ID);

    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}/members", tileId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var members = readResponse(result, new TypeReference<List<MapTileMember>>() {});
    var granted = members.stream().filter(m -> m.userId().equals(MEMBER_USER_ID)).findFirst();
    assertTrue(granted.isPresent());
    assertEquals("user-" + MEMBER_USER_ID, granted.get().username());
  }

  @Test
  void listingUserGroups_returnsTheGrantWithTheGroupName() throws Exception {
    var tileId = persistPrivateBasemap("Group listing", "group-listing");
    var groupId = insertUserGroup("coalition-partners");
    mockMvc
        .perform(
            post("/v1/map/tiles/{id}/user-groups", tileId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(groupId))))
        .andExpect(status().isNoContent());

    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}/user-groups", tileId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var groups = readResponse(result, new TypeReference<List<MapTileUserGroupGrant>>() {});
    assertEquals(1, groups.size());
    assertEquals("coalition-partners", groups.getFirst().name());
  }

  @Test
  void creatingATile_grantsTheCreatorAccess() throws Exception {
    var createdId = createPrivateTileAsAdmin();

    var result =
        mockMvc
            .perform(get("/v1/map/tiles/{id}/members", createdId).with(authentication(ADMIN_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var members = readResponse(result, new TypeReference<List<MapTileMember>>() {});
    assertEquals(1, members.size());
    assertEquals(ADMIN_USER_ID, members.getFirst().userId());
  }

  @Test
  void aGrantHolder_cannotPatchTheTile() throws Exception {
    var tileId = persistPrivateBasemap("Read only", "read-only");
    grantMember(tileId, MEMBER_USER_ID);

    mockMvc
        .perform(
            patch("/v1/map/tiles/{id}", tileId)
                .with(csrf())
                .with(authentication(MEMBER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(namePatch("Nope"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatingTheFirstPrivateBasemap_doesNotAutoPromoteItToPrimary() throws Exception {
    // Auto-promotion writes is_primary directly and bypasses setPrimary's guard. A PRIVATE primary
    // would 403 for every ungranted operator on /v1/map/tiles/primary, leaving them no basemap.
    var createdId = createPrivateTileAsAdmin();

    assertFalse(mapTileFixtures.load(createdId).isPrimary());
    assertEquals(0, mapTileRepository.findAll().stream().filter(t -> t.isPrimary()).count());
  }

  @Test
  void creatingTheFirstGlobalBasemap_autoPromotesItToPrimary() throws Exception {
    var create =
        new MapTileCreate(
            "Global raster",
            "https://tiles.example.com/global/{z}/{x}/{y}.png",
            "Global raster description",
            null,
            MapTileAccessScope.GLOBAL,
            MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)),
            null);

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    assertTrue(mapTileFixtures.load(uuidFromLocation(result)).isPrimary());
  }

  @Test
  void disabledTile_isNotReadableByIdForAnOperator() throws Exception {
    // Disabling must withhold the tile from the detail route too, not just the list. Otherwise a
    // caller who knows the id keeps retrieving its url, config and decrypted token.
    var tileId = persistPrivateBasemap("Withheld", "withheld");
    grantMember(tileId, MEMBER_USER_ID);
    patchEnabled(tileId, false).andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(MEMBER_AUTH)))
        .andExpect(status().isNotFound());
  }

  @Test
  void disabledTile_staysReadableForAnAdminSoTheDrawerCanEditIt() throws Exception {
    var tileId = persistPrivateBasemap("Withheld admin", "withheld-admin");
    patchEnabled(tileId, false).andExpect(status().isOk());

    mockMvc
        .perform(get("/v1/map/tiles/{id}", tileId).with(authentication(ADMIN_AUTH)))
        .andExpect(status().isOk());
  }

  @Test
  void aTokenedCreateKeepsItsScopeAndCredit() throws Exception {
    // Create rebuilds the request to swap the plaintext token for its secret reference; that
    // reconstruction must not drop the requested scope or the credit.
    var create =
        new MapTileCreate(
            "Tokened global",
            "https://tiles.example.com/tokened/{z}/{x}/{y}.png",
            "Tokened global description",
            "super-secret-token",
            MapTileAccessScope.GLOBAL,
            MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)),
            "© Example imagery");

    var result =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(create)))
            .andExpect(status().isCreated())
            .andReturn();

    var tileId = uuidFromLocation(result);
    var tokenRef = MapTileSecretRef.fromTileId(tileId);
    assertEquals("© Example imagery", readResponse(result, MapTile.class).getAttribution());
    var entity = mapTileFixtures.load(tileId);
    assertEquals(MapTileAccessScope.GLOBAL, entity.getAccessScope());
    assertEquals("© Example imagery", entity.getAttribution());
    assertEquals(tokenRef, entity.getTokenRef());
    verify(secretStore).patch(eq(tokenRef), eq(Map.of("token", "super-secret-token")), eq(null));
  }

  @Test
  void aTileInsertedWithoutAnAccessScopeIsPrivate() throws Exception {
    // The schema default is the last line of defence: the entity supplies PRIVATE for JPA writes,
    // but a direct SQL path, an import, or a future writer that omits the column must not publish
    // a tile to everyone. V43 backfills existing rows GLOBAL and then sets the default to PRIVATE.
    var tileId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO map_tile (id, is_primary, enabled, name, description, url, kind, config)
        VALUES (?, false, true, ?, ?, ?, 'RASTER', CAST(? AS jsonb))
        """,
        tileId,
        "raw-" + tileId,
        "Inserted without an access scope",
        "https://tiles.example.com/raw/{z}/{x}/{y}.png",
        "{\"type\":\"BASEMAP\",\"tile\":{\"kind\":\"RASTER\",\"tileSize\":256,\"minZoom\":0,\"maxZoom\":18}}");

    assertEquals(
        "PRIVATE",
        jdbc.queryForObject(
            "SELECT access_scope::text FROM map_tile WHERE id = ?", String.class, tileId));
  }

  private UUID createPrivateTileAsAdmin() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/v1/admin/map/tiles")
                    .with(csrf())
                    .with(authentication(ADMIN_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(MapTileFixtures.defaultRasterCreate())))
            .andExpect(status().isCreated())
            .andReturn();
    return uuidFromLocation(createResult);
  }

  private UUID persistPrivateBasemap(String name, String slug) {
    return mapTileFixtures.persistMapTile(
        UUID.randomUUID(),
        false,
        true,
        MapTileAccessScope.PRIVATE,
        name,
        name + " description",
        "https://tiles.example.com/" + slug + "/{z}/{x}/{y}.png",
        MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)),
        null);
  }

  private void grantMember(UUID tileId, UUID userId) throws Exception {
    mockMvc
        .perform(
            post("/v1/map/tiles/{id}/members", tileId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Set.of(userId))))
        .andExpect(status().isNoContent());
  }

  private MapTilePatch namePatch(String name) {
    return new MapTilePatch(
        Optional.of(name),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private org.springframework.test.web.servlet.ResultActions patchName(UUID id, String name)
      throws Exception {
    return mockMvc.perform(
        patch("/v1/map/tiles/{id}", id)
            .with(csrf())
            .with(authentication(MEMBER_AUTH))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(namePatch(name))));
  }

  private List<UUID> memberTileIds() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/map/tiles").with(authentication(MEMBER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, new TypeReference<List<MapTile>>() {}).stream()
        .map(MapTile::getId)
        .toList();
  }

  private static MapTileCreate weatherCreate(java.util.Set<WeatherFeatureConfig> features) {
    return new MapTileCreate(
        "NASA GIBS Weather",
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
        "Weather layer",
        null,
        MapTileFixtures.weatherConfig(
            new WmtsConfig(
                "1.0.0",
                "IMERG_Precipitation_Rate_30min",
                "default",
                "image/png",
                "GoogleMapsCompatible_Level6",
                null),
            new WeatherTileExtension(features)));
  }
}
