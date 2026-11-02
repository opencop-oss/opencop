// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.scim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.general.scim.ScimWebTestApplication.ControllableCurrentAccountAccessCacheGateway;
import com.opencop.core.api.component.general.scim.ScimWebTestApplication.ControllableScimUserMutationGateway;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.ScimFixtures;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.usecase.user.ScimUserMutationUsecase;
import com.opencop.core.api.general.ports.usecase.runtimefeature.ManageRuntimeFeatures;
import com.opencop.core.api.general.technical.endpoint.scim.ProvisioningEndpoint;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.map.MapCaptureJpaRepository;
import com.opencop.core.api.general.technical.gateway.tile.MapTileService;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.JpaRuntimeFeatureStore;
import com.unboundid.scim2.common.messages.PatchOperation;
import com.unboundid.scim2.common.messages.PatchRequest;
import com.unboundid.scim2.common.types.Email;
import com.unboundid.scim2.common.types.GroupResource;
import com.unboundid.scim2.common.types.Member;
import com.unboundid.scim2.common.types.Name;
import com.unboundid.scim2.common.types.UserResource;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = ScimWebTestApplication.class)
@TestPropertySource(properties = "opencop.admin.default=alpha@example.com")
class ProvisioningEndpointIT extends AbstractWebIT {

  private static final MediaType SCIM_JSON = MediaType.valueOf(ProvisioningEndpoint.SCIM_JSON);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ScimUserMutationUsecase scimUserMutationUsecase;
  @Autowired private ControllableScimUserMutationGateway scimUserGateway;
  @Autowired private ControllableCurrentAccountAccessCacheGateway cacheGateway;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private ManageRuntimeFeatures manageRuntimeFeatures;
  @Autowired private JpaRuntimeFeatureStore runtimeFeatureStore;
  @Autowired private RuntimeFeatureAudiencePolicy runtimeFeatureAudiencePolicy;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private MapTileService mapTileService;
  @MockitoBean private MapCaptureJpaRepository mapCaptureRepository;

  private ScimFixtures scimFixtures;
  private ExecutorService executor;

  @DynamicPropertySource
  static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
  }

  @BeforeEach
  void setUp() {
    scimFixtures = new ScimFixtures(jdbcTemplate);
    resetRuntimeFeature();
    scimFixtures.reset();
    scimUserGateway.reset();
    cacheGateway.reset();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    executor = Executors.newFixedThreadPool(2);

    when(mapTileService.getOne(any(UUID.class))).thenReturn(Optional.empty());
    when(mapCaptureRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    scimUserGateway.releaseFirstCreate();
    scimUserGateway.releaseFirstPatch();
    scimUserGateway.releaseFirstMutationResponse();
    cacheGateway.reset();
    executor.shutdownNow();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    resetRuntimeFeature();
    scimFixtures.reset();
  }

  private void resetRuntimeFeature() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbcTemplate.update("DELETE FROM config.runtime_feature_history");
              jdbcTemplate.update(
                  "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
              jdbcTemplate.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0, updated_at = created_at WHERE name = 'watchbox'");
            });
  }

  // Happy path
  @Test
  void createUser_returnsScimMetadataAndDoesNotCreatePreferenceRow() throws Exception {
    var createResult =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(user("ext-alpha", "alpha.user"))))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(createResult, UserResource.class);
    var createdUserId = UUID.fromString(created.getId());
    assertEquals("ext-alpha", created.getExternalId());
    assertEquals("alpha.user", created.getUserName());
    assertEquals("Alpha User", created.getDisplayName());
    assertEquals("User", created.getMeta().getResourceType());
    assertTrue(
        created
            .getMeta()
            .getLocation()
            .toString()
            .endsWith("/scim/v1/provisioning/Users/" + created.getId()));
    assertEquals(1, scimFixtures.countUsers());
    assertEquals(0, scimFixtures.countPreferencesForUser(createdUserId));
    assertEquals(1L, scimFixtures.userAccountAccessRevision(createdUserId));

    var getResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Users/{id}", createdUserId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    var fetched = readResponse(getResult, UserResource.class);
    assertEquals(created.getId(), fetched.getId());
    assertEquals("alpha@example.com", fetched.getEmails().getFirst().getValue());
    assertEquals(
        "alpha",
        jdbcTemplate.queryForObject(
            "SELECT username FROM opencop_user WHERE id = ?", String.class, createdUserId));
  }

  @Test
  void createUser_withUnknownCoreAttributeReturnsBadRequestWithoutCreatingUser() throws Exception {
    var endpoint = "/scim/v1/provisioning/Users";

    mockMvc
        .perform(
            post(endpoint)
                .servletPath(endpoint)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {
                      "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                      "externalId": "ext-typo",
                      "userName": "typo.user",
                      "displayName": "Typo User",
                      "emails": [{"value": "typo@example.com", "primary": true}],
                      "actve": false
                    }
                    """))
        .andExpect(status().isBadRequest());

    assertEquals(0, scimFixtures.countUsers());
  }

  @Test
  void createUser_withInactiveAccount_reactivatesSameRowAndRestoresDefaultAdministrator()
      throws Exception {
    var userId = createUser("ext-alpha", "alpha.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserAdministrator(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(user("ext-alpha", "alpha.user"))))
            .andExpect(status().isCreated())
            .andReturn();

    assertEquals(userId.toString(), readResponse(result, UserResource.class).getId());
    assertEquals(1, scimFixtures.countUsers());
    assertTrue(scimFixtures.userActive(userId));
    assertTrue(scimFixtures.userAdministrator(userId));
    assertFalse(scimFixtures.userHasDeletionTime(userId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(userId));
  }

  @Test
  void createUser_reactivationChangingFromDefaultAdministratorEmail_doesNotRestoreAdministrator()
      throws Exception {
    var userId = createUser("ext-alpha", "alpha.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserAdministrator(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            user("ext-alpha", "alpha.user", "other@example.com"))))
            .andExpect(status().isCreated())
            .andReturn();

    assertEquals(userId.toString(), readResponse(result, UserResource.class).getId());
    assertFalse(scimFixtures.userAdministrator(userId));
    assertEquals(
        "other@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, userId));
  }

  @Test
  void createUser_reactivationChangingToDefaultAdministratorEmail_grantsAdministrator()
      throws Exception {
    var userId = createUser("ext-bravo", "bravo.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserAdministrator(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            user("ext-bravo", "bravo.user", "alpha@example.com"))))
            .andExpect(status().isCreated())
            .andReturn();

    assertEquals(userId.toString(), readResponse(result, UserResource.class).getId());
    assertTrue(scimFixtures.userAdministrator(userId));
    assertEquals(
        "alpha@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, userId));
  }

  @Test
  void createUser_withConcurrentReactivations_returnsOneCreatedAndOneConflict() throws Exception {
    var userId = createUser("ext-alpha", "alpha.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));
    var requestBody = objectMapper.writeValueAsBytes(user("ext-alpha", "alpha.user"));
    scimUserGateway.coordinateNextTwoCreates();

    Future<MvcResult> first = submitCreate(requestBody);
    assertTrue(scimUserGateway.awaitFirstCreateReturned());
    Future<MvcResult> second = submitCreate(requestBody);
    assertTrue(scimUserGateway.awaitSecondCreateReturned());
    scimUserGateway.releaseFirstCreate();

    var results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
    assertEquals(
        List.of(201, 409),
        results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
    var conflict =
        results.stream()
            .filter(result -> result.getResponse().getStatus() == 409)
            .findFirst()
            .orElseThrow();
    assertEquals(SCIM_JSON.toString(), conflict.getResponse().getContentType());
    JsonNode error = objectMapper.readTree(conflict.getResponse().getContentAsByteArray());
    assertEquals("409", error.get("status").asText());
    assertEquals("User with externalId already exists: ext-alpha", error.get("detail").asText());
    assertTrue(scimFixtures.userActive(userId));
    assertFalse(scimFixtures.userHasDeletionTime(userId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(userId));
    assertEquals(1, scimFixtures.countUsers());
  }

  @Test
  void createUser_withInactiveUnboundUsername_reusesRowForNewExternalId() throws Exception {
    var userId = createUser("ext-bravo", "bravo.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));
    scimFixtures.setUserExternalId(userId, "");

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(user("ext-bravo-new", "bravo.user"))))
            .andExpect(status().isCreated())
            .andReturn();

    assertEquals(userId.toString(), readResponse(result, UserResource.class).getId());
    assertEquals(1, scimFixtures.countUsers());
    assertTrue(scimFixtures.userActive(userId));
    assertFalse(scimFixtures.userHasDeletionTime(userId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(userId));
  }

  @Test
  void createUser_withInactiveUsernameBoundToDifferentExternalId_remainsConflict()
      throws Exception {
    var userId = createUser("ext-charlie", "charlie.user");
    scimFixtures.setUserActive(userId, false);
    scimFixtures.setUserDeletedAt(userId, java.time.Instant.parse("2026-07-20T12:00:00Z"));

    mockMvc
        .perform(
            post("/scim/v1/provisioning/Users")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(user("ext-other", "charlie.user"))))
        .andExpect(status().isConflict());

    assertEquals(1, scimFixtures.countUsers());
    assertFalse(scimFixtures.userActive(userId));
    assertTrue(scimFixtures.userHasDeletionTime(userId));
  }

  @Test
  void createUser_withInactiveRequest_persistsInactiveAccount() throws Exception {
    var request = user("ext-delta", "delta.user");
    request.setActive(false);

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(result, UserResource.class);
    assertFalse(created.getActive());
    var createdUserId = UUID.fromString(created.getId());
    assertFalse(scimFixtures.userActive(createdUserId));
    assertEquals(1L, scimFixtures.userAccountAccessRevision(createdUserId));
  }

  @Test
  void createUser_whenRequiredCacheEvictionFails_rollsBackCompleteMutation() {
    cacheGateway.failNextRequiredEviction();

    assertThrows(
        RedisConnectionFailureException.class,
        () -> scimUserMutationUsecase.create(user("ext-alpha", "alpha.user")));

    assertEquals(0, scimFixtures.countUsers());
  }

  @Test
  void replaceUser_whenRequiredCacheEvictionFails_rollsBackProfileAndAccountMutation()
      throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    var cacheKey = RedisCurrentAccountAccessGateway.key(createdUserId);
    redisTemplate.opsForValue().set(cacheKey, "cached-before-failure");
    var replacement = replacement("ext-alpha", "renamed.user");
    replacement.setActive(false);
    cacheGateway.failNextRequiredEviction();

    assertThrows(
        RedisConnectionFailureException.class,
        () -> scimUserMutationUsecase.replace(createdUserId.toString(), replacement));

    assertEquals(
        "ext-alpha",
        jdbcTemplate.queryForObject(
            "SELECT idp_user_id FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "alpha.user",
        jdbcTemplate.queryForObject(
            "SELECT idp_username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "alpha",
        jdbcTemplate.queryForObject(
            "SELECT username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "Alpha",
        jdbcTemplate.queryForObject(
            "SELECT first_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "User",
        jdbcTemplate.queryForObject(
            "SELECT last_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "Alpha User",
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "alpha@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertTrue(scimFixtures.userActive(createdUserId));
    assertFalse(scimFixtures.userHasDeletionTime(createdUserId));
    assertEquals(1L, scimFixtures.userAccountAccessRevision(createdUserId));
    assertEquals("cached-before-failure", redisTemplate.opsForValue().get(cacheKey));
  }

  @Test
  void deleteUser_whenRequiredCacheEvictionFails_rollsBackDeletionAndAccountMutation()
      throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    var cacheKey = RedisCurrentAccountAccessGateway.key(createdUserId);
    redisTemplate.opsForValue().set(cacheKey, "cached-before-failure");
    cacheGateway.failNextRequiredEviction();

    assertThrows(
        RedisConnectionFailureException.class, () -> scimUserMutationUsecase.delete(createdUserId));

    assertTrue(scimFixtures.userActive(createdUserId));
    assertFalse(scimFixtures.userHasDeletionTime(createdUserId));
    assertEquals(1L, scimFixtures.userAccountAccessRevision(createdUserId));
    assertEquals("cached-before-failure", redisTemplate.opsForValue().get(cacheKey));
  }

  @Test
  void patchUser_doesNotOverrideExistingUsername() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    jdbcTemplate.update(
        "UPDATE opencop_user SET username = ? WHERE id = ?", "custom-user", createdUserId);

    var patchRequest =
        new PatchRequest(
            PatchOperation.replace("displayName", "Renamed User"),
            PatchOperation.replace("emails.value", "renamed@example.com"));

    scimUserMutationUsecase.patch(createdUserId, patchRequest);

    assertEquals(
        "custom-user",
        jdbcTemplate.queryForObject(
            "SELECT username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "Renamed User",
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "renamed@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
  }

  @Test
  void patchUserHttp_usesScimConverterAndDoesNotOverrideExistingUsername() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    jdbcTemplate.update(
        "UPDATE opencop_user SET username = ? WHERE id = ?", "custom-user", createdUserId);

    var patchRequest =
        """
        {"Operations":[
          {"op":"replace","path":"displayName","value":"Renamed User"},
          {"op":"replace","path":"emails.value","value":"renamed@example.com"}
        ]}
        """;

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Users/{id}", createdUserId)
                .servletPath("/scim/v1/provisioning/Users/" + createdUserId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(patchRequest))
        .andExpect(status().isOk());

    assertEquals(
        "custom-user",
        jdbcTemplate.queryForObject(
            "SELECT username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "Renamed User",
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "renamed@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
  }

  @Test
  void patchUserHttp_withOverlappingDisjointProfileWrites_preservesBothChanges() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    var displayNamePatch =
        """
        {"Operations":[
          {"op":"replace","path":"displayName","value":"Concurrent Name"}
        ]}
        """;
    var emailPatch =
        """
        {"Operations":[
          {"op":"replace","path":"emails.value","value":"concurrent@example.com"}
        ]}
        """;
    scimUserGateway.coordinateNextTwoPatches();

    Future<MvcResult> first = submitPatch(createdUserId, displayNamePatch);
    assertTrue(scimUserGateway.awaitFirstPatchReturned());
    Future<MvcResult> second = submitPatch(createdUserId, emailPatch);
    assertTrue(scimUserGateway.awaitSecondPatchStarted());
    boolean secondLoadedBeforeFirstCommit = scimUserGateway.awaitSecondPatchReturned();
    if (secondLoadedBeforeFirstCommit) {
      assertEquals(200, second.get(5, TimeUnit.SECONDS).getResponse().getStatus());
    }
    scimUserGateway.releaseFirstPatch();

    assertEquals(200, first.get(5, TimeUnit.SECONDS).getResponse().getStatus());
    if (!secondLoadedBeforeFirstCommit) {
      assertEquals(200, second.get(5, TimeUnit.SECONDS).getResponse().getStatus());
    }
    assertEquals(
        "Concurrent Name",
        jdbcTemplate.queryForObject(
            "SELECT display_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "concurrent@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
  }

  @Test
  void patchUserHttp_updatesSupportedFieldsFromScimJson() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");

    var patchRequest =
        """
        {"Operations":[
          {"op":"replace","path":"name.givenName","value":"Ada"},
          {"op":"replace","path":"name.familyName","value":"Lovelace"},
          {"op":"replace","path":"active","value":false},
          {"op":"replace","path":"emails.value","value":"ada@example.com"}
        ]}
        """;

    var result =
        mockMvc
            .perform(
                patch("/scim/v1/provisioning/Users/{id}", createdUserId)
                    .servletPath("/scim/v1/provisioning/Users/" + createdUserId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(patchRequest))
            .andExpect(status().isOk())
            .andReturn();

    var patched = readResponse(result, UserResource.class);
    assertEquals("Ada", patched.getName().getGivenName());
    assertEquals("Lovelace", patched.getName().getFamilyName());
    assertFalse(patched.getActive());
    assertEquals("ada@example.com", patched.getEmails().getFirst().getValue());

    assertEquals(
        "Ada",
        jdbcTemplate.queryForObject(
            "SELECT first_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "Lovelace",
        jdbcTemplate.queryForObject(
            "SELECT last_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertFalse(scimFixtures.userActive(createdUserId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(createdUserId));
    assertEquals(
        "ada@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
  }

  @Test
  void patchUserHttp_withMultipleActiveOperations_preservesParserOrderAndEvictsCache()
      throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    var cacheKey = RedisCurrentAccountAccessGateway.key(createdUserId);
    redisTemplate.opsForValue().set(cacheKey, "cached-before-patch");
    var patchRequest =
        """
        {"Operations":[
          {"op":"replace","path":"active","value":false},
          {"op":"replace","path":"name.givenName","value":"Ada"},
          {"op":"replace","path":"active","value":true},
          {"op":"replace","path":"emails.value","value":"ada@example.com"},
          {"op":"replace","path":"active","value":false}
        ]}
        """;

    var result =
        mockMvc
            .perform(
                patch("/scim/v1/provisioning/Users/{id}", createdUserId)
                    .servletPath("/scim/v1/provisioning/Users/" + createdUserId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(patchRequest))
            .andExpect(status().isOk())
            .andReturn();

    var patched = readResponse(result, UserResource.class);
    assertEquals(List.of(false, true, false), scimUserGateway.lastPatchActiveStates());
    assertFalse(patched.getActive());
    assertEquals("Ada", patched.getName().getGivenName());
    assertEquals("ada@example.com", patched.getEmails().getFirst().getValue());
    assertFalse(scimFixtures.userActive(createdUserId));
    assertEquals(4L, scimFixtures.userAccountAccessRevision(createdUserId));
    assertEquals(
        "Ada",
        jdbcTemplate.queryForObject(
            "SELECT first_name FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "ada@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertNull(redisTemplate.opsForValue().get(cacheKey));
  }

  @Test
  void replaceUserHttp_updatesScimFieldsButPreservesApplicationUsername() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    jdbcTemplate.update(
        "UPDATE opencop_user SET username = ? WHERE id = ?", "custom-user", createdUserId);

    var replacement = user("ext-alpha", "alpha.renamed");
    replacement.setSchemaUrns(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
    replacement.setActive(false);

    Name name = new Name();
    name.setGivenName("Renamed");
    name.setFamilyName("Person");
    replacement.setName(name);

    Email email = new Email();
    email.setValue("replacement@example.com");
    email.setPrimary(true);
    replacement.setEmails(List.of(email));

    var result =
        mockMvc
            .perform(
                put("/scim/v1/provisioning/Users/{id}", createdUserId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(replacement)))
            .andExpect(status().isOk())
            .andReturn();

    var replaced = readResponse(result, UserResource.class);
    assertEquals("alpha.renamed", replaced.getUserName());
    assertFalse(replaced.getActive());
    assertEquals("replacement@example.com", replaced.getEmails().getFirst().getValue());

    assertEquals(
        "custom-user",
        jdbcTemplate.queryForObject(
            "SELECT username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "alpha.renamed",
        jdbcTemplate.queryForObject(
            "SELECT idp_username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(
        "replacement@example.com",
        jdbcTemplate.queryForObject(
            "SELECT email FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(createdUserId));
  }

  @Test
  void replaceUser_withLaterCommittedWrite_returnsOperationLocalResponses() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");
    var firstReplacement = replacement("ext-alpha", "first.version");
    var secondReplacement = replacement("ext-alpha", "second.version");
    scimUserGateway.coordinateNextMutationResponse();

    Future<MvcResult> first =
        submitReplace(createdUserId, objectMapper.writeValueAsBytes(firstReplacement));
    assertTrue(scimUserGateway.awaitFirstMutationCommitted());
    var second =
        mockMvc
            .perform(
                put("/scim/v1/provisioning/Users/{id}", createdUserId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(secondReplacement)))
            .andExpect(status().isOk())
            .andReturn();
    scimUserGateway.releaseFirstMutationResponse();

    var firstResponse = readResponse(first.get(5, TimeUnit.SECONDS), UserResource.class);
    var secondResponse = readResponse(second, UserResource.class);
    assertEquals("first.version", firstResponse.getUserName());
    assertEquals("First", firstResponse.getName().getGivenName());
    assertEquals("first@example.com", firstResponse.getEmails().getFirst().getValue());
    assertEquals("second.version", secondResponse.getUserName());
    assertEquals("Second", secondResponse.getName().getGivenName());
    assertEquals("second@example.com", secondResponse.getEmails().getFirst().getValue());
    assertEquals(
        "second.version",
        jdbcTemplate.queryForObject(
            "SELECT idp_username FROM opencop_user WHERE id = ?", String.class, createdUserId));
    assertEquals(3L, scimFixtures.userAccountAccessRevision(createdUserId));
  }

  @Test
  void deleteUser_softDeletesWithoutRemovingUserRow() throws Exception {
    var createdUserId = createUser("ext-alpha", "alpha.user");

    mockMvc
        .perform(delete("/scim/v1/provisioning/Users/{id}", createdUserId).with(csrf()))
        .andExpect(status().isNoContent());

    assertEquals(1, scimFixtures.countUsers());
    assertFalse(scimFixtures.userActive(createdUserId));
    assertNotNull(
        jdbcTemplate.queryForObject(
            "SELECT deleted_at FROM opencop_user WHERE id = ?", Object.class, createdUserId));
    assertEquals(2L, scimFixtures.userAccountAccessRevision(createdUserId));

    var result =
        mockMvc
            .perform(get("/scim/v1/provisioning/Users/{id}", createdUserId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    assertFalse(readResponse(result, UserResource.class).getActive());
  }

  @Test
  void createUser_withDuplicateExternalIdOrUsernameReturnsConflict() throws Exception {
    createUser("ext-alpha", "alpha.user");

    mockMvc
        .perform(
            post("/scim/v1/provisioning/Users")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(user("ext-alpha", "other.user"))))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post("/scim/v1/provisioning/Users")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(user("ext-other", "alpha.user"))))
        .andExpect(status().isConflict());

    assertEquals(1, scimFixtures.countUsers());
  }

  @Test
  void createGroup_withMembers_linksUsersAndReturnsResolvedMembersOnReadback() throws Exception {
    var firstUserId = createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");

    GroupResource group = new GroupResource();
    group.setExternalId("grp-operators");
    group.setDisplayName("Operators");
    group.setMembers(
        List.of(new Member().setValue("ext-bravo"), new Member().setValue("ext-charlie")));

    var createGroupResult =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Groups")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(group)))
            .andExpect(status().isCreated())
            .andReturn();

    var createdGroup = readResponse(createGroupResult, GroupResource.class);
    var groupId = UUID.fromString(createdGroup.getId());
    assertEquals(1, scimFixtures.countGroups());
    assertEquals(2, scimFixtures.countGroupMemberships(groupId));

    var getGroupResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();

    var fetchedGroup = readResponse(getGroupResult, GroupResource.class);
    assertEquals("Operators", fetchedGroup.getDisplayName());
    assertEquals(2, fetchedGroup.getMembers().size());
    assertEquals("Group", fetchedGroup.getMeta().getResourceType());
    assertTrue(
        fetchedGroup.getMembers().stream()
            .map(Member::getValue)
            .toList()
            .containsAll(List.of(firstUserId.toString(), secondUserId.toString())));
  }

  @Test
  void replaceGroup_updatesDisplayNameAndReplacesMembershipSet() throws Exception {
    createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo", "ext-charlie");

    GroupResource replacement = new GroupResource();
    replacement.setExternalId("grp-operators");
    replacement.setDisplayName("Field Operators");
    replacement.setMembers(List.of(new Member().setValue("ext-charlie")));

    var replaceGroupResult =
        mockMvc
            .perform(
                put("/scim/v1/provisioning/Groups/{id}", groupId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(replacement)))
            .andExpect(status().isOk())
            .andReturn();

    var replacedGroup = readResponse(replaceGroupResult, GroupResource.class);
    assertEquals("Field Operators", replacedGroup.getDisplayName());
    assertEquals("Group", replacedGroup.getMeta().getResourceType());
    assertTrue(
        replacedGroup
            .getMeta()
            .getLocation()
            .toString()
            .endsWith("/scim/v1/provisioning/Groups/" + groupId));
    assertEquals(1, scimFixtures.countGroupMemberships(groupId));

    var getGroupResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    var fetchedGroup = readResponse(getGroupResult, GroupResource.class);
    assertEquals("Field Operators", fetchedGroup.getDisplayName());
    assertEquals(
        List.of(secondUserId.toString()),
        fetchedGroup.getMembers().stream().map(Member::getValue).toList());
  }

  @Test
  void deleteGroup_removesIamGroupAndMembershipsWithoutDisablingFormerUsers() throws Exception {
    var firstUserId = createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo", "ext-charlie");

    mockMvc
        .perform(
            delete("/scim/v1/provisioning/Groups/{id}", groupId).with(csrf()).accept(SCIM_JSON))
        .andExpect(status().isNoContent());

    assertEquals(0, scimFixtures.countGroups());
    assertEquals(0, scimFixtures.countGroupMemberships(groupId));
    assertTrue(scimFixtures.userActive(firstUserId));
    assertTrue(scimFixtures.userActive(secondUserId));
  }

  @Test
  void targetedScimGroupTracksMembershipReplacementAndHardDeleteImmediately() throws Exception {
    var firstUserId = createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-runtime-feature", "Runtime Feature Group", "ext-bravo");
    var audience = new RuntimeFeatureAudience.UserGroups(new TreeSet<>(Set.of(groupId)));

    var targeted =
        manageRuntimeFeatures.replaceAudience(
            "watchbox",
            audience,
            OptionalLong.of(0),
            new RuntimeFeatureActor(firstUserId, Optional.empty(), Optional.empty(), false));
    assertEquals(1, targeted.revision());
    assertTrue(runtimeFeatureAudiencePolicy.isAvailable(audience, firstUserId));
    assertFalse(runtimeFeatureAudiencePolicy.isAvailable(audience, secondUserId));

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"members","value":[{"value":"ext-charlie"}]}]}
                    """))
        .andExpect(status().isOk());

    assertFalse(runtimeFeatureAudiencePolicy.isAvailable(audience, firstUserId));
    assertTrue(runtimeFeatureAudiencePolicy.isAvailable(audience, secondUserId));
    var revisionBeforeDelete = targeted.revision();
    var historyBeforeDelete =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM config.runtime_feature_history", Long.class);

    mockMvc
        .perform(
            delete("/scim/v1/provisioning/Groups/{id}", groupId).with(csrf()).accept(SCIM_JSON))
        .andExpect(status().isNoContent());

    assertFalse(scimFixtures.groupExists(groupId));
    assertFalse(runtimeFeatureAudiencePolicy.isAvailable(audience, secondUserId));
    var rawState = runtimeFeatureStore.findByName("watchbox").orElseThrow();
    assertEquals(audience, rawState.audience());
    assertEquals(revisionBeforeDelete, rawState.revision());
    assertEquals(
        historyBeforeDelete,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM config.runtime_feature_history", Long.class));
  }

  @Test
  void patchGroup_replaceMembersConvergesToProvidedSet() throws Exception {
    var bravoId = createUser("ext-bravo", "bravo.user");
    var charlieId = createUser("ext-charlie", "charlie.user");
    createUser("ext-delta", "delta.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo", "ext-charlie");

    var patchRequest =
        """
        {"Operations":[{"op":"replace","path":"members","value":[{"value":"ext-charlie"},{"value":"ext-delta"}]}]}
        """;

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(patchRequest))
        .andExpect(status().isOk());

    assertEquals(2, scimFixtures.countGroupMemberships(groupId));
    var getResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    var fetched = readResponse(getResult, GroupResource.class);
    var memberValues = fetched.getMembers().stream().map(Member::getValue).toList();
    assertEquals(2, memberValues.size());
    assertFalse(memberValues.contains(bravoId.toString()));
    assertTrue(memberValues.contains(charlieId.toString()));
  }

  @Test
  void patchGroup_replaceMembersWithEmptyArrayClearsMembership() throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    "{\"Operations\":[{\"op\":\"replace\",\"path\":\"members\",\"value\":[]}]}"))
        .andExpect(status().isOk());

    assertEquals(0, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void patchGroup_replaceMembersWithMissingUserReturns404AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"members","value":[{"value":"missing-user"}]}]}
                    """))
        .andExpect(status().isNotFound());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void patchGroup_replaceMembersWithEntryMissingValueReturns400AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"members","value":[{"display":"Someone"}]}]}
                    """))
        .andExpect(status().isBadRequest());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void patchGroup_replaceMembersWithMixedValidAndMalformedReturns400AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"members","value":[{"value":"ext-charlie"},{"display":"Someone"}]}]}
                    """))
        .andExpect(status().isBadRequest());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
    var fetched =
        readResponse(
            mockMvc
                .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
                .andExpect(status().isOk())
                .andReturn(),
            GroupResource.class);
    assertEquals(1, fetched.getMembers().size());
  }

  @Test
  void patchGroup_addMemberWithEntryMissingValueReturns400AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"add","path":"members","value":[{"display":"Someone"}]}]}
                    """))
        .andExpect(status().isBadRequest());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void patchGroup_removeMemberWithEntryMissingValueReturns400AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"remove","path":"members","value":[{"display":"Someone"}]}]}
                    """))
        .andExpect(status().isBadRequest());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void createGroup_withMemberMissingValueReturns400AndRollsBackIamGroup() throws Exception {
    createUser("ext-bravo", "bravo.user");

    GroupResource group = new GroupResource();
    group.setExternalId("grp-malformed");
    group.setDisplayName("Malformed");
    group.setMembers(List.of(new Member().setValue("ext-bravo"), new Member()));

    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(group)))
        .andExpect(status().isBadRequest());

    assertEquals(0, scimFixtures.countGroupsByExternalId("grp-malformed"));
  }

  @Test
  void replaceGroup_withMemberMissingValueReturns400AndLeavesMembershipUnchanged()
      throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    GroupResource replacement = new GroupResource();
    replacement.setExternalId("grp-operators");
    replacement.setDisplayName("Operators");
    replacement.setMembers(List.of(new Member()));

    mockMvc
        .perform(
            put("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(replacement)))
        .andExpect(status().isBadRequest());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void createGroup_resolvesUuidShapedExternalIdMemberViaIssuerScopedLookup() throws Exception {
    String uuidShapedExternalId = UUID.randomUUID().toString();
    var internalUserId = createUser(uuidShapedExternalId, "uuid.user");

    GroupResource group = new GroupResource();
    group.setExternalId("grp-uuid-ext");
    group.setDisplayName("Uuid Ext Members");
    group.setMembers(List.of(new Member().setValue(uuidShapedExternalId)));

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Groups")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(group)))
            .andExpect(status().isCreated())
            .andReturn();

    var created = readResponse(result, GroupResource.class);
    var groupId = UUID.fromString(created.getId());
    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
    assertEquals(internalUserId.toString(), created.getMembers().getFirst().getValue());
  }

  @Test
  void patchGroup_rejectsMemberFromAnotherIssuerByInternalUuid() throws Exception {
    createUser("ext-bravo", "bravo.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");
    var otherIssuerUserId =
        scimFixtures.insertUser("https://other-idp.example.com", "ext-other", "other.user");

    var patchAddRequest =
        """
        {"Operations":[{"op":"add","path":"members","value":{"value":"%s"}}]}
        """
            .formatted(otherIssuerUserId);

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(patchAddRequest))
        .andExpect(status().isNotFound());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));

    GroupResource replacement = new GroupResource();
    replacement.setExternalId("grp-operators");
    replacement.setDisplayName("Operators");
    replacement.setMembers(List.of(new Member().setValue(otherIssuerUserId.toString())));
    mockMvc
        .perform(
            put("/scim/v1/provisioning/Groups/{id}", groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(replacement)))
        .andExpect(status().isNotFound());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
  }

  @Test
  void createGroup_conflictsWithExistingScimDisplayNameInSameIssuer() throws Exception {
    createGroup("grp-operators", "Operators");

    GroupResource duplicate = new GroupResource();
    duplicate.setExternalId("grp-other");
    duplicate.setDisplayName("Operators");
    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(duplicate)))
        .andExpect(status().isConflict());

    assertEquals(1, scimFixtures.countGroups());
  }

  @Test
  void createGroup_succeedsWhenDisplayNameCollidesOnlyWithAdminOrOtherIssuerGroups()
      throws Exception {
    scimFixtures.insertGroup("Operators", null, null);
    scimFixtures.insertGroup("Operators", "https://other-idp.example.com", "ext-other");

    GroupResource group = new GroupResource();
    group.setExternalId("grp-operators");
    group.setDisplayName("Operators");
    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(group)))
        .andExpect(status().isCreated());

    assertEquals(3, scimFixtures.countGroups());
  }

  @Test
  void replaceGroup_externalIdIsImmutable() throws Exception {
    var groupId = createGroup("grp-operators", "Operators");

    GroupResource changedExternalId = new GroupResource();
    changedExternalId.setExternalId("grp-renamed");
    changedExternalId.setDisplayName("Operators");
    mockMvc
        .perform(
            put("/scim/v1/provisioning/Groups/{id}", groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(changedExternalId)))
        .andExpect(status().isConflict());

    assertEquals(1, scimFixtures.countGroupsByExternalId("grp-operators"));
    assertEquals(0, scimFixtures.countGroupsByExternalId("grp-renamed"));

    GroupResource missingExternalId = new GroupResource();
    missingExternalId.setDisplayName("Field Operators");
    var result =
        mockMvc
            .perform(
                put("/scim/v1/provisioning/Groups/{id}", groupId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(missingExternalId)))
            .andExpect(status().isOk())
            .andReturn();
    var replaced = readResponse(result, GroupResource.class);
    assertEquals("grp-operators", replaced.getExternalId());
    assertEquals("Field Operators", replaced.getDisplayName());
  }

  @Test
  void patchGroup_replaceDisplayNameConflictsWithExistingScimGroup() throws Exception {
    createGroup("grp-operators", "Operators");
    var analystsId = createGroup("grp-analysts", "Analysts");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", analystsId)
                .servletPath("/scim/v1/provisioning/Groups/" + analystsId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"displayName","value":"Operators"}]}
                    """))
        .andExpect(status().isConflict());

    assertEquals("Analysts", scimFixtures.groupName(analystsId));
  }

  @Test
  void singleGroupOps_returnNotFoundForAdminCreatedGroup() throws Exception {
    var userId = createUser("ext-bravo", "bravo.user");
    var adminGroupId = scimFixtures.insertGroup("Local Admins", null, null);

    mockMvc
        .perform(get("/scim/v1/provisioning/Groups/{id}", adminGroupId).accept(SCIM_JSON))
        .andExpect(status().isNotFound());

    GroupResource replacement = new GroupResource();
    replacement.setExternalId("grp-attempt");
    replacement.setDisplayName("Hijacked");
    replacement.setMembers(List.of(new Member().setValue(userId.toString())));
    mockMvc
        .perform(
            put("/scim/v1/provisioning/Groups/{id}", adminGroupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(replacement)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", adminGroupId)
                .servletPath("/scim/v1/provisioning/Groups/" + adminGroupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"displayName","value":"Hijacked"}]}
                    """))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/scim/v1/provisioning/Groups/{id}", adminGroupId)
                .with(csrf())
                .accept(SCIM_JSON))
        .andExpect(status().isNotFound());

    assertTrue(scimFixtures.groupExists(adminGroupId));
    assertEquals("Local Admins", scimFixtures.groupName(adminGroupId));
  }

  @Test
  void singleGroupOps_returnNotFoundForOtherIssuerGroup() throws Exception {
    var otherIssuerGroupId =
        scimFixtures.insertGroup(
            "Other Issuer Group", "https://other-idp.example.com", "ext-other");

    mockMvc
        .perform(get("/scim/v1/provisioning/Groups/{id}", otherIssuerGroupId).accept(SCIM_JSON))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/scim/v1/provisioning/Groups/{id}", otherIssuerGroupId)
                .with(csrf())
                .accept(SCIM_JSON))
        .andExpect(status().isNotFound());

    assertTrue(scimFixtures.groupExists(otherIssuerGroupId));
  }

  @Test
  void patchGroup_addsMemberByInternalUserId() throws Exception {
    var firstUserId = createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo");

    var patchRequest =
        """
        {"Operations":[{"op":"add","path":"members","value":{"value":"%s"}}]}
        """
            .formatted(secondUserId);

    var patchResult =
        mockMvc
            .perform(
                patch("/scim/v1/provisioning/Groups/{id}", groupId)
                    .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(patchRequest))
            .andExpect(status().isOk())
            .andReturn();

    var patchedGroup = readResponse(patchResult, GroupResource.class);
    assertEquals("Group", patchedGroup.getMeta().getResourceType());
    assertTrue(
        patchedGroup
            .getMeta()
            .getLocation()
            .toString()
            .endsWith("/scim/v1/provisioning/Groups/" + groupId));

    var getGroupResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    var fetchedGroup = readResponse(getGroupResult, GroupResource.class);
    assertEquals(2, fetchedGroup.getMembers().size());
    assertTrue(
        fetchedGroup.getMembers().stream()
            .map(Member::getValue)
            .toList()
            .containsAll(List.of(firstUserId.toString(), secondUserId.toString())));
  }

  @Test
  void patchGroup_removesMemberByExternalId() throws Exception {
    createUser("ext-bravo", "bravo.user");
    var secondUserId = createUser("ext-charlie", "charlie.user");
    var groupId = createGroup("grp-operators", "Operators", "ext-bravo", "ext-charlie");

    var patchRequest =
        """
        {"Operations":[{"op":"remove","path":"members","value":[{"value":"ext-bravo"}]}]}
        """;

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(patchRequest))
        .andExpect(status().isOk());

    assertEquals(1, scimFixtures.countGroupMemberships(groupId));
    var getGroupResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Groups/{id}", groupId).accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    var fetchedGroup = readResponse(getGroupResult, GroupResource.class);
    assertEquals(
        List.of(secondUserId.toString()),
        fetchedGroup.getMembers().stream().map(Member::getValue).toList());
  }

  @Test
  void listGroups_filtersByDisplayNameAndOmitsMembersFromRows() throws Exception {
    createUser("ext-bravo", "bravo.user");
    createUser("ext-charlie", "charlie.user");
    createGroup("grp-operators", "Operators", "ext-bravo");
    createGroup("grp-analysts", "Analysts", "ext-charlie");

    var result =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Groups")
                    .queryParam("filter", "displayName eq \"Operators\"")
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    JsonNode row = body.get("Resources").get(0);
    assertEquals(1, body.get("totalResults").asInt());
    assertEquals("Operators", row.get("displayName").asText());
    assertTrue(!row.has("members") || row.get("members").isEmpty());
  }

  @Test
  void listGroups_filtersByScimIdAndExternalId() throws Exception {
    createUser("ext-bravo", "bravo.user");
    createUser("ext-charlie", "charlie.user");
    var operatorsId = createGroup("grp-operators", "Operators", "ext-bravo");
    createGroup("grp-analysts", "Analysts", "ext-charlie");

    var idResult =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Groups")
                    .queryParam("filter", "id eq \"%s\"".formatted(operatorsId))
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode idBody = objectMapper.readTree(idResult.getResponse().getContentAsByteArray());
    assertEquals(1, idBody.get("totalResults").asInt());
    assertEquals(operatorsId.toString(), idBody.get("Resources").get(0).get("id").asText());

    var externalIdResult =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Groups")
                    .queryParam("filter", "externalId eq \"grp-operators\"")
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode extBody =
        objectMapper.readTree(externalIdResult.getResponse().getContentAsByteArray());
    assertEquals(1, extBody.get("totalResults").asInt());
    assertEquals("grp-operators", extBody.get("Resources").get(0).get("externalId").asText());
    assertEquals(operatorsId.toString(), extBody.get("Resources").get(0).get("id").asText());
  }

  @Test
  void listGroups_filterByMalformedIdReturnsScimInvalidFilter() throws Exception {
    mockMvc
        .perform(
            get("/scim/v1/provisioning/Groups")
                .queryParam("filter", "id eq \"not-a-uuid\"")
                .accept(SCIM_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listGroups_filterByIdExcludesOtherIssuerAndAdminGroups() throws Exception {
    var otherIssuerGroupId =
        scimFixtures.insertGroup(
            "Other Issuer Group", "https://other-idp.example.com", "ext-other");
    var adminGroupId = scimFixtures.insertGroup("Local Admins", null, null);

    for (UUID excluded : List.of(otherIssuerGroupId, adminGroupId)) {
      var result =
          mockMvc
              .perform(
                  get("/scim/v1/provisioning/Groups")
                      .queryParam("filter", "id eq \"%s\"".formatted(excluded))
                      .accept(SCIM_JSON))
              .andExpect(status().isOk())
              .andReturn();
      JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
      assertEquals(0, body.get("totalResults").asInt());
    }
  }

  @Test
  void createGroup_withMissingMember_returnsNotFoundAndRollsBackIamGroup() throws Exception {
    createUser("ext-bravo", "bravo.user");

    GroupResource group = new GroupResource();
    group.setExternalId("grp-operators");
    group.setDisplayName("Operators");
    group.setMembers(
        List.of(new Member().setValue("ext-bravo"), new Member().setValue("missing-user")));

    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(group)))
        .andExpect(status().isNotFound());

    assertEquals(0, scimFixtures.countGroupsByExternalId("grp-operators"));
  }

  @Test
  void listUsers_filtersAndNormalizesPaginationInputs() throws Exception {
    createUser("ext-delta", "delta.user");
    createUser("ext-echo", "echo.user");

    var result =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Users")
                    .queryParam("filter", "userName eq \"echo.user\"")
                    .queryParam("startIndex", "0")
                    .queryParam("count", "0")
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals(1, body.get("totalResults").asInt());
    assertEquals(1, body.get("startIndex").asInt());
    assertEquals(1, body.get("itemsPerPage").asInt());
    assertEquals("echo.user", body.get("Resources").get(0).get("userName").asText());
  }

  @Test
  void listUsers_filtersByExternalIdAndInternalId() throws Exception {
    createUser("ext-delta", "delta.user");
    var echoUserId = createUser("ext-echo", "echo.user");

    var externalIdResult =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Users")
                    .queryParam("filter", "externalId eq \"ext-echo\"")
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode externalIdBody =
        objectMapper.readTree(externalIdResult.getResponse().getContentAsByteArray());
    assertEquals(1, externalIdBody.get("totalResults").asInt());
    assertEquals("ext-echo", externalIdBody.get("Resources").get(0).get("externalId").asText());

    var idResult =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Users")
                    .queryParam("filter", "id eq \"%s\"".formatted(echoUserId))
                    .accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode idBody = objectMapper.readTree(idResult.getResponse().getContentAsByteArray());
    assertEquals(1, idBody.get("totalResults").asInt());
    assertEquals(echoUserId.toString(), idBody.get("Resources").get(0).get("id").asText());
  }

  // Validation failures
  @Test
  void userInvalidInputs_returnScimErrors() throws Exception {
    UserResource missingUsername = user("ext-alpha", "alpha.user");
    missingUsername.setUserName(null);
    mockMvc
        .perform(
            post("/scim/v1/provisioning/Users")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(missingUsername)))
        .andExpect(status().isBadRequest());

    var createdUserId = createUser("ext-alpha", "alpha.user");
    UserResource changedExternalId = user("ext-other", "alpha.user");
    changedExternalId.setSchemaUrns(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
    mockMvc
        .perform(
            put("/scim/v1/provisioning/Users/{id}", createdUserId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(changedExternalId)))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Users/{id}", createdUserId)
                .servletPath("/scim/v1/provisioning/Users/" + createdUserId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[
                      {"op":"remove","path":"userName"},
                      {"op":"replace","path":"active","value":false}
                    ]}
                    """))
        .andExpect(status().isBadRequest());

    assertTrue(scimFixtures.userActive(createdUserId));
    assertEquals(1L, scimFixtures.userAccountAccessRevision(createdUserId));
  }

  @Test
  void listUsers_withInvalidFilter_returnsScimBadRequest() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/scim/v1/provisioning/Users")
                    .queryParam("filter", "displayName eq \"Alpha User\"")
                    .accept(SCIM_JSON))
            .andExpect(status().isBadRequest())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals("invalidFilter", body.get("scimType").asText());
    assertEquals("Invalid filter: displayName eq \"Alpha User\"", body.get("detail").asText());
  }

  @Test
  void groupInvalidInputs_returnScimErrors() throws Exception {
    var groupId = createGroup("grp-operators", "Operators");

    mockMvc
        .perform(
            patch("/scim/v1/provisioning/Groups/{id}", groupId)
                .servletPath("/scim/v1/provisioning/Groups/" + groupId)
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(
                    """
                    {"Operations":[{"op":"replace","path":"unsupported","value":"x"}]}
                    """))
        .andExpect(status().isBadRequest());

    GroupResource blankDisplayName = new GroupResource();
    blankDisplayName.setExternalId("grp-blank");
    blankDisplayName.setDisplayName(" ");
    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(blankDisplayName)))
        .andExpect(status().isBadRequest());

    GroupResource duplicate = new GroupResource();
    duplicate.setExternalId("grp-operators");
    duplicate.setDisplayName("Duplicate Operators");
    mockMvc
        .perform(
            post("/scim/v1/provisioning/Groups")
                .with(csrf())
                .contentType(SCIM_JSON)
                .content(objectMapper.writeValueAsBytes(duplicate)))
        .andExpect(status().isConflict());
  }

  // Other edge cases
  @Test
  void metadataEndpoints_returnScimContracts() throws Exception {
    var configResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/ServiceProviderConfig").accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode config = objectMapper.readTree(configResult.getResponse().getContentAsByteArray());
    assertTrue(config.get("patch").get("supported").asBoolean());
    assertEquals(200, config.get("filter").get("maxResults").asInt());
    assertEquals("bearer", config.get("authenticationSchemes").get(0).get("type").asText());

    var resourceTypeResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/ResourceTypes/User").accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode resourceType =
        objectMapper.readTree(resourceTypeResult.getResponse().getContentAsByteArray());
    assertEquals("User", resourceType.get("id").asText());
    assertEquals("ResourceType", resourceType.get("meta").get("resourceType").asText());

    var schemaResult =
        mockMvc
            .perform(get("/scim/v1/provisioning/Schemas/Group").accept(SCIM_JSON))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode schema = objectMapper.readTree(schemaResult.getResponse().getContentAsByteArray());
    assertEquals("urn:ietf:params:scim:schemas:core:2.0:Group", schema.get("id").asText());
    assertEquals("members", schema.get("attributes").get(1).get("name").asText());
  }

  private UUID createUser(String externalId, String username) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Users")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(user(externalId, username))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(readResponse(result, UserResource.class).getId());
  }

  private Future<MvcResult> submitCreate(byte[] requestBody) {
    return executor.submit(
        () ->
            mockMvc
                .perform(
                    post("/scim/v1/provisioning/Users")
                        .with(csrf())
                        .contentType(SCIM_JSON)
                        .content(requestBody))
                .andReturn());
  }

  private Future<MvcResult> submitReplace(UUID internalUserId, byte[] requestBody) {
    return executor.submit(
        () ->
            mockMvc
                .perform(
                    put("/scim/v1/provisioning/Users/{id}", internalUserId)
                        .with(csrf())
                        .contentType(SCIM_JSON)
                        .content(requestBody))
                .andReturn());
  }

  private Future<MvcResult> submitPatch(UUID internalUserId, String requestBody) {
    return executor.submit(
        () ->
            mockMvc
                .perform(
                    patch("/scim/v1/provisioning/Users/{id}", internalUserId)
                        .servletPath("/scim/v1/provisioning/Users/" + internalUserId)
                        .with(csrf())
                        .contentType(SCIM_JSON)
                        .content(requestBody))
                .andReturn());
  }

  private UUID createGroup(String externalId, String displayName, String... memberExternalIds)
      throws Exception {
    GroupResource group = new GroupResource();
    group.setExternalId(externalId);
    group.setDisplayName(displayName);
    group.setMembers(
        List.of(memberExternalIds).stream().map(value -> new Member().setValue(value)).toList());

    var result =
        mockMvc
            .perform(
                post("/scim/v1/provisioning/Groups")
                    .with(csrf())
                    .contentType(SCIM_JSON)
                    .content(objectMapper.writeValueAsBytes(group)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(readResponse(result, GroupResource.class).getId());
  }

  private static UserResource user(String externalId, String username) {
    return user(externalId, username, username.split("\\.")[0] + "@example.com");
  }

  private static UserResource user(String externalId, String username, String emailValue) {
    UserResource user = new UserResource();
    user.setExternalId(externalId);
    user.setUserName(username);
    user.setActive(true);

    Name name = new Name();
    name.setGivenName(capitalized(username.split("\\.")[0]));
    name.setFamilyName("User");
    user.setName(name);
    user.setDisplayName(capitalized(username.split("\\.")[0]) + " User");

    Email email = new Email();
    email.setValue(emailValue);
    email.setPrimary(true);
    user.setEmails(List.of(email));

    return user;
  }

  private static UserResource replacement(String externalId, String username) {
    var replacement = user(externalId, username);
    replacement.setSchemaUrns(List.of("urn:ietf:params:scim:schemas:core:2.0:User"));
    return replacement;
  }

  private static String capitalized(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
