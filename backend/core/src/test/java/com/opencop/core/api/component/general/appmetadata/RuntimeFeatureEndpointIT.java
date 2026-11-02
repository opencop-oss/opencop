// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class RuntimeFeatureEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443101");
  private static final UUID GROUP_A = UUID.fromString("11111111-1111-4111-8111-111111111101");
  private static final UUID GROUP_B = UUID.fromString("22222222-2222-4222-8222-222222222202");

  // Mirrors RuntimeFeatureEndpoint.MAX_RECONCILE_NAMES, which is private. The pair of cap tests
  // pins it from both sides — this many names must be accepted, one more must be rejected — so the
  // mirror cannot silently drift from the endpoint's real bound in either direction.
  private static final int MAX_RECONCILE_NAMES = 200;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void resetRuntimeFeatures() {
    resetRuntimeFeatures(true);
  }

  private void resetRuntimeFeatures(boolean insertAdmin) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update("DELETE FROM config.runtime_feature_history");
              jdbc.update(
                  "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
              // Registration tests create rows; restore the migration-seeded catalog.
              jdbc.update(
                  "DELETE FROM config.runtime_feature WHERE name NOT IN ('watchbox', 'rtsp')");
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0, updated_at = created_at WHERE name = 'watchbox'");
              jdbc.update(
                  "DELETE FROM iam.user_group_member WHERE group_id IN (?, ?)", GROUP_A, GROUP_B);
              jdbc.update("DELETE FROM iam.user_group WHERE id IN (?, ?)", GROUP_A, GROUP_B);
              jdbc.update("DELETE FROM opencop_user WHERE id = ?", ADMIN_ID);
              if (insertAdmin) {
                jdbc.update(
                    "INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username) VALUES (?, 'runtime-admin', 'runtime-admin', 'runtime-admin', 'runtime-admin')",
                    ADMIN_ID);
              }
            });
  }

  // The embedded database is shared JVM-wide; leftover history rows reference this class's user
  // and break later classes that blanket-delete opencop_user.
  @AfterEach
  void clearRuntimeFeatureFixtures() {
    resetRuntimeFeatures(false);
  }

  @Test
  void endpointsRequireAuthenticationAndGlobalAdminRole() throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcUser(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void historyRequiresAuthenticationAndGlobalAdminRole() throws Exception {
    mockMvc.perform(get("/v1/admin/runtime-features/history")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            get("/v1/admin/runtime-features/history")
                .with(authentication(TestAuthentications.oidcUser(ADMIN_ID))))
        .andExpect(status().isForbidden());
  }

  @Test
  void repeatedAudienceIsIdempotentWithAndWithoutRevisionGuard() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience.mode").value("ALL"))
        .andExpect(jsonPath("$.audience.userGroupIds").doesNotExist())
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isEqualTo(3);
  }

  @Test
  void patchIsImmediatelyVisibleAndCreatesOneActorHistoryRow() throws Exception {
    var admin = authentication(adminAuthWithEmail(" runtime.admin@example.test "));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("watchbox"))
        .andExpect(jsonPath("$.audience.mode").value("ALL"))
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            get("/v1/app-metadata").with(authentication(TestAuthentications.oidcUser(ADMIN_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(true));

    mockMvc
        .perform(get("/v1/admin/runtime-features/history").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].operation").value("PATCH"))
        .andExpect(jsonPath("$.content[0].actor.userId").value(ADMIN_ID.toString()))
        .andExpect(jsonPath("$.content[0].actor.fullName").value("Runtime Feature Administrator"))
        .andExpect(jsonPath("$.content[0].actor.email").value("runtime.admin@example.test"))
        .andExpect(jsonPath("$.content[0].actor.globalAdmin").value(true));

    assertThat(
            jdbc.queryForMap(
                "SELECT actor_full_name, actor_email, actor_global_admin"
                    + " FROM config.runtime_feature_history WHERE feature_name = 'watchbox'"))
        .containsEntry("actor_full_name", "Runtime Feature Administrator")
        .containsEntry("actor_email", "runtime.admin@example.test")
        .containsEntry("actor_global_admin", true);
  }

  @Test
  void history_whenActorFactsAreNullable_returnsDirectFactsWithoutInventingFallbacks()
      throws Exception {
    jdbc.update(
        """
        INSERT INTO config.runtime_feature_history (
          feature_name, consumer_class, operation,
          before_audience, after_audience, revision_before, revision_after,
          occurred_at, actor_user_id, actor_full_name, actor_email, actor_global_admin
        ) VALUES (
          'watchbox', 'BACKEND_ENFORCED', 'PATCH',
          '{"mode":"NONE"}'::jsonb, '{"mode":"ALL"}'::jsonb, 0, 1,
          '2026-08-19T11:00:00Z'::timestamptz, ?, NULL, 'direct@example.test', false
        )
        """,
        ADMIN_ID);

    mockMvc
        .perform(
            get("/v1/admin/runtime-features/history")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].actor.userId").value(ADMIN_ID.toString()))
        .andExpect(jsonPath("$.content[0].actor.email").value("direct@example.test"))
        .andExpect(jsonPath("$.content[0].actor.fullName").doesNotExist())
        .andExpect(jsonPath("$.content[0].actor.globalAdmin").value(false));
  }

  @Test
  void userGroupsPatchIsCanonicalIdempotentAndRecordedExactly() throws Exception {
    insertGroup(GROUP_A, "runtime-endpoint-a");
    insertGroup(GROUP_B, "runtime-endpoint-b");
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    var request =
        "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
            + GROUP_B
            + "\",\""
            + GROUP_A
            + "\"]},\"expectedRevision\":0}";

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience.length()").value(2))
        .andExpect(jsonPath("$.audience.mode").value("USER_GROUPS"))
        .andExpect(jsonPath("$.audience.userGroupIds.length()").value(2))
        .andExpect(jsonPath("$.audience.userGroupIds[0]").value(GROUP_A.toString()))
        .andExpect(jsonPath("$.audience.userGroupIds[1]").value(GROUP_B.toString()))
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                        + GROUP_A
                        + "\",\""
                        + GROUP_B
                        + "\"]},\"expectedRevision\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(get("/v1/admin/runtime-features/history").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].beforeAudience.mode").value("USER_GROUPS"))
        .andExpect(jsonPath("$.content[0].afterAudience.mode").value("USER_GROUPS"))
        .andExpect(jsonPath("$.content[0].revisionBefore").value(1))
        .andExpect(jsonPath("$.content[0].revisionAfter").value(1))
        .andExpect(jsonPath("$.content[1].beforeAudience.length()").value(1))
        .andExpect(jsonPath("$.content[1].beforeAudience.mode").value("NONE"))
        .andExpect(jsonPath("$.content[1].beforeAudience.userGroupIds").doesNotExist())
        .andExpect(jsonPath("$.content[1].afterAudience.length()").value(2))
        .andExpect(jsonPath("$.content[1].afterAudience.mode").value("USER_GROUPS"))
        .andExpect(jsonPath("$.content[1].afterAudience.userGroupIds[0]").value(GROUP_A.toString()))
        .andExpect(
            jsonPath("$.content[1].afterAudience.userGroupIds[1]").value(GROUP_B.toString()));

    assertThat(
            jdbc.queryForList(
                "SELECT user_group_id FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox' ORDER BY user_group_id",
                UUID.class))
        .containsExactly(GROUP_A, GROUP_B);
  }

  @Test
  void missingUserGroupTargetIsRejectedWithoutMutation() throws Exception {
    var missingGroupId = UUID.fromString("33333333-3333-4333-8333-333333333303");
    insertGroup(GROUP_A, "runtime-endpoint-existing");

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                        + GROUP_A
                        + "\",\""
                        + missingGroupId
                        + "\"]}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString(missingGroupId.toString()),
                        org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString(GROUP_A.toString())))));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'",
                Long.class))
        .isZero();
  }

  @Test
  void staleAndUnknownFieldRequestsDoNotMutateStateOrHistory() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevison\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_INVALID_REQUEST"));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isZero();
  }

  @Test
  void unknownAudienceModeIsRejectedWithoutMutation() throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"LAYERS\",\"layerIds\":[\""
                        + UUID.randomUUID()
                        + "\"]}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_INVALID_REQUEST"));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isZero();
  }

  @Test
  void unknownAndStaleGuardedRequestsReturnSpecificCodesWithoutExtraMutation() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/unknown")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_NOT_FOUND"));

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_REVISION_CONFLICT"));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isEqualTo(1L);
  }

  @ParameterizedTest
  @MethodSource("unreadableBodies")
  void unreadableBodiesFallBackToGlobalInvalidRequest(String body) throws Exception {
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("Invalid request body"));

    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isZero();
  }

  @Test
  void patchFailsFastWith409WhileTheFeatureRowIsLocked() throws Exception {
    try (var featureBlocker = dataSource.getConnection()) {
      featureBlocker.setAutoCommit(false);
      try (var statement =
          featureBlocker.prepareStatement(
              "SELECT name FROM config.runtime_feature WHERE name = 'watchbox' FOR UPDATE")) {
        statement.executeQuery().close();
      }

      mockMvc
          .perform(
              patch("/v1/admin/runtime-features/watchbox")
                  .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"audience\":{\"mode\":\"ALL\"}}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_BUSY"));

      assertThat(
              jdbc.queryForObject(
                  "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'",
                  Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM config.runtime_feature_history", Long.class))
          .isZero();

      featureBlocker.commit();
    }

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience.mode").value("ALL"));

    assertThat(
            jdbc.queryForObject(
                "SELECT audience_mode FROM config.runtime_feature WHERE name = 'watchbox'",
                String.class))
        .isEqualTo("ALL");
  }

  @Test
  void userGroupsPatchFailsFastWithoutMutationWhileATargetGroupIsLocked() throws Exception {
    insertGroup(GROUP_A, "runtime-endpoint-locked-target");
    try (var targetGroupBlocker = dataSource.getConnection()) {
      targetGroupBlocker.setAutoCommit(false);
      try (var statement =
          targetGroupBlocker.prepareStatement(
              "SELECT id FROM iam.user_group WHERE id = ? FOR UPDATE")) {
        statement.setObject(1, GROUP_A);
        statement.executeQuery().close();
      }

      try {
        var response =
            assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () ->
                    mockMvc.perform(
                        patch("/v1/admin/runtime-features/watchbox")
                            .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                                    + GROUP_A
                                    + "\"]},\"expectedRevision\":0}")));
        response
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_BUSY"))
            .andExpect(
                jsonPath("$.message").value(org.hamcrest.Matchers.containsString("target groups")));

        assertThat(
                jdbc.queryForObject(
                    "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'",
                    Long.class))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM config.runtime_feature_history", Long.class))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM config.runtime_feature_audience_user_group"
                        + " WHERE feature_name = 'watchbox'",
                    Long.class))
            .isZero();
      } finally {
        targetGroupBlocker.commit();
      }
    }

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                        + GROUP_A
                        + "\"]},\"expectedRevision\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience.mode").value("USER_GROUPS"))
        .andExpect(jsonPath("$.audience.userGroupIds[0]").value(GROUP_A.toString()))
        .andExpect(jsonPath("$.revision").value(1));
  }

  private static Stream<String> unreadableBodies() {
    return Stream.of(
        "[]",
        "{\"audience\":\"ALL\"}",
        "{\"audience\":{\"mode\":\"ALL\"},\"expectedRevision\":\"one\"}",
        "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\"not-a-uuid\"]}}",
        "{\"audience\":");
  }

  @Test
  void patchOpenApiSchemaMatchesServerContract() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs").with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeaturePatchRequest.required")
                .value(org.hamcrest.Matchers.contains("audience")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeaturePatchRequest.properties.unknownProperties")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeaturePatchRequest.additionalProperties")
                .value(false))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeaturePatchRequest.properties.expectedRevision.minimum")
                .value(0))
        .andExpect(
            jsonPath("$.components.schemas.Audience.required")
                .value(org.hamcrest.Matchers.contains("mode")))
        .andExpect(
            jsonPath("$.components.schemas.Audience.properties.mode.enum")
                .value(org.hamcrest.Matchers.contains("NONE", "ALL", "USER_GROUPS")))
        .andExpect(
            jsonPath("$.components.schemas.Audience.properties.userGroupIds.type").value("array"))
        .andExpect(
            jsonPath("$.components.schemas.Audience.oneOf[*]['$ref']")
                .value(
                    org.hamcrest.Matchers.contains(
                        "#/components/schemas/RuntimeFeatureNoneAudience",
                        "#/components/schemas/RuntimeFeatureAllAudience",
                        "#/components/schemas/RuntimeFeatureUserGroupsAudience")))
        .andExpect(
            jsonPath("$.components.schemas.Audience.discriminator.propertyName").value("mode"))
        .andExpect(
            jsonPath("$.components.schemas.Audience.discriminator.mapping.NONE")
                .value("#/components/schemas/RuntimeFeatureNoneAudience"))
        .andExpect(
            jsonPath("$.components.schemas.Audience.discriminator.mapping.ALL")
                .value("#/components/schemas/RuntimeFeatureAllAudience"))
        .andExpect(
            jsonPath("$.components.schemas.Audience.discriminator.mapping.USER_GROUPS")
                .value("#/components/schemas/RuntimeFeatureUserGroupsAudience"))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureNoneAudience.required")
                .value(org.hamcrest.Matchers.contains("mode")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureNoneAudience.properties.mode.enum")
                .value(org.hamcrest.Matchers.contains("NONE")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureNoneAudience.properties.userGroupIds")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureNoneAudience.additionalProperties")
                .value(false))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureAllAudience.required")
                .value(org.hamcrest.Matchers.contains("mode")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureAllAudience.properties.mode.enum")
                .value(org.hamcrest.Matchers.contains("ALL")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureAllAudience.properties.userGroupIds")
                .doesNotExist())
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureAllAudience.additionalProperties")
                .value(false))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureUserGroupsAudience.required")
                .value(org.hamcrest.Matchers.contains("mode", "userGroupIds")))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.mode.enum")
                .value(org.hamcrest.Matchers.contains("USER_GROUPS")))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.userGroupIds.minItems")
                .value(1))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.userGroupIds.maxItems")
                .value(100))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.userGroupIds.uniqueItems")
                .value(true))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.userGroupIds.items.type")
                .value("string"))
        .andExpect(
            jsonPath(
                    "$.components.schemas.RuntimeFeatureUserGroupsAudience.properties.userGroupIds.items.format")
                .value("uuid"))
        .andExpect(
            jsonPath("$.components.schemas.RuntimeFeatureUserGroupsAudience.additionalProperties")
                .value(false))
        .andExpect(
            jsonPath("$.components.schemas.Audience.properties.unknownProperties").doesNotExist())
        .andExpect(jsonPath("$.components.schemas.Audience.additionalProperties").value(false))
        .andExpect(
            jsonPath(
                    "$.paths['/v1/admin/runtime-features/{name}'].patch.responses['400'].description")
                .value(
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("RUNTIME_FEATURE_INVALID_REQUEST"),
                        org.hamcrest.Matchers.containsString(
                            "RUNTIME_FEATURE_INVALID_USER_GROUP_TARGETS"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("layer")))))
        .andExpect(
            jsonPath(
                    "$.paths['/v1/admin/runtime-features/{name}'].patch.responses['409'].description")
                .value(org.hamcrest.Matchers.containsString("target groups")));
  }

  @Test
  void historyOpenApi_whenGenerated_usesUniqueCompleteActorComponent() throws Exception {
    var result =
        mockMvc
            .perform(
                get("/v3/api-docs").with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID))))
            .andExpect(status().isOk())
            .andReturn();

    var schemas =
        objectMapper
            .readTree(result.getResponse().getContentAsByteArray())
            .path("components")
            .path("schemas");
    var actorSchema = schemas.path("RuntimeFeatureHistoryActorResponse").path("properties");
    assertThat(actorSchema.path("userId").path("format").asText()).isEqualTo("uuid");
    assertThat(actorSchema.path("fullName").path("type").asText()).isEqualTo("string");
    assertThat(actorSchema.path("email").path("type").asText()).isEqualTo("string");
    assertThat(actorSchema.path("globalAdmin").path("type").asText()).isEqualTo("boolean");
    assertThat(
            schemas
                .path("RuntimeFeatureHistoryResponse")
                .path("properties")
                .path("actor")
                .path("$ref")
                .asText())
        .isEqualTo("#/components/schemas/RuntimeFeatureHistoryActorResponse");
  }

  @Test
  void registrationRequiresAuthenticationAndGlobalAdminRole() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"themes-page\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(authentication(TestAuthentications.oidcUser(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"themes-page\"}"))
        .andExpect(status().isForbidden());

    assertThat(featureCount("themes-page")).isZero();
  }

  @Test
  void registrationCreatesAtRevisionZeroThenConvergesOnRepeat() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"themes-page\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("themes-page"))
        .andExpect(jsonPath("$.consumerClass").value("FRONTEND_ONLY"))
        .andExpect(jsonPath("$.audience.mode").value("NONE"))
        .andExpect(jsonPath("$.revision").value(0));

    // A second identical call is convergence, not an error — 200 rather than 201 is the only
    // difference a reconciling client sees.
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"themes-page\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.consumerClass").value("FRONTEND_ONLY"))
        .andExpect(jsonPath("$.audience.mode").value("NONE"))
        .andExpect(jsonPath("$.revision").value(0));

    assertThat(featureCount("themes-page")).isEqualTo(1);
    mockMvc
        .perform(get("/v1/admin/runtime-features/history").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].featureName").value("themes-page"))
        .andExpect(jsonPath("$.content[0].operation").value("CREATE"))
        .andExpect(jsonPath("$.content[0].beforeAudience").doesNotExist())
        .andExpect(jsonPath("$.content[0].afterAudience.mode").value("NONE"))
        .andExpect(jsonPath("$.content[0].revisionBefore").doesNotExist())
        .andExpect(jsonPath("$.content[0].revisionAfter").value(0));
  }

  @Test
  void registrationRetryLeavesAnEnabledDefinitionUntouched() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"plugin-loader\"}"))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/plugin-loader")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"plugin-loader\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience.mode").value("ALL"))
        .andExpect(jsonPath("$.revision").value(1));
  }

  @ParameterizedTest
  @MethodSource("rejectedRegistrationBodies")
  void registrationRejectsMalformedAndReservedNames(String body, String code) throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(code));
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> rejectedRegistrationBodies() {
    return Stream.of(
        // camelCase is the pre-migration value and must not slip through.
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"themesPage\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of("{}", "RUNTIME_FEATURE_INVALID_NAME"),
        // Dot segments are collapsed by URI normalization, so a row under one of these names could
        // never be addressed by the Location it was advertised at, nor patched afterwards.
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\".\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"..\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"-themes\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"themes-\"}", "RUNTIME_FEATURE_INVALID_NAME"),
        org.junit.jupiter.params.provider.Arguments.of(
            "{\"name\":\"watchbox\"}", "RUNTIME_FEATURE_RESERVED_NAME"));
  }

  /**
   * The other side of the dot-segment bound: a name carrying every allowed separator is accepted,
   * and the {@code Location} it advertises actually reaches the row. Registering without patching
   * would prove only that the header was produced, not that it addresses anything.
   */
  @Test
  void registrationAcceptsASeparatorBearingNameAndItsLocationAddressesTheDefinition()
      throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    var name = "flag.v2_beta-1";

    var location =
        mockMvc
            .perform(
                post("/v1/admin/runtime-features")
                    .with(admin)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/v1/admin/runtime-features/" + name))
            .andReturn()
            .getResponse()
            .getHeader("Location");

    mockMvc
        .perform(
            patch(location)
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(name))
        .andExpect(jsonPath("$.revision").value(1));
  }

  // An absent body is a distinct branch from "{}": the request object itself is null, so the name
  // policy is never reached and the endpoint's own guard has to answer.
  @Test
  void registrationRejectsAnAbsentBody() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_INVALID_REQUEST"));
  }

  @Test
  void registrationAdvertisesTheCreatedDefinitionsLocation() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"themes-page\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/admin/runtime-features/themes-page"));
  }

  @Test
  void serverOwnedFieldsAreIgnoredRatherThanRejected() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    // The invariant worth executing is not "extras are rejected" — it is that they cannot reach
    // the stored state. The endpoint passes only the name across the business boundary, so an
    // attempt to supply consumer class, audience, or revision changes nothing, and an explicit
    // null (which the previous capture implementation turned into a 500) is equally inert.
    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"themes-page\",\"consumerClass\":\"BACKEND_ENFORCED\","
                        + "\"audience\":{\"mode\":\"ALL\"},\"revision\":9,\"extra\":null}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("themes-page"))
        .andExpect(jsonPath("$.consumerClass").value("FRONTEND_ONLY"))
        .andExpect(jsonPath("$.audience.mode").value("NONE"))
        .andExpect(jsonPath("$.revision").value(0));

    var stored =
        jdbc.queryForMap(
            "SELECT consumer_class, audience_mode, revision FROM config.runtime_feature"
                + " WHERE name = 'themes-page'");
    assertThat(stored.get("consumer_class")).isEqualTo("FRONTEND_ONLY");
    assertThat(stored.get("audience_mode")).isEqualTo("NONE");
    assertThat(stored.get("revision")).isEqualTo(0L);
  }

  @Test
  void reconcileRegistersEveryMissingNameAndConvergesOnRepeat() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\",\"plugin-loader\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(true))
        .andExpect(jsonPath("$.created.length()").value(2))
        .andExpect(jsonPath("$.created[0].name").value("themes-page"))
        .andExpect(jsonPath("$.created[0].consumerClass").value("FRONTEND_ONLY"))
        .andExpect(jsonPath("$.created[0].audience.mode").value("NONE"))
        .andExpect(jsonPath("$.created[0].revision").value(0))
        .andExpect(jsonPath("$.alreadyRegistered").isEmpty())
        .andExpect(jsonPath("$.rejected").isEmpty());

    // Re-running is convergence: nothing is created, nothing is an error.
    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\",\"plugin-loader\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(true))
        .andExpect(jsonPath("$.created").isEmpty())
        .andExpect(jsonPath("$.alreadyRegistered.length()").value(2))
        .andExpect(jsonPath("$.rejected").isEmpty());

    assertThat(featureCount("themes-page")).isEqualTo(1);
    assertThat(featureCount("plugin-loader")).isEqualTo(1);
    mockMvc
        .perform(get("/v1/admin/runtime-features/history").with(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].operation").value("CREATE"))
        .andExpect(jsonPath("$.content[1].operation").value("CREATE"));
  }

  @Test
  void reconcileReportsEachNameIndependentlyInsteadOfFailingAsAUnit() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    // The load-bearing property. One malformed and one reserved name must not stop the good one
    // from registering, and the whole call still answers 200 — the body is the report. This is
    // what an outer @Transactional would break: the first rejection would mark the transaction
    // rollback-only and discard the name that had already been created.
    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\",\"BAD NAME\",\"watchbox\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(false))
        .andExpect(jsonPath("$.created.length()").value(1))
        .andExpect(jsonPath("$.created[0].name").value("themes-page"))
        .andExpect(jsonPath("$.rejected.length()").value(2))
        .andExpect(jsonPath("$.rejected[0].name").value("BAD NAME"))
        .andExpect(jsonPath("$.rejected[0].code").value("RUNTIME_FEATURE_INVALID_NAME"))
        .andExpect(jsonPath("$.rejected[1].name").value("watchbox"))
        .andExpect(jsonPath("$.rejected[1].code").value("RUNTIME_FEATURE_RESERVED_NAME"));

    // The good name is durable, and the reserved identity was never touched.
    assertThat(featureCount("themes-page")).isEqualTo(1);
    assertThat(featureCount("BAD NAME")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT consumer_class FROM config.runtime_feature WHERE name = 'watchbox'",
                String.class))
        .isEqualTo("BACKEND_ENFORCED");
  }

  @Test
  void reconcileLeavesAnEnabledDefinitionUntouched() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\"]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/themes-page")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"audience\":{\"mode\":\"ALL\"}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(1));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").isEmpty())
        .andExpect(jsonPath("$.alreadyRegistered.length()").value(1))
        .andExpect(jsonPath("$.alreadyRegistered[0].audience.mode").value("ALL"))
        .andExpect(jsonPath("$.alreadyRegistered[0].revision").value(1));
  }

  @Test
  void reconcileCollapsesDuplicateNames() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\",\"themes-page\",\"themes-page\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(true))
        // Collapsed, so the name is attempted once and reported once — not created then twice
        // "already registered", which would read as a race that did not happen.
        .andExpect(jsonPath("$.created.length()").value(1))
        .andExpect(jsonPath("$.alreadyRegistered").isEmpty());

    assertThat(featureCount("themes-page")).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("rejectedReconcileBodies")
  void reconcileRejectsMalformedEnvelopes(String body) throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_INVALID_REQUEST"));
  }

  private static Stream<org.junit.jupiter.params.provider.Arguments> rejectedReconcileBodies() {
    // An individually bad name belongs in `rejected`; a bad *envelope* is a 400, because there is
    // nothing to report per name. A null entry is the latter: a rejection is keyed by name.
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of("{\"names\":[]}"),
        org.junit.jupiter.params.provider.Arguments.of("{}"),
        org.junit.jupiter.params.provider.Arguments.of("{\"names\":[\"themes-page\",null]}"),
        org.junit.jupiter.params.provider.Arguments.of(namesEnvelope(MAX_RECONCILE_NAMES + 1)));
  }

  // Pins the accepted side of the cap. Without it, flipping the endpoint's `>` to `>=` would leave
  // the over-cap case passing while every legitimate full-size manifest started failing.
  @Test
  void reconcileAcceptsExactlyTheNameCap() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(namesEnvelope(MAX_RECONCILE_NAMES)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(true))
        .andExpect(jsonPath("$.created.length()").value(MAX_RECONCILE_NAMES));
  }

  // The only rejection raised *after* a name's store transaction has already committed, so this is
  // the case that actually proves reconcile reports independently rather than failing as a unit —
  // the reserved-name rejections above are caught before the catalog is ever touched.
  @Test
  void reconcileReportsAnExistingBackendEnforcedNameAsAClassConflict() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    // A backend-owned name absent from this build's BackendRuntimeFeature enum, so the
    // reserved-name guard cannot catch it and the stored class is the only signal. Seeded with
    // every piece of state registration must not disturb — a targeted audience, a non-zero
    // revision, and a history row — so the snapshot below has something to protect.
    // The audience/targets consistency triggers are DEFERRABLE INITIALLY DEFERRED, so the row and
    // its targets have to land in one transaction — separately they are each momentarily
    // inconsistent and the check fires at every autocommit.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update(
                  "INSERT INTO config.runtime_feature (name, consumer_class, audience_mode,"
                      + " revision, created_at, updated_at)"
                      + " VALUES ('legacy-backend-flag', 'BACKEND_ENFORCED', 'USER_GROUPS', 7,"
                      + " now(), now())");
              jdbc.update(
                  "INSERT INTO config.runtime_feature_audience_user_group (feature_name,"
                      + " user_group_id) VALUES ('legacy-backend-flag', ?),"
                      + " ('legacy-backend-flag', ?)",
                  GROUP_A,
                  GROUP_B);
              jdbc.update(
                  "INSERT INTO config.runtime_feature_history (feature_name, consumer_class,"
                      + " operation, before_audience, after_audience, revision_before,"
                      + " revision_after, occurred_at, actor_user_id, actor_email,"
                      + " actor_global_admin) VALUES ('legacy-backend-flag', 'BACKEND_ENFORCED',"
                      + " 'PATCH', '{\"mode\":\"NONE\"}'::jsonb,"
                      + " '{\"mode\":\"ALL\"}'::jsonb, 6, 7, now(), ?,"
                      + " 'seed@example.com', true)",
                  ADMIN_ID);
            });

    var rowBefore = backendRowSnapshot();
    var targetsBefore = backendTargetsSnapshot();
    var historyBefore = backendHistorySnapshot();

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\",\"legacy-backend-flag\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(false))
        .andExpect(jsonPath("$.created.length()").value(1))
        .andExpect(jsonPath("$.created[0].name").value("themes-page"))
        .andExpect(jsonPath("$.rejected.length()").value(1))
        .andExpect(jsonPath("$.rejected[0].name").value("legacy-backend-flag"))
        .andExpect(jsonPath("$.rejected[0].code").value("RUNTIME_FEATURE_CLASS_CONFLICT"));

    // The good name survived a post-commit rejection.
    assertThat(featureCount("themes-page")).isEqualTo(1);

    // The backend-owned row is byte-identical: not just its class, but its audience, targets,
    // revision, timestamps, and history. Asserting only the class would pass even if reconciliation
    // had rewritten everything else about the row on its way to refusing it.
    assertThat(backendRowSnapshot()).isEqualTo(rowBefore);
    assertThat(backendTargetsSnapshot()).isEqualTo(targetsBefore);
    assertThat(backendHistorySnapshot()).isEqualTo(historyBefore);
  }

  private Map<String, Object> backendRowSnapshot() {
    return jdbc.queryForMap(
        "SELECT name, consumer_class, audience_mode, revision, created_at, updated_at"
            + " FROM config.runtime_feature WHERE name = 'legacy-backend-flag'");
  }

  private List<Map<String, Object>> backendTargetsSnapshot() {
    return jdbc.queryForList(
        "SELECT user_group_id FROM config.runtime_feature_audience_user_group"
            + " WHERE feature_name = 'legacy-backend-flag' ORDER BY user_group_id");
  }

  private List<Map<String, Object>> backendHistorySnapshot() {
    return jdbc.queryForList(
        "SELECT consumer_class, operation, before_audience::text, after_audience::text,"
            + " revision_before, revision_after, occurred_at, actor_user_id, actor_full_name,"
            + " actor_email, actor_global_admin FROM config.runtime_feature_history"
            + " WHERE feature_name = 'legacy-backend-flag' ORDER BY id");
  }

  private static String namesEnvelope(int count) {
    return "{\"names\":["
        + Stream.iterate(0, i -> i + 1)
            .limit(count)
            .map(i -> "\"flag-" + i + "\"")
            .reduce((a, b) -> a + "," + b)
            .orElseThrow()
        + "]}";
  }

  @Test
  void reconcileRequiresAuthenticationAndGlobalAdminRole() throws Exception {
    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\"]}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/v1/admin/runtime-features/reconcile")
                .with(authentication(TestAuthentications.oidcUser(ADMIN_ID)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"names\":[\"themes-page\"]}"))
        .andExpect(status().isForbidden());

    assertThat(featureCount("themes-page")).isZero();
  }

  @Test
  void registeringWatchboxLeavesTheBackendEnforcedRowAndItsHistoryAlone() throws Exception {
    var admin = authentication(TestAuthentications.oidcAdmin(ADMIN_ID));
    var before =
        jdbc.queryForMap(
            "SELECT consumer_class, audience_mode, revision, updated_at FROM config.runtime_feature WHERE name = 'watchbox'");

    mockMvc
        .perform(
            post("/v1/admin/runtime-features")
                .with(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"watchbox\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RUNTIME_FEATURE_RESERVED_NAME"));

    assertThat(
            jdbc.queryForMap(
                "SELECT consumer_class, audience_mode, revision, updated_at FROM config.runtime_feature WHERE name = 'watchbox'"))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM config.runtime_feature_history WHERE feature_name = 'watchbox'",
                Long.class))
        .isZero();
  }

  private long featureCount(String name) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM config.runtime_feature WHERE name = ?", Long.class, name);
  }

  private void insertGroup(UUID id, String name) {
    jdbc.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, ?, 'runtime feature endpoint fixture')",
        id,
        name);
  }

  private static UsernamePasswordAuthenticationToken adminAuthWithEmail(String email) {
    var principal =
        new OpenCopPrincipal(
                ADMIN_ID,
                "https://issuer.test",
                "runtime-admin",
                "runtime-admin",
                new OpenCopPrincipal.Profile(
                    "preferred-runtime-admin",
                    "Runtime Feature Administrator",
                    null,
                    null,
                    email,
                    true),
                0,
                Instant.EPOCH)
            .withRequestCurrentGlobalAdministrator(true);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority(Roles.ADMIN)));
  }
}
