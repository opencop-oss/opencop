// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.MapTileFixtures;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileMemberEntity;
import com.opencop.core.api.general.technical.gateway.tile.access.MapTileMemberRepository;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.map.tile.RasterConfig;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Route placement against the production filter chain.
 *
 * <p>Everything under /v1/admin/** requires ROLE_admin before any controller runs, so a
 * resource-scoped route placed there makes its tile role unreachable no matter what the use case
 * authorizes. The component-test chain only authenticates /v1/**, so it cannot catch that; these
 * tests boot the real SecurityConfig instead.
 */
@ComponentWebIT(classes = OidcAdmissionTestApplication.class)
class MapTileSecurityIT extends AbstractWebIT {

  private static final UUID GRANTED_USER_ID = UUID.randomUUID();
  private static final UUID STRANGER_ID = UUID.randomUUID();

  private static final String ISSUER = "http://localhost/test-issuer";
  private static final UUID GLOBAL_ADMIN_ID = UUID.randomUUID();

  /**
   * Built directly rather than through TestPrincipals, which hardcodes the IdP subject. The
   * production chain resolves the current account by IdP subject, so principals sharing a subject
   * all resolve to the same account and these users could not be told apart.
   */
  private static OpenCopPrincipal principal(UUID userId) {
    return new OpenCopPrincipal(
        userId,
        ISSUER,
        "idp-" + userId,
        "idp-" + userId,
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        0L,
        Instant.EPOCH);
  }

  /** No ROLE_admin: an ordinary user who has been granted access to the tile itself. */
  private static final Authentication GRANTED_USER =
      new UsernamePasswordAuthenticationToken(principal(GRANTED_USER_ID), null, List.of());

  private static final Authentication STRANGER =
      new UsernamePasswordAuthenticationToken(principal(STRANGER_ID), null, List.of());

  private static final Authentication GLOBAL_ADMIN =
      new UsernamePasswordAuthenticationToken(
          principal(GLOBAL_ADMIN_ID).withRequestCurrentGlobalAdministrator(true),
          null,
          List.of(new SimpleGrantedAuthority(Roles.ADMIN)));

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  @Autowired
  @org.springframework.beans.factory.annotation.Qualifier("springSessionRepositoryFilter")
  private jakarta.servlet.Filter sessionRepositoryFilter;

  @Autowired private MapTileRepository mapTileRepository;
  @Autowired private MapTileMemberRepository memberRepository;
  @MockitoBean private SecretStore secretStore;
  @MockitoBean private UserPreferenceRepository userPreferenceRepository;
  @MockitoBean private RetrieveMedia retrieveMedia;

  @MockitoBean
  private com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry
      streamAuthorizationTelemetry;

  @DynamicPropertySource
  static void securityChainProperties(DynamicPropertyRegistry registry) {
    // The production chain needs a session store and an issuer to construct its oauth2 beans. The
    // login flow itself is not exercised: each request carries an injected Authentication, so what
    // is under test is purely which matcher the path falls into.
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
    registry.add("redis.host", LocalRedisSupport::host);
    registry.add("redis.port", LocalRedisSupport::port);
    registry.add("opencop.domain", () -> "https://opencop.test");
    registry.add("opencop.idp.issuer", () -> "");
    registry.add("idp.user-id-claim", () -> "sub");
    registry.add("idp.audience", () -> "CORE-API");
    registry.add("idp.allowed.0.group", () -> "group-a");
    registry.add("idp.allowed.0.roles", () -> "operator");
    registry.add("idp.admin.client-id", () -> "test-client");
    registry.add("idp.admin.client-secret", () -> "test-secret");
    registry.add("idp.authorization-uri", () -> "https://idp.test/authorize");
    registry.add("idp.token-uri", () -> "https://idp.test/token");
    registry.add("idp.jwk-set-uri", () -> "https://idp.test/jwks");
    registry.add("idp.user-info-uri", () -> "https://idp.test/userinfo");
  }

  // The login flow is not under test here; only the filter chain's path matching is. These are the
  // collaborators SecurityConfig needs to construct its oauth2 beans.
  @MockitoBean private com.opencop.core.api.general.ports.technical.gateway.UserService userService;

  private MapTileFixtures fixtures;
  private UUID tileId;

  /**
   * The production chain resolves a current account per request, so an unprovisioned principal is
   * refused before path matching is ever reached. Seeding real rows keeps these tests measuring
   * route placement rather than account provisioning.
   */
  private void provision(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email,
          first_name, last_name, display_name, active, administrator,
          session_generation, account_access_revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "idp-" + userId,
        "user-" + userId,
        "http://localhost/test-issuer",
        "user-" + userId,
        userId + "@example.test",
        "Test",
        "User",
        "Test User",
        true,
        false,
        0L,
        0L);
  }

  @BeforeEach
  void seedTileWithGrants() {
    mockMvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .addFilters(sessionRepositoryFilter)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
    provision(GRANTED_USER_ID);
    provision(STRANGER_ID);
    provision(GLOBAL_ADMIN_ID);
    fixtures = new MapTileFixtures(mapTileRepository);
    fixtures.reset();
    tileId =
        fixtures.persistMapTile(
            UUID.randomUUID(),
            false,
            true,
            MapTileAccessScope.PRIVATE,
            "Scoped",
            "Scoped description",
            "https://tiles.example.com/scoped/{z}/{x}/{y}.png",
            MapTileFixtures.basemapConfig(new RasterConfig(256, 0, 18)),
            null);
    memberRepository.save(new MapTileMemberEntity(tileId, GRANTED_USER_ID));
  }

  /**
   * A grant confers visibility, not management. These routes are resource-scoped rather than under
   * /v1/admin, so this is the check that stops a granted user editing what they can merely see.
   */
  @Test
  void aGrantedUserCannotPatchThroughTheProductionChain() throws Exception {
    mockMvc
        .perform(renamePatch(GRANTED_USER, "Renamed by a viewer"))
        .andExpect(status().isForbidden());
    assertEquals("Scoped", fixtures.load(tileId).getName());
  }

  @Test
  void aGrantedUserCannotDeleteThroughTheProductionChain() throws Exception {
    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", tileId)
                .with(csrf().asHeader())
                .with(authentication(GRANTED_USER)))
        .andExpect(status().isForbidden());
  }

  /** The positive control: management still works for the people who are supposed to have it. */
  @Test
  void aGlobalAdminCanPatchThroughTheProductionChain() throws Exception {
    mockMvc
        .perform(renamePatch(GLOBAL_ADMIN, "Renamed by a global admin"))
        .andExpect(status().isOk());
    assertEquals("Renamed by a global admin", fixtures.load(tileId).getName());
  }

  @Test
  void aStrangerCannotPatch() throws Exception {
    mockMvc.perform(renamePatch(STRANGER, "Nope")).andExpect(status().isForbidden());
    assertEquals("Scoped", fixtures.load(tileId).getName());
  }

  @Test
  void aStrangerCannotDelete() throws Exception {
    mockMvc
        .perform(
            delete("/v1/map/tiles/{id}", tileId)
                .with(csrf().asHeader())
                .with(authentication(STRANGER)))
        .andExpect(status().isForbidden());
  }

  @Test
  void theAdminCatalogStaysGlobalAdminOnly() throws Exception {
    // The counterpart guarantee: moving PATCH and DELETE out must not loosen /v1/admin/**.
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/v1/admin/map/tiles")
                .with(authentication(GRANTED_USER)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/v1/admin/map/tiles")
                .with(authentication(GLOBAL_ADMIN)))
        .andExpect(status().isOk());
  }

  // csrf().asHeader(), not the default csrf(): the chain backs CSRF with
  // HttpSessionCsrfTokenRepository under a custom X-XSRF-TOKEN header, and a token submitted as a
  // form parameter is rejected with 403 before authorization runs. The default form-parameter
  // csrf() makes these tests fail in a way that looks exactly like an authorization denial.
  private org.springframework.test.web.servlet.RequestBuilder renamePatch(
      Authentication as, String name) throws Exception {
    var body =
        new MapTilePatch(
            Optional.of(name),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return patch("/v1/map/tiles/{id}", tileId)
        .with(csrf().asHeader())
        .with(authentication(as))
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsBytes(body));
  }
}
