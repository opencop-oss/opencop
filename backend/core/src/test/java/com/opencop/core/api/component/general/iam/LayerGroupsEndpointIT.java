// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.common.filter.SpaAuthenticationEntryPoint;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.ports.gateway.layergroup.LayerGroupService;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupContainerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupLayerManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupMemberManagement;
import com.opencop.core.api.general.ports.usecase.layergroup.LayerGroupUserGroupManagement;
import com.opencop.core.api.general.technical.endpoint.layergroup.AdminLayerGroupsEndpoint;
import com.opencop.core.api.general.technical.endpoint.layergroup.LayerGroupsEndpoint;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupCreateRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupLayersAssignRequest;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupMemberResponse;
import com.opencop.core.api.general.technical.model.layergroup.LayerGroupResponse;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.core.type.TypeReference;

/**
 * Proves over HTTP that an ordinary (non-admin) user can own and populate a PRIVATE layer group,
 * and that the scope and role gates around that capability actually deny.
 *
 * <p>{@code LayerGroupsEndpointTest} only checks that {@code createLayerGroup} carries no
 * {@code @PreAuthorize}, which is not the same as reachability: the admin gate for this area is the
 * {@code /v1/admin/**} → {@code hasRole("admin")} matcher in {@link SecurityConfig}. Moving the
 * route under {@code /v1/admin/} or widening that matcher would leave every annotation-shaped
 * assertion green while the feature was dead. The request-driven tests here run the real endpoint,
 * use case, policy and database against {@code POST /v1/layer-groups}, so relocating the mapping
 * fails them with a 404, and {@link
 * #realSecurityChain_admitsNonAdminToLayerGroupsAndBlocksAdminRoute()} boots the real chain that
 * the shared harness otherwise replaces with {@code TestSecurityConfig} (all of {@code /v1/**}
 * merely authenticated).
 */
@ComponentWebIT(classes = LayerGroupScopeWebTestApplication.class)
class LayerGroupsEndpointIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-ab0000000001");
  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-ab0000000002");
  private static final UUID PROBE_GROUP_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-ab00000000ff");

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedUsers() {
    insertUser(jdbcTemplate, USER_ID);
    insertUser(jdbcTemplate, ADMIN_ID);
  }

  @Test
  void createLayerGroup_nonAdmin_returns201PrivateGroupOwnedByCaller() throws Exception {
    var body =
        objectMapper.writeValueAsString(
            new LayerGroupCreateRequest("NonAdminOwnedGroup", "made by a plain user", null));

    var result =
        mockMvc
            .perform(
                post("/v1/layer-groups")
                    .with(csrf())
                    .with(authentication(userAuthentication(USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(result, LayerGroupResponse.class);
    assertNotNull(created.id());
    assertEquals(
        LayerGroupAccessScope.PRIVATE,
        created.accessScope(),
        "A group created without an explicit scope must come back PRIVATE");

    var membersResult =
        mockMvc
            .perform(
                get("/v1/layer-groups/{id}/members", created.id())
                    .with(authentication(userAuthentication(USER_ID))))
            .andExpect(status().isOk())
            .andReturn();

    List<LayerGroupMemberResponse> members = readResponse(membersResult, new TypeReference<>() {});
    assertEquals(1, members.size(), "The creator must be the group's only member");
    assertEquals(USER_ID, members.getFirst().userId());
    assertEquals(
        LayerGroupRole.OWNER, members.getFirst().role(), "The creator must be seated as OWNER");
  }

  @Test
  void createLayerGroup_nonAdminRequestingGlobalScope_returns403() throws Exception {
    var body =
        objectMapper.writeValueAsString(
            new LayerGroupCreateRequest(
                "NonAdminGlobalAttempt", null, LayerGroupAccessScope.GLOBAL));

    mockMvc
        .perform(
            post("/v1/layer-groups")
                .with(csrf())
                .with(authentication(userAuthentication(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iam.layer_group WHERE name = ?",
            Integer.class,
            "NonAdminGlobalAttempt");
    assertEquals(0, count, "A rejected GLOBAL request must not leave a group behind");
  }

  /**
   * The scope gate ({@code LayerGroupPolicy.assertLayerIsPrivate}): every user inherits MEMBER on
   * every GLOBAL layer, so without it an owner could pull the whole global catalogue into their own
   * group and share it onward.
   */
  @Test
  void assignLayers_nonAdminOwnerAssigningGlobalLayer_returns403() throws Exception {
    var groupId = createPrivateGroupAsNonAdmin("NonAdminGroupForGlobalLayer");
    var globalLayerId = insertLayer(jdbcTemplate, "NonAdminGlobalLayer", "GLOBAL");

    var body =
        objectMapper.writeValueAsString(
            new LayerGroupLayersAssignRequest(Set.of(globalLayerId), LayerRole.MEMBER));

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(userAuthentication(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertNoAssignment(groupId, globalLayerId);
  }

  /**
   * The EDITOR floor ({@code LayerGroupPolicy.assertCanAssignLayer}). Differs from {@link
   * #assignLayers_nonAdminWithEditorOnLayer_returns201()} only in the caller's role on the layer,
   * so the pair isolates the floor from any surrounding gate.
   */
  @Test
  void assignLayers_nonAdminWithOnlyMemberOnLayer_returns403() throws Exception {
    var groupId = createPrivateGroupAsNonAdmin("NonAdminGroupForMemberLayer");
    var layerId = insertLayer(jdbcTemplate, "NonAdminMemberLayer", "PRIVATE");
    insertLayerMember(jdbcTemplate, layerId, USER_ID, LayerRole.MEMBER.name());

    var body =
        objectMapper.writeValueAsString(
            new LayerGroupLayersAssignRequest(Set.of(layerId), LayerRole.MEMBER));

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(userAuthentication(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertNoAssignment(groupId, layerId);
  }

  @Test
  void assignLayers_nonAdminWithEditorOnLayer_returns201() throws Exception {
    var groupId = createPrivateGroupAsNonAdmin("NonAdminGroupForEditorLayer");
    var layerId = insertLayer(jdbcTemplate, "NonAdminEditorLayer", "PRIVATE");
    insertLayerMember(jdbcTemplate, layerId, USER_ID, LayerRole.EDITOR.name());

    var body =
        objectMapper.writeValueAsString(
            new LayerGroupLayersAssignRequest(Set.of(layerId), LayerRole.MEMBER));

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(userAuthentication(USER_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].layerId").value(layerId.toString()));

    assertAssigned(groupId, layerId);
  }

  /**
   * The scope restriction applies only to non-admins. A global admin must still be able to assign a
   * GLOBAL layer into a PRIVATE group.
   */
  @Test
  void assignLayers_globalAdminAssigningGlobalLayerIntoPrivateGroup_returns201() throws Exception {
    var groupId = insertLayerGroup(jdbcTemplate, "AdminGroupForGlobalLayer", "PRIVATE");
    insertLayerGroupMember(jdbcTemplate, groupId, ADMIN_ID, LayerGroupRole.OWNER.name());
    var globalLayerId = insertLayer(jdbcTemplate, "AdminGlobalLayer", "GLOBAL");

    var body =
        objectMapper.writeValueAsString(
            new LayerGroupLayersAssignRequest(Set.of(globalLayerId), LayerRole.MEMBER));

    mockMvc
        .perform(
            post("/v1/layer-groups/{id}/layers", groupId)
                .with(csrf())
                .with(authentication(adminAuthentication(ADMIN_ID)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(1));

    assertAssigned(groupId, globalLayerId);
  }

  /**
   * Boots the production {@link SecurityConfig} chain, the only place the {@code /v1/admin/**} →
   * {@code hasRole("admin")} matcher exists, and drives the same non-admin principal at both
   * routes. Endpoint collaborators are stubbed because the subject is the routing decision, not the
   * use case that the database-backed tests above cover. The admin-route denial asserts the exact
   * body: a CSRF rejection is also a 403 but carries {@code "code":"CSRF_REJECTED"}, so matching
   * the plain forbidden payload proves the denial came from the path matcher.
   */
  @Test
  void realSecurityChain_admitsNonAdminToLayerGroupsAndBlocksAdminRoute() {
    securityChainContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(
                      post("/v1/layer-groups")
                          .with(csrf().asHeader())
                          .with(authentication(userAuthentication(USER_ID)))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"name\":\"ChainProbe\"}"))
                  .andExpect(status().isCreated());

              chainMvc
                  .perform(
                      post("/v1/admin/layer-groups")
                          .with(csrf().asHeader())
                          .with(authentication(userAuthentication(USER_ID)))
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{\"name\":\"ChainProbe\"}"))
                  .andExpect(status().isForbidden())
                  .andExpect(content().string("{\"error\":\"forbidden\"}"));
            });
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private UUID createPrivateGroupAsNonAdmin(String name) throws Exception {
    var body = objectMapper.writeValueAsString(new LayerGroupCreateRequest(name, null, null));
    var result =
        mockMvc
            .perform(
                post("/v1/layer-groups")
                    .with(csrf())
                    .with(authentication(userAuthentication(USER_ID)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return readResponse(result, LayerGroupResponse.class).id();
  }

  private void assertAssigned(UUID groupId, UUID layerId) {
    assertEquals(
        1, countAssignments(groupId, layerId), "The layer should be assigned to the layer group");
  }

  private void assertNoAssignment(UUID groupId, UUID layerId) {
    assertEquals(0, countAssignments(groupId, layerId), "A denied assignment must persist nothing");
  }

  private Integer countAssignments(UUID groupId, UUID layerId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM iam.layer_group_layer WHERE layer_group_id = ? AND layer_id = ?",
        Integer.class,
        groupId,
        layerId);
  }

  private static WebApplicationContextRunner securityChainContext() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(RealSecurityChainConfig.class, SecurityConfig.class)
        .withPropertyValues(
            "opencop.domain=https://opencop.test",
            "idp.audience=test-audience",
            "idp.user-id-claim=sub",
            "opencop.idp.issuer=",
            "idp.admin.client-id=test-client",
            "idp.admin.client-secret=test-value",
            "idp.authorization-uri=https://idp.test/authorize",
            "idp.token-uri=https://idp.test/oauth2/v2.0/access",
            "idp.jwk-set-uri=https://idp.test/discovery/keys",
            "idp.user-info-uri=https://idp.test/oidc/userinfo");
  }

  private static UsernamePasswordAuthenticationToken adminAuthentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        TestPrincipals.oidcPrincipal(userId, true),
        null,
        List.of(new SimpleGrantedAuthority(Roles.ADMIN)));
  }

  private static UsernamePasswordAuthenticationToken userAuthentication(UUID userId) {
    return new UsernamePasswordAuthenticationToken(
        TestPrincipals.oidcPrincipal(userId, false), null, List.of());
  }

  private static void insertUser(JdbcTemplate jdbc, UUID userId) {
    jdbc.update(
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
        "user@example.test",
        "Test",
        "User",
        "Test User");
  }

  private static UUID insertLayerGroup(JdbcTemplate jdbc, String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO iam.layer_group (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private static void insertLayerGroupMember(
      JdbcTemplate jdbc, UUID groupId, UUID userId, String role) {
    jdbc.update(
        "INSERT INTO iam.layer_group_member (layer_group_id, user_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_group_role))",
        groupId,
        userId,
        role);
  }

  private static UUID insertLayer(JdbcTemplate jdbc, String name, String accessScope) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO iam.layer (id, name, access_scope)"
            + " VALUES (?, ?, CAST(? AS iam.layer_access_scope))",
        id,
        name,
        accessScope);
    return id;
  }

  private static void insertLayerMember(JdbcTemplate jdbc, UUID layerId, UUID userId, String role) {
    jdbc.update(
        "INSERT INTO iam.layer_member (layer_id, user_id, role)"
            + " VALUES (?, ?, CAST(? AS iam.layer_role))",
        layerId,
        userId,
        role);
  }

  /**
   * The minimum bean graph {@link SecurityConfig} needs to stand up, plus the two layer-group
   * controllers so the chain has real mappings to route to. Collaborators the probe never reaches
   * are stubbed, but {@code CurrentAccountAccessFilter} is real because a mock would swallow the
   * request instead of continuing the chain; the real one passes straight through for the probe's
   * non-OAuth2 authentication.
   */
  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSecurity
  // Boot's auto-configuration is absent here, so MVC must be enabled explicitly; without it there
  // is no JSON message converter and the controllers answer 415.
  @EnableWebMvc
  @Import({LayerGroupsEndpoint.class, AdminLayerGroupsEndpoint.class})
  static class RealSecurityChainConfig {

    @Bean
    LayerGroupContainerManagement layerGroupContainerManagement() {
      var useCase = mock(LayerGroupContainerManagement.class);
      when(useCase.create(any(), any()))
          .thenReturn(
              new LayerGroupResponse(
                  PROBE_GROUP_ID,
                  "ChainProbe",
                  null,
                  LayerGroupAccessScope.PRIVATE,
                  Instant.EPOCH));
      return useCase;
    }

    @Bean
    LayerGroupMemberManagement layerGroupMemberManagement() {
      return mock(LayerGroupMemberManagement.class);
    }

    @Bean
    LayerGroupLayerManagement layerGroupLayerManagement() {
      return mock(LayerGroupLayerManagement.class);
    }

    @Bean
    LayerGroupUserGroupManagement layerGroupUserGroupManagement() {
      return mock(LayerGroupUserGroupManagement.class);
    }

    @Bean
    LayerGroupService layerGroupService() {
      return mock(LayerGroupService.class);
    }

    @Bean
    CurrentAccountAccessFilter currentAccountAccessFilter(
        SpaAuthenticationEntryPoint authenticationEntryPoint,
        SessionLifetimePolicy sessionLifetimePolicy) {
      return new CurrentAccountAccessFilter(
          mock(ResolveCurrentAccountAccessUsecase.class),
          mock(CurrentAccountAuthenticationFactory.class),
          authenticationEntryPoint,
          mock(CurrentAccountSessionTelemetry.class),
          sessionLifetimePolicy);
    }

    @Bean
    UserService userService() {
      return mock(UserService.class);
    }

    @Bean
    CurrentAccountAccessFactory currentAccountAccessFactory() {
      return new CurrentAccountAccessFactory();
    }

    @Bean
    CurrentAccountAccessPolicy currentAccountAccessPolicy() {
      return new CurrentAccountAccessPolicy();
    }

    @Bean
    StreamAuthorizationTelemetry streamAuthorizationTelemetry(MeterRegistry meterRegistry) {
      return new StreamAuthorizationTelemetry(meterRegistry);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    CookieSerializer cookieSerializer() {
      return new DefaultCookieSerializer();
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }
  }
}
