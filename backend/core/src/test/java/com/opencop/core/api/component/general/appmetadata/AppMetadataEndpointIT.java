// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.appmetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.component.security.TestPrincipals;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ComponentWebIT(classes = AppMetadataWebTestApplication.class)
class AppMetadataEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443111");
  private static final UUID MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443112");
  private static final UUID NON_MEMBER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443113");
  private static final UUID GROUP_ID = UUID.fromString("44444444-4444-4444-8444-444444444404");
  private static final UUID SECOND_GROUP_ID =
      UUID.fromString("44444444-4444-4444-8444-444444444405");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void resetRuntimeFeatures() {
    resetRuntimeFeatureFixtures();
  }

  @AfterEach
  void clearRuntimeFeatureFixtures() {
    resetRuntimeFeatureFixtures();
  }

  private void resetRuntimeFeatureFixtures() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            _ -> {
              jdbc.update("DELETE FROM config.runtime_feature_history");
              jdbc.update(
                  "DELETE FROM config.runtime_feature_audience_user_group WHERE feature_name = 'watchbox'");
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'NONE', revision = 0, updated_at = created_at WHERE name = 'watchbox'");
              jdbc.update(
                  "UPDATE config.runtime_feature SET audience_mode = 'ALL', revision = 0, updated_at = created_at WHERE name = 'rtsp'");
              jdbc.update(
                  "DELETE FROM iam.user_group_member WHERE group_id IN (?, ?)",
                  GROUP_ID,
                  SECOND_GROUP_ID);
              jdbc.update(
                  "DELETE FROM iam.user_group WHERE id IN (?, ?)", GROUP_ID, SECOND_GROUP_ID);
              jdbc.update(
                  "DELETE FROM opencop_user WHERE id IN (?, ?, ?)",
                  ADMIN_ID,
                  MEMBER_ID,
                  NON_MEMBER_ID);
            });
  }

  @Test
  void getAppMetadata_whenUnauthenticated_returnsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/app-metadata")).andExpect(status().isUnauthorized());
  }

  @Test
  void getAppMetadata_whenAuthenticatedNonAdmin_returnsDatabaseDefaults() throws Exception {
    mockMvc
        .perform(get("/v1/app-metadata").with(authentication(TestAuthentications.oidcUser())))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.features.length()").value(2))
        .andExpect(jsonPath("$.features[0].name").value("rtsp"))
        .andExpect(jsonPath("$.features[0].enabled").value(true))
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(false))
        .andExpect(jsonPath("$.admin").doesNotExist());
  }

  @Test
  void userGroupAudienceTracksMembershipImmediatelyAndSurvivesRenameAndDelete() throws Exception {
    insertUser(ADMIN_ID, "metadata-admin");
    insertUser(MEMBER_ID, "metadata-member");
    insertUser(NON_MEMBER_ID, "metadata-non-member");
    jdbc.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, 'metadata-runtime-group', 'runtime feature metadata fixture')",
        GROUP_ID);
    addMember();

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(globalAdminAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                        + GROUP_ID
                        + "\"]},\"expectedRevision\":0}"))
        .andExpect(status().isOk());

    assertWatchboxAvailability(MEMBER_ID, true);
    assertWatchboxAvailability(NON_MEMBER_ID, false);
    assertAdminRawAudience();

    jdbc.update(
        "DELETE FROM iam.user_group_member WHERE group_id = ? AND user_id = ?",
        GROUP_ID,
        MEMBER_ID);
    assertWatchboxAvailability(MEMBER_ID, false);

    addMember();
    jdbc.update(
        "UPDATE iam.user_group SET name = 'metadata-runtime-group-renamed' WHERE id = ?", GROUP_ID);
    assertWatchboxAvailability(MEMBER_ID, true);

    var revisionBeforeDelete =
        jdbc.queryForObject(
            "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class);
    var historyBeforeDelete =
        jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class);
    jdbc.update("DELETE FROM iam.user_group WHERE id = ?", GROUP_ID);

    assertWatchboxAvailability(MEMBER_ID, false);
    assertAdminRawAudience();
    assertThat(
            jdbc.queryForObject(
                "SELECT revision FROM config.runtime_feature WHERE name = 'watchbox'", Long.class))
        .isEqualTo(revisionBeforeDelete);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM config.runtime_feature_history", Long.class))
        .isEqualTo(historyBeforeDelete);
  }

  @Test
  void userGroupAudienceEnablesMembershipInAnyTargetGroup() throws Exception {
    insertUser(ADMIN_ID, "metadata-admin");
    insertUser(MEMBER_ID, "metadata-member");
    jdbc.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, 'metadata-runtime-group-a', 'runtime feature metadata fixture')",
        GROUP_ID);
    jdbc.update(
        "INSERT INTO iam.user_group (id, name, description) VALUES (?, 'metadata-runtime-group-b', 'runtime feature metadata fixture')",
        SECOND_GROUP_ID);
    jdbc.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)",
        SECOND_GROUP_ID,
        MEMBER_ID);

    mockMvc
        .perform(
            patch("/v1/admin/runtime-features/watchbox")
                .with(authentication(globalAdminAuth()))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"audience\":{\"mode\":\"USER_GROUPS\",\"userGroupIds\":[\""
                        + GROUP_ID
                        + "\",\""
                        + SECOND_GROUP_ID
                        + "\"]},\"expectedRevision\":0}"))
        .andExpect(status().isOk());

    assertWatchboxAvailability(MEMBER_ID, true);
  }

  private void assertWatchboxAvailability(UUID userId, boolean enabled) throws Exception {
    mockMvc
        .perform(get("/v1/app-metadata").with(authentication(TestAuthentications.oidcUser(userId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.features[1].name").value("watchbox"))
        .andExpect(jsonPath("$.features[1].enabled").value(enabled));
  }

  private void assertAdminRawAudience() throws Exception {
    mockMvc
        .perform(get("/v1/app-metadata").with(authentication(globalAdminAuth())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.admin.runtimeFeatures[1].name").value("watchbox"))
        .andExpect(jsonPath("$.admin.runtimeFeatures[1].audience.mode").value("USER_GROUPS"))
        .andExpect(
            jsonPath("$.admin.runtimeFeatures[1].audience.userGroupIds[0]")
                .value(GROUP_ID.toString()));
  }

  private void insertUser(UUID id, String username) {
    jdbc.update(
        "INSERT INTO opencop_user (id, idp_user_id, idp_username, idp_issuer, username) VALUES (?, ?, ?, 'runtime-metadata', ?)",
        id,
        id.toString(),
        username,
        username);
  }

  private void addMember() {
    jdbc.update(
        "INSERT INTO iam.user_group_member (group_id, user_id) VALUES (?, ?)", GROUP_ID, MEMBER_ID);
  }

  private static Authentication globalAdminAuth() {
    var principal =
        TestPrincipals.oidcPrincipal(ADMIN_ID).withRequestCurrentGlobalAdministrator(true);
    return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
  }
}
