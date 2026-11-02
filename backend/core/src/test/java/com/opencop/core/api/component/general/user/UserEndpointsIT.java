// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.business.model.auth.MeResponse;
import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.model.user.UserPreferencePatch;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.technical.endpoint.user.UserWebTestApplication;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.business.model.map.tile.RasterConfig;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.iam.user.UserAdminPatchRequest;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = UserWebTestApplication.class)
class UserEndpointsIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440191");
  private static final UUID OTHER_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440192");
  private static final UUID LEGACY_USER_ID =
      UUID.fromString("a57eb176-3930-459a-a0d3-4bfdef113740");
  private static final String LEGACY_PRINCIPAL_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";
  private static final Authentication USER_AUTH = TestAuthentications.oidcUser(USER_ID);
  private static final Authentication ADMIN_AUTH = TestAuthentications.oidcAdmin(USER_ID);
  private static final Set<String> USER_RESPONSE_FIELDS =
      Set.of(
          "id",
          "idpUserId",
          "idpUsername",
          "issuer",
          "username",
          "email",
          "firstName",
          "lastName",
          "displayName",
          "active",
          "admin",
          "createdAt",
          "updatedAt",
          "deletedAt");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserPreferenceRepository userPreferenceRepository;

  @MockitoBean private MapTileService mapTileService;
  @MockitoBean private MapCaptureJpaRepository mapCaptureRepository;
  @MockitoBean private SecretStore secretStore;

  private UserFixtures userFixtures;

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
  }

  @BeforeEach
  void setUp() throws Exception {
    userFixtures = new UserFixtures(jdbcTemplate, userPreferenceRepository);
    userFixtures.reset();
    userFixtures.seedUser(USER_ID);
    userFixtures.seedUser(OTHER_USER_ID);

    when(mapTileService.getOne(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(Optional.empty());
    when(mapTileService.getOneSanitized(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(Optional.empty());
    when(mapCaptureRepository.findById(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(Optional.empty());
    when(secretStore.read(org.mockito.ArgumentMatchers.any(String.class))).thenReturn(Map.of());
  }

  // Happy path
  @Test
  void csrf_returnsTokenForAuthenticatedRequest() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/csrf").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var body = readResponse(result, new TypeReference<Map<String, String>>() {});
    assertNotNull(body.get("token"));
  }

  @Test
  void me_returnsMappedMinimalIdentityAndPersistedPreference() throws Exception {
    userFixtures.persistPreference(
        USER_ID, "fr", MeasurementSystem.METRIC, TemperatureUnit.CELSIUS);

    var result =
        mockMvc
            .perform(get("/v1/me").with(authentication(richAuth(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, MeResponse.class);
    assertTrue(response.authenticated());
    assertEquals(USER_ID, response.user().id());
    assertEquals("test-subject", response.user().subject());
    assertEquals("preferred-user", response.user().username());
    assertEquals("Jane Doe", response.user().displayName());
    assertEquals("jane@example.com", response.user().email());
    assertEquals("https://issuer.test", response.idp().issuer());
    assertEquals(List.of(Roles.ADMIN, "SCOPE_profile"), response.authorities());
    assertEquals("preferred-user", response.claims().get("preferred_username"));
    assertEquals("jane@example.com", response.claims().get("email"));
    assertEquals("fr", response.preference().locale());
    assertEquals("UTC", response.preference().timezone());
    assertEquals(ZoomBehavior.CENTER, response.preference().zoomBehavior());
  }

  @Test
  void me_preservesSubjectFallbacksWhenMinimalIdentityOmitsOptionalProfile() throws Exception {
    var principal =
        new OpenCopPrincipal(
            USER_ID,
            "https://issuer.test",
            "stable-user-id",
            "fallback-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, null, null),
            0,
            Instant.parse("2026-03-16T09:00:00Z"));
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

    var result =
        mockMvc
            .perform(get("/v1/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, MeResponse.class);
    assertEquals("fallback-subject", response.user().username());
    assertEquals("fallback-subject", response.user().displayName());
    assertNull(response.user().email());
    assertEquals(Map.of("sub", "fallback-subject"), response.claims());
  }

  @Test
  void me_usesGivenNameWhenMinimalIdentityOmitsFullName() throws Exception {
    var principal =
        new OpenCopPrincipal(
            USER_ID,
            "https://issuer.test",
            "stable-user-id",
            "fallback-subject",
            new OpenCopPrincipal.Profile(null, null, "Given", null, null, null),
            0,
            Instant.parse("2026-03-16T09:00:00Z"));
    var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

    var result =
        mockMvc
            .perform(get("/v1/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, MeResponse.class);
    assertEquals("fallback-subject", response.user().username());
    assertEquals("Given", response.user().displayName());
    assertEquals(Map.of("sub", "fallback-subject", "given_name", "Given"), response.claims());
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void me_preservesTheRetainedLegacyIdentityResponse() throws Exception {
    userFixtures.seedUser(LEGACY_USER_ID);
    var principal = legacyPrincipal();
    var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

    var result =
        mockMvc
            .perform(get("/v1/me").with(authentication(auth)))
            .andExpect(status().isOk())
            .andReturn();

    var response = readResponse(result, MeResponse.class);
    assertEquals(LEGACY_USER_ID, response.user().id());
    assertEquals("legacy-subject", response.user().subject());
    assertEquals("legacy-user", response.user().username());
    assertEquals("Legacy User", response.user().displayName());
    assertEquals("legacy.user@example.test", response.user().email());
    assertEquals("https://legacy-idp.example.test/realms/opencop", response.idp().issuer());
    assertFalse(response.claims().containsKey("arbitrary_legacy_claim"));
  }

  @Test
  void getPreferences_whenMissing_returnsDefaultPreferences() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/me/preferences").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();

    var preference = readResponse(result, UserPreference.class);
    assertEquals("user-" + USER_ID, preference.username());
    assertEquals("en", preference.locale());
    assertEquals(MeasurementSystem.METRIC, preference.measurementSystem());
    assertEquals(TemperatureUnit.CELSIUS, preference.temperatureUnit());
    assertEquals("UTC", preference.timezone());
    assertEquals(ZoomBehavior.CENTER, preference.zoomBehavior());
  }

  @Test
  void patchPreferences_persistsAndReadsBackPreferenceChanges() throws Exception {
    userFixtures.seedUser(USER_ID);
    var patch =
        new UserPreferencePatch(
            Optional.of("updated-user"),
            Optional.of("fr"),
            Optional.of(MeasurementSystem.IMPERIAL),
            Optional.of(TemperatureUnit.FAHRENHEIT),
            Optional.of("America/New_York"),
            Optional.of(ZoomBehavior.MOUSE),
            Optional.empty(),
            Optional.empty(),
            null);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/me/preferences")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isOk())
            .andReturn();

    var patched = readResponse(patchResult, UserPreference.class);
    assertEquals("updated-user", patched.username());
    assertEquals("fr", patched.locale());
    assertEquals(MeasurementSystem.IMPERIAL, patched.measurementSystem());
    assertEquals(TemperatureUnit.FAHRENHEIT, patched.temperatureUnit());
    assertEquals("America/New_York", patched.timezone());
    assertEquals(ZoomBehavior.MOUSE, patched.zoomBehavior());

    var getResult =
        mockMvc
            .perform(get("/v1/me/preferences").with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    var readback = readResponse(getResult, UserPreference.class);
    assertEquals("updated-user", readback.username());
    assertEquals("fr", readback.locale());
    assertEquals("America/New_York", readback.timezone());
    assertEquals(ZoomBehavior.MOUSE, readback.zoomBehavior());
    assertEquals(1, userFixtures.countPreferences());
  }

  @Test
  void patchPreferences_withDefaultTile_returnsTileWithoutVaultRef() throws Exception {
    // GLOBAL explicitly: the column now defaults to PRIVATE, and setting a default tile you cannot
    // use is refused. A GLOBAL tile is one any user may legitimately nominate.
    userFixtures.seedUser(USER_ID);
    var tileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO map_tile (
          id, is_primary, access_scope, name, description, url, kind, config, token_ref)
        VALUES (?, ?, 'GLOBAL', ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """,
        tileId,
        false,
        "test-tile-" + tileId,
        "Ops tile",
        "https://tiles.example.com/{z}/{x}/{y}.png",
        "RASTER",
        "{\"type\":\"BASEMAP\",\"tile\":{\"kind\":\"RASTER\",\"tileSize\":256,\"minZoom\":0,\"maxZoom\":18}}",
        "opencop/core/secrets/map/tiles/" + tileId);
    var sanitizedTile =
        new MapTile(
            tileId,
            true,
            true,
            "Ops",
            "Ops tile",
            "https://tiles.example.com/{z}/{x}/{y}.png",
            new MapTileConfig(new RasterConfig(256, 0, 18)),
            null);
    when(mapTileService.getOneSanitized(tileId)).thenReturn(Optional.of(sanitizedTile));

    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(tileId),
            null);

    var patchResult =
        mockMvc
            .perform(
                patch("/v1/me/preferences")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isOk())
            .andReturn();

    var patched = readResponse(patchResult, UserPreference.class);
    assertNotNull(patched.defaultTile());
    assertEquals(tileId, patched.defaultTile().getId());
    assertNull(patched.defaultTile().getToken());
  }

  @Test
  void patchPreferences_whenUsernameAlreadyTaken_returnsConflictAndDoesNotPersist()
      throws Exception {
    userFixtures.seedUser(USER_ID);
    userFixtures.seedUser(OTHER_USER_ID);
    userFixtures.setUsername(OTHER_USER_ID, "taken-user");
    var patch =
        new UserPreferencePatch(
            Optional.of("taken-user"),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    var result =
        mockMvc
            .perform(
                patch("/v1/me/preferences")
                    .with(csrf())
                    .with(authentication(USER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(patch)))
            .andExpect(status().isConflict())
            .andReturn();

    var error = readResponse(result, GlobalErrorResponse.class);
    assertEquals("Username already taken: taken-user", error.message());
    assertEquals(0, userFixtures.countPreferences());
  }

  // Access edge cases
  @Test
  void patchPreferences_withoutCsrf_returnsForbiddenAndDoesNotPersist() throws Exception {
    userFixtures.seedUser(USER_ID);
    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.of("fr"),
            Optional.empty(),
            Optional.empty(),
            Optional.of("America/New_York"),
            Optional.of(ZoomBehavior.CENTER),
            Optional.empty(),
            Optional.empty(),
            null);

    mockMvc
        .perform(
            patch("/v1/me/preferences")
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patch)))
        .andExpect(status().isForbidden());

    assertEquals(0, userFixtures.countPreferences());
  }

  @Test
  void patchPreferences_withInvalidTimezone_returnsBadRequest() throws Exception {
    userFixtures.seedUser(USER_ID);
    var patch =
        new UserPreferencePatch(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("+02:00"),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null);

    mockMvc
        .perform(
            patch("/v1/me/preferences")
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(patch)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getUser_preservesExactPublicJsonProperties() throws Exception {
    var oneResult =
        mockMvc
            .perform(get("/v1/users/{userId}", USER_ID).with(authentication(USER_AUTH)))
            .andExpect(status().isOk())
            .andReturn();
    Map<String, Object> one = readResponse(oneResult, new TypeReference<>() {});
    assertEquals(USER_RESPONSE_FIELDS, one.keySet());
    assertEquals(USER_ID.toString(), one.get("id"));
    assertEquals("idp-" + USER_ID, one.get("idpUserId"));
    assertEquals("user-" + USER_ID, one.get("idpUsername"));
    assertEquals("https://issuer.test", one.get("issuer"));
    assertEquals("user-" + USER_ID, one.get("username"));
    assertEquals("user@example.com", one.get("email"));
    assertEquals("Test", one.get("firstName"));
    assertEquals("User", one.get("lastName"));
    assertEquals("Test User", one.get("displayName"));
    assertEquals(true, one.get("active"));
    assertEquals(false, one.get("admin"));
    assertNotNull(one.get("createdAt"));
    assertNotNull(one.get("updatedAt"));
    assertNull(one.get("deletedAt"));
  }

  @Test
  void setAdmin_whenRequesterIsNotAdmin_returnsForbidden() throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/users/{userId}", OTHER_USER_ID)
                .with(csrf())
                .with(authentication(USER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new UserAdminPatchRequest(true))))
        .andExpect(status().isForbidden());

    assertFalse(userFixtures.administrator(OTHER_USER_ID));
    assertEquals(0L, userFixtures.accountAccessRevision(OTHER_USER_ID));
  }

  @Test
  void setAdmin_whenRequesterIsAdmin_persistsAdministratorState() throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/users/{userId}", OTHER_USER_ID)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new UserAdminPatchRequest(true))))
        .andExpect(status().isOk());

    assertTrue(userFixtures.administrator(OTHER_USER_ID));
    assertEquals(1L, userFixtures.accountAccessRevision(OTHER_USER_ID));
  }

  // Missing resource
  @Test
  void setAdmin_whenUserDoesNotExist_preservesInternalServerErrorContract() throws Exception {
    var missingUserId = UUID.randomUUID();

    mockMvc
        .perform(
            patch("/v1/admin/users/{userId}", missingUserId)
                .with(csrf())
                .with(authentication(ADMIN_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new UserAdminPatchRequest(true))))
        .andExpect(status().isInternalServerError());
  }

  // Auth
  @Test
  void setAdmin_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/users/{userId}", OTHER_USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new UserAdminPatchRequest(true))))
        .andExpect(status().isUnauthorized());

    assertFalse(userFixtures.administrator(OTHER_USER_ID));
    assertEquals(0L, userFixtures.accountAccessRevision(OTHER_USER_ID));
  }

  // R-01: /v1/me hydrates the default tile's token. A default is stored as a bare id and outlives
  // the access that justified it, so every one of these flows would previously have handed the
  // caller a provider credential for a tile they cannot use.

  /**
   * Inserts a tile directly. access_scope is explicit because V42 defaults existing rows GLOBAL.
   */
  private UUID seedTile(String scope, boolean enabled) {
    var tileId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO map_tile (
          id, is_primary, enabled, access_scope, name, description, url, kind, config, token_ref)
        VALUES (?, ?, ?, CAST(? AS iam.map_tile_access_scope), ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """,
        tileId,
        false,
        enabled,
        scope,
        "tile-" + tileId,
        "Ops tile",
        "https://tiles.example.com/{z}/{x}/{y}.png",
        "RASTER",
        "{\"type\":\"BASEMAP\",\"tile\":{\"kind\":\"RASTER\",\"tileSize\":256,\"minZoom\":0,\"maxZoom\":18}}",
        "opencop/core/secrets/map/tiles/" + tileId);
    // The secret must actually be there, or these assertions would pass on an empty vault rather
    // than on the access check.
    when(secretStore.read("opencop/core/secrets/map/tiles/" + tileId))
        .thenReturn(Map.of("token", "super-secret-token"));
    // MapTileService is mocked here, so the preference cannot resolve its tile without this. The
    // access decision still comes from the real repositories, which is what these tests exercise.
    when(mapTileService.getOneSanitized(tileId))
        .thenReturn(
            Optional.of(
                new MapTile(
                    tileId,
                    false,
                    enabled,
                    "tile-" + tileId,
                    "Ops tile",
                    "https://tiles.example.com/{z}/{x}/{y}.png",
                    new MapTileConfig(new RasterConfig(256, 0, 18)),
                    null)));
    return tileId;
  }

  private void grantMember(UUID tileId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO iam.map_tile_member (map_tile_id, user_id) VALUES (?, ?)", tileId, userId);
  }

  /**
   * Sets the default through the repository rather than the endpoint: these tests are about what
   * the read path does with a stored default, including ones that became unusable after the fact.
   */
  private void setDefaultTile(UUID tileId)
      throws com.opencop.core.sdk.technical.gateway.db.PersistenceException {
    userPreferenceRepository.patch(
        USER_ID,
        new UserPreferencePatch(
            null, null, null, null, null, null, null, Optional.of(tileId), null));
  }

  private MeResponse me() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/me").with(authentication(richAuth(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, MeResponse.class);
  }

  @Test
  void me_returnsTheTokenForADefaultTileTheUserMayUse() throws Exception {
    // The control. Without this the negative cases below could pass for the wrong reason.
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);

    var response = me();
    assertNotNull(response.preference().defaultTile());
    assertEquals("super-secret-token", response.preference().defaultTile().getToken());
  }

  @Test
  void me_withholdsTheTokenForAGuessedPrivateTileId() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    setDefaultTile(tileId); // never granted to this user

    assertNull(me().preference().defaultTile());
  }

  @Test
  void me_withholdsTheTokenAfterTheGrantIsRevoked() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);
    assertNotNull(me().preference().defaultTile());

    jdbcTemplate.update(
        "DELETE FROM iam.map_tile_member WHERE map_tile_id = ? AND user_id = ?", tileId, USER_ID);

    assertNull(me().preference().defaultTile());
  }

  @Test
  void me_withholdsTheTokenAfterGroupAccessIsRemoved() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    var groupId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO iam.user_group (id, name) VALUES (?, ?)", groupId, "viewers");
    jdbcTemplate.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)", groupId, USER_ID);
    jdbcTemplate.update(
        "INSERT INTO iam.map_tile_user_group (map_tile_id, user_group_id) VALUES (?, ?)",
        tileId,
        groupId);
    setDefaultTile(tileId);
    assertNotNull(me().preference().defaultTile());

    jdbcTemplate.update(
        "DELETE FROM iam.user_group_member WHERE group_id = ? AND user_id = ?", groupId, USER_ID);

    assertNull(me().preference().defaultTile());
  }

  @Test
  void me_withholdsTheTokenAfterTheTileIsDisabled() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);
    assertNotNull(me().preference().defaultTile());

    jdbcTemplate.update("UPDATE map_tile SET enabled = false WHERE id = ?", tileId);

    assertNull(me().preference().defaultTile());
  }

  /**
   * The preference-bearing responses share one enrichment boundary with /v1/me, so revocation has
   * to drop the stored tile on all of them. Otherwise a former viewer keeps receiving the tile's
   * url and config from an unrelated preference read or avatar call.
   */
  @Test
  void getPreferences_omitsADefaultTileTheUserCanNoLongerUse() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);
    assertNotNull(preferences().defaultTile());

    jdbcTemplate.update(
        "DELETE FROM iam.map_tile_member WHERE map_tile_id = ? AND user_id = ?", tileId, USER_ID);

    assertNull(preferences().defaultTile());
  }

  @Test
  void getPreferences_omitsADefaultTileAfterItIsDisabled() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);
    assertNotNull(preferences().defaultTile());

    jdbcTemplate.update("UPDATE map_tile SET enabled = false WHERE id = ?", tileId);

    assertNull(preferences().defaultTile());
  }

  @Test
  void patchPreferences_omitsADefaultTileTheUserCanNoLongerUse() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true);
    grantMember(tileId, USER_ID);
    setDefaultTile(tileId);

    jdbcTemplate.update(
        "DELETE FROM iam.map_tile_member WHERE map_tile_id = ? AND user_id = ?", tileId, USER_ID);

    // An unrelated preference change must not hand the tile back on the way out.
    var result =
        mockMvc
            .perform(
                patch("/v1/me/preferences")
                    .with(csrf())
                    .with(authentication(richAuth(USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"timezone\":\"UTC\"}"))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(readResponse(result, UserPreference.class).defaultTile());
  }

  private UserPreference preferences() throws Exception {
    var result =
        mockMvc
            .perform(get("/v1/me/preferences").with(authentication(richAuth(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();
    return readResponse(result, UserPreference.class);
  }

  @Test
  void patchPreferences_rejectsADefaultTileTheUserCannotUse() throws Exception {
    userFixtures.seedUser(USER_ID);
    var tileId = seedTile("PRIVATE", true); // never granted

    mockMvc
        .perform(
            patch("/v1/me/preferences")
                .with(csrf())
                .with(authentication(richAuth(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"defaultTileId\":\"" + tileId + "\"}"))
        .andExpect(status().isBadRequest());
  }

  private static Authentication richAuth(UUID userId) {
    var authorities =
        List.of(
            new SimpleGrantedAuthority("SCOPE_profile"), new SimpleGrantedAuthority(Roles.ADMIN));
    var principal =
        new OpenCopPrincipal(
            userId,
            "https://issuer.test",
            "stable-user-id",
            "test-subject",
            new OpenCopPrincipal.Profile(
                "preferred-user", "Jane Doe", "Jane", "Doe", "jane@example.com", true),
            0,
            Instant.parse("2026-03-16T09:00:00Z"));
    return new UsernamePasswordAuthenticationToken(principal, null, authorities);
  }

  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture = UserEndpointsIT.class.getResourceAsStream(LEGACY_PRINCIPAL_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }
}
