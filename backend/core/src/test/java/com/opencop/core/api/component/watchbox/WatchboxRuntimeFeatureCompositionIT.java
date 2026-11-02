// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.watchbox;

import static com.opencop.core.api.component.watchbox.WatchboxTestMapperIdentity.MAPPER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.FullyQualifiedConfigurationContextInitializer;
import com.opencop.core.api.component.support.fixture.ConnectionFixtures;
import com.opencop.core.api.component.support.fixture.LayerFixtures;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.usecase.runtimefeature.CheckRuntimeFeatureAvailabilityUsecase;
import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.api.general.technical.gateway.iam.IamRuntimeFeatureUserGroupAccess;
import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureActor;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureWriteResult;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.IamLayerRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.member.IamLayerMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.JpaRuntimeFeatureStore;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureAudienceUserGroupJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureEntity;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureHistoryJpaRepository;
import com.opencop.core.sdk.technical.gateway.db.runtimefeature.RuntimeFeatureJpaRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.ObjectMapper;

/**
 * Proves the persisted watchbox audience changes real Watchbox HTTP behavior. The shared Watchbox
 * slice replaces {@link CheckRuntimeFeatureAvailability} with an always-true bean; this test
 * overrides it with the production use case backed by the real JPA store so a catalog,
 * configuration, or store regression cannot hide behind the stub.
 */
@ComponentWebIT(
    classes = {
      WatchboxWebTestApplication.class,
      WatchboxRuntimeFeatureCompositionIT.RealRuntimeFeatureComposition.class
    })
@ContextConfiguration(initializers = FullyQualifiedConfigurationContextInitializer.class)
class WatchboxRuntimeFeatureCompositionIT extends AbstractWebIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443201");
  private static final UUID NON_MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443205");
  private static final UUID FEATURE_ONLY_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443206");
  private static final UUID FEATURE_GROUP_ID =
      UUID.fromString("55555555-5555-4555-8555-555555555505");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443202");
  private static final Authentication OWNER_AUTH = TestAuthentications.oidcUser(OWNER_ID);
  private static final Authentication NON_MEMBER_AUTH = TestAuthentications.oidcUser(NON_MEMBER_ID);
  private static final Authentication FEATURE_ONLY_AUTH =
      TestAuthentications.oidcUser(FEATURE_ONLY_ID);
  private static final RuntimeFeatureActor ACTOR =
      new RuntimeFeatureActor(OWNER_ID, Optional.empty(), Optional.empty(), false);

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSourceRepository dataSourceRepository;
  @Autowired private MapperProfileService mapperProfileService;
  @Autowired private IamLayerRepository layerRepository;
  @Autowired private IamLayerMembersRepository layerMembersRepository;
  @Autowired private JpaRuntimeFeatureStore runtimeFeatureStore;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

  @Autowired
  private com.opencop.core.api.iam.technical.gateway.db.server.layer.IamServerLayerRepository
      serverLayerRepository;

  private UUID layerId;

  @BeforeEach
  void setUp() {
    var connectionFixtures =
        new ConnectionFixtures(jdbcTemplate, dataSourceRepository, objectMapper);
    var layerFixtures =
        new LayerFixtures(
            jdbcTemplate, layerRepository, layerMembersRepository, serverLayerRepository);

    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    resetRuntimeFeatureState();
    jdbcTemplate.update("DELETE FROM iam.user_group_member WHERE group_id = ?", FEATURE_GROUP_ID);
    jdbcTemplate.update("DELETE FROM iam.user_group WHERE id = ?", FEATURE_GROUP_ID);
    layerFixtures.reset();
    connectionFixtures.reset();
    insertUser(OWNER_ID);
    insertUser(NON_MEMBER_ID);
    insertUser(FEATURE_ONLY_ID);
    connectionFixtures.seedDefaultMapper(MAPPER_ID);
    connectionFixtures.persistTcpSource(SOURCE_ID, "composition-source", MAPPER_ID, true, null);
    layerId =
        layerFixtures.persistLayerWithMembers(
            "Composition Layer", Map.of(OWNER_ID, LayerRole.OWNER, NON_MEMBER_ID, LayerRole.OWNER));
    layerFixtures.addServerAccess(SOURCE_ID, layerId, ServerRole.VIEWER);
    when(mapperProfileService.getLatestMapperProfileFull(MAPPER_ID))
        .thenReturn(
            new MapperProfileFullMeta(
                MapperProfile.builder()
                    .withId(MAPPER_ID)
                    .withName("test-mapper")
                    .withEditable(false)
                    .withIntegrationId(WatchboxTestMapperIdentity.INTEGRATION_ID)
                    .withVersion(1)
                    .withArchived(false)
                    .build(),
                null,
                null));
  }

  // The embedded database is shared JVM-wide; leftover watchbox/history rows reference this
  // class's user and break later classes that blanket-delete opencop_user.
  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_config_audit");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_source_status");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox_layer");
    jdbcTemplate.update("DELETE FROM watchbox.watchbox");
    resetRuntimeFeatureState();
    jdbcTemplate.update("DELETE FROM iam.user_group_member WHERE group_id = ?", FEATURE_GROUP_ID);
    jdbcTemplate.update("DELETE FROM iam.user_group WHERE id = ?", FEATURE_GROUP_ID);
  }

  @Test
  void persistedAudienceGatesWatchboxHttpBehavior() throws Exception {
    var blocked =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("composition-proof")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(readResponse(blocked, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED);
    assertThat(countWatchboxes()).isZero();

    var enabled =
        runtimeFeatureStore.replaceAudience(
            "watchbox", new RuntimeFeatureAudience.All(), OptionalLong.of(0), ACTOR);
    assertThat(enabled).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);

    var created =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("composition-proof")))
            .andExpect(status().isCreated())
            .andReturn();
    var watchbox = readResponse(created, WatchboxResponse.class);
    assertThat(countWatchboxes()).isEqualTo(1);

    var disabledAgain =
        runtimeFeatureStore.replaceAudience(
            "watchbox", new RuntimeFeatureAudience.None(), OptionalLong.of(1), ACTOR);
    assertThat(disabledAgain).isInstanceOf(RuntimeFeatureWriteResult.Applied.class);

    mockMvc
        .perform(
            patch("/v1/watchboxes/" + watchbox.id())
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk());

    var renameBlocked =
        mockMvc
            .perform(
                patch("/v1/watchboxes/" + watchbox.id())
                    .with(csrf())
                    .with(authentication(OWNER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"composition-proof-renamed\"}"))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(readResponse(renameBlocked, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED);
  }

  @Test
  void userGroupAudienceAndLayerAuthorizationRemainIndependent() throws Exception {
    jdbcTemplate.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, 'watchbox-runtime-feature-group', 'watchbox composition fixture')",
        FEATURE_GROUP_ID);
    jdbcTemplate.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?), (?, ?)",
        FEATURE_GROUP_ID,
        OWNER_ID,
        FEATURE_GROUP_ID,
        FEATURE_ONLY_ID);
    var audience =
        new RuntimeFeatureAudience.UserGroups(
            new java.util.TreeSet<>(java.util.Set.of(FEATURE_GROUP_ID)));
    assertThat(runtimeFeatureStore.replaceAudience("watchbox", audience, OptionalLong.of(0), ACTOR))
        .isInstanceOf(RuntimeFeatureWriteResult.Applied.class);

    mockMvc
        .perform(
            post("/v1/watchboxes")
                .with(csrf())
                .with(authentication(OWNER_AUTH))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validCreateRequest("group-member-proof")))
        .andExpect(status().isCreated());

    var nonMemberResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(NON_MEMBER_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("layer-authorized-non-member-proof")))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertThat(readResponse(nonMemberResult, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_FEATURE_DISABLED);

    var featureOnlyResult =
        mockMvc
            .perform(
                post("/v1/watchboxes")
                    .with(csrf())
                    .with(authentication(FEATURE_ONLY_AUTH))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest("feature-only-proof")))
            .andExpect(status().isForbidden())
            .andReturn();
    assertThat(readResponse(featureOnlyResult, WatchboxErrorResponse.class).code())
        .isEqualTo(WatchboxErrorCode.WATCHBOX_MANAGEMENT_ROLE_REQUIRED);
    assertThat(countWatchboxes()).isEqualTo(1);
  }

  private void resetRuntimeFeatureState() {
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbcTemplate.update("DELETE FROM config.runtime_feature_history");
              jdbcTemplate.update(
                  "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
              jdbcTemplate.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0, updated_at = created_at WHERE name = 'watchbox'");
            });
  }

  private long countWatchboxes() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM watchbox.watchbox", Long.class);
  }

  private void insertUser(UUID userId) {
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
            id, idp_user_id, idp_username, idp_issuer, username, active, administrator, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        userId,
        "idp-user-" + userId,
        "user-" + userId,
        "issuer",
        "user-" + userId,
        true,
        false);
  }

  private String validCreateRequest(String name) {
    return """
        {
          "name":"%s",
          "description":"runtime feature composition proof",
          "direction":"BOTH",
          "layerIds":["%s"],
          "polygon":{"type":"Polygon","coordinates":[[[-100.2,39.8],[-99.8,39.8],[-99.8,40.2],[-100.2,40.2],[-100.2,39.8]]]},
          "filter":{"combine":"ALL","predicates":[{"path":"properties.opencop.element.symbol","operation":"IN","values":["10061000001211000000"]}]},
          "enabled":true
        }
        """
        .formatted(name, layerId);
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = RuntimeFeatureEntity.class)
  @EnableJpaRepositories(basePackageClasses = RuntimeFeatureJpaRepository.class)
  static class RealRuntimeFeatureComposition {

    @Bean
    JpaRuntimeFeatureStore compositionRuntimeFeatureStore(
        RuntimeFeatureJpaRepository featureRepository,
        RuntimeFeatureAudienceUserGroupJpaRepository audienceUserGroupRepository,
        RuntimeFeatureHistoryJpaRepository historyRepository,
        ObjectMapper objectMapper) {
      return new JpaRuntimeFeatureStore(
          featureRepository,
          audienceUserGroupRepository,
          historyRepository,
          objectMapper,
          Clock.systemUTC());
    }

    // @Primary beats the slice's always-true stub for injection into the Watchbox use case.
    @Bean
    @Primary
    CheckRuntimeFeatureAvailability realCheckRuntimeFeatureAvailability(
        JpaRuntimeFeatureStore store,
        IamGroupRepository groupRepository,
        IamGroupMembersRepository groupMembersRepository) {
      var audiencePolicy =
          new RuntimeFeatureAudiencePolicy(
              new IamRuntimeFeatureUserGroupAccess(groupRepository, groupMembersRepository));
      return new CheckRuntimeFeatureAvailabilityUsecase(store, audiencePolicy);
    }
  }
}
