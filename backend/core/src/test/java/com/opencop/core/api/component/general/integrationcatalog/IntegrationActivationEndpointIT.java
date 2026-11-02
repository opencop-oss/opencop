// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@ComponentWebIT(classes = IntegrationActivationWebTestApplication.class)
class IntegrationActivationEndpointIT extends AbstractWebIT {

  private static final UUID ADMIN_ID = UUID.fromString("a9d417ee-ce6d-46c2-b5ee-c0b767ecb3de");
  private static final UUID INTEGRATION_ID =
      UUID.fromString("3db58f5a-485e-4667-97c8-a771a59042be");
  private static final UUID OTHER_INTEGRATION_ID =
      UUID.fromString("66733520-3308-4e96-b3ea-80eb43e7e003");
  private static final UUID MAPPER_ID = UUID.fromString("3b345a79-d49d-4ad5-85bf-250492518a43");
  private static final OffsetDateTime FIXED_UPDATED_AT =
      OffsetDateTime.parse("2026-08-19T10:11:12Z");

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetCatalog() {
    cleanFixtures();
    seedMapper();
    seedUser();
  }

  @AfterEach
  void cleanCatalog() {
    cleanFixtures();
  }

  private void cleanFixtures() {
    jdbc.update("DELETE FROM profiles.data_source");
    jdbc.update("DELETE FROM profiles.data_mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM profiles.mapper WHERE id = ?", MAPPER_ID);
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update(
        """
        DELETE FROM config.integration i
         WHERE NOT EXISTS (SELECT 1 FROM profiles.mapper m WHERE m.integration_id = i.id)
        """);
    jdbc.update("DELETE FROM opencop_user WHERE id = ?", ADMIN_ID);
  }

  @Test
  void administratorDeactivatesAnIntegrationWithSeveralSchemaVersions() throws Exception {
    seedSchema(1, true);
    seedSchema(2, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    setActive(INTEGRATION_ID, false).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(false);
    assertThat(updatedAtValues()).allMatch(updatedAt -> updatedAt.isAfter(FIXED_UPDATED_AT));
  }

  /** Native sets up every connection no plugin owns, so an administrator cannot switch it off. */
  @Test
  void administratorCannotDeactivateTheNativeIntegration() throws Exception {
    setActive(NativeIntegration.NATIVE_ID, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_DEACTIVATABLE"));
  }

  @Test
  void administratorReactivatesAnIntegrationWithSeveralSchemaVersions() throws Exception {
    seedSchema(1, false);
    seedSchema(2, false);

    setActive(INTEGRATION_ID, true).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(true);
  }

  @Test
  void reactivationDoesNotApplyConnectionUsageGuard() throws Exception {
    seedSchema(1, false);
    seedConnection(
        UUID.fromString("4f8a6ef0-51bc-4f76-8b84-07db7915554a"),
        "Referenced while inactive",
        1,
        true,
        null,
        false,
        null);

    setActive(INTEGRATION_ID, true).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(true);
  }

  @Test
  void unknownIntegrationReturnsStableNotFoundWithoutMutatingCatalog() throws Exception {
    seedSchema(1, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    var result =
        setActive(OTHER_INTEGRATION_ID, false).andExpect(status().isNotFound()).andReturn();

    assertThat(readResponse(result, GlobalErrorResponse.class).code())
        .isEqualTo("INTEGRATION_NOT_FOUND");
    assertThat(activeStates()).containsExactly(true);
    assertThat(updatedAtValues()).containsExactly(FIXED_UPDATED_AT);
  }

  @Test
  void repeatedActiveStateIsNoOpAndPreservesTheTimestamp() throws Exception {
    seedSchema(1, true);
    seedSchema(2, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    setActive(INTEGRATION_ID, true).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(true);
    assertThat(updatedAtValues()).containsExactly(FIXED_UPDATED_AT);
  }

  @Test
  void repeatedInactiveStateIsNoOpAndPreservesTheTimestamp() throws Exception {
    seedSchema(1, false);
    seedSchema(2, false);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    setActive(INTEGRATION_ID, false).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(false);
    assertThat(updatedAtValues()).containsExactly(FIXED_UPDATED_AT);
  }

  @Test
  void enabledAssignedRunningConnectionBlocksDeactivation() throws Exception {
    var sourceId = UUID.fromString("6d7c8674-e03e-482a-938b-acdbf4a901af");
    seedSchema(1, true);
    seedConnection(sourceId, "Running", 1, true, "connect-1", false, "CONNECTED");

    setActive(INTEGRATION_ID, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_IN_USE"))
        .andExpect(
            jsonPath("$.message").value("Integration is referenced by non-archived connections"))
        .andExpect(jsonPath("$.connections[0].id").value(sourceId.toString()))
        .andExpect(jsonPath("$.connections[0].name").value("Running"));

    assertThat(activeStates()).containsExactly(true);
  }

  @Test
  void disabledUnassignedStoppedConnectionBlocksDeactivation() throws Exception {
    var sourceId = UUID.fromString("0d145034-2ccc-4166-8767-3aef6938ff0a");
    seedSchema(1, true);
    seedConnection(sourceId, "Stopped", 1, false, null, false, "STOPPED");

    setActive(INTEGRATION_ID, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.connections[0].id").value(sourceId.toString()));

    assertThat(activeStates()).containsExactly(true);
  }

  @Test
  void blockedDeactivationReturnsEverySchemaBlockerSortedByNameThenId() throws Exception {
    var alpha = UUID.fromString("10000000-0000-0000-0000-000000000001");
    var beta = UUID.fromString("20000000-0000-0000-0000-000000000002");
    var zulu = UUID.fromString("30000000-0000-0000-0000-000000000003");
    seedSchema(1, true);
    seedSchema(2, true);
    seedConnection(zulu, "Zulu", 1, true, null, false, null);
    seedConnection(beta, "Beta", 2, true, null, false, null);
    seedConnection(alpha, "Alpha", 1, true, null, false, null);

    setActive(INTEGRATION_ID, false)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.connections.length()").value(3))
        .andExpect(jsonPath("$.connections[0].id").value(alpha.toString()))
        .andExpect(jsonPath("$.connections[0].name").value("Alpha"))
        .andExpect(jsonPath("$.connections[1].id").value(beta.toString()))
        .andExpect(jsonPath("$.connections[1].name").value("Beta"))
        .andExpect(jsonPath("$.connections[2].id").value(zulu.toString()))
        .andExpect(jsonPath("$.connections[2].name").value("Zulu"));

    assertThat(activeStates()).containsExactly(true);
  }

  @Test
  void archivedReferencesDoNotBlockDeactivation() throws Exception {
    seedSchema(1, true);
    seedConnection(
        UUID.fromString("3f743ec7-73d3-4e25-a99f-d97f57e95f3d"),
        "Archived",
        1,
        true,
        "connect-1",
        true,
        "CONNECTED");

    setActive(INTEGRATION_ID, false).andExpect(status().isNoContent());

    assertThat(activeStates()).containsExactly(false);
  }

  @Test
  void authenticatedNonAdministratorIsForbiddenWithoutMutation() throws Exception {
    seedSchema(1, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    mockMvc
        .perform(
            patch("/v1/admin/integrations/{integrationId}", INTEGRATION_ID)
                .with(csrf())
                .with(authentication(TestAuthentications.oidcUser()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isForbidden());

    assertCatalogUnchanged(true);
  }

  @Test
  void unauthenticatedCallerIsUnauthorizedWithoutMutation() throws Exception {
    seedSchema(1, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    mockMvc
        .perform(
            patch("/v1/admin/integrations/{integrationId}", INTEGRATION_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isUnauthorized());

    assertCatalogUnchanged(true);
  }

  @Test
  void malformedJsonUsesExistingInvalidRequestResponseWithoutMutation() throws Exception {
    seedSchema(1, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    var result =
        adminPatch(INTEGRATION_ID, "{\"active\":").andExpect(status().isBadRequest()).andReturn();

    assertThat(readResponse(result, GlobalErrorResponse.class))
        .isEqualTo(new GlobalErrorResponse("INVALID_REQUEST", "Invalid request body"));
    assertCatalogUnchanged(true);
  }

  @Test
  void nonBooleanActiveUsesExistingInvalidRequestResponseWithoutMutation() throws Exception {
    seedSchema(1, true);
    setCatalogTimestamp(FIXED_UPDATED_AT);

    var result =
        adminPatch(INTEGRATION_ID, "{\"active\":\"inactive\"}")
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(readResponse(result, GlobalErrorResponse.class))
        .isEqualTo(new GlobalErrorResponse("INVALID_REQUEST", "Invalid request body"));
    assertCatalogUnchanged(true);
  }

  private void seedSchema(int schemaVersion, boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description, active)
        VALUES (?, 'Integration', 'Activation test integration', ?)
        ON CONFLICT (id) DO NOTHING
        """,
        INTEGRATION_ID,
        active);
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (?, ?)
        """,
        INTEGRATION_ID,
        schemaVersion);
  }

  private org.springframework.test.web.servlet.ResultActions setActive(
      UUID integrationId, boolean active) throws Exception {
    return adminPatch(integrationId, "{\"active\":" + active + "}");
  }

  private org.springframework.test.web.servlet.ResultActions adminPatch(
      UUID integrationId, String body) throws Exception {
    return mockMvc.perform(
        patch("/v1/admin/integrations/{integrationId}", integrationId)
            .with(csrf())
            .with(authentication(TestAuthentications.oidcAdmin(ADMIN_ID)))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private List<Boolean> activeStates() {
    return jdbc.queryForList(
        """
        SELECT active
        FROM config.integration
        WHERE id = ?
        """,
        Boolean.class,
        INTEGRATION_ID);
  }

  private List<OffsetDateTime> updatedAtValues() {
    return jdbc.queryForList(
        """
        SELECT updated_at
        FROM config.integration
        WHERE id = ?
        """,
        OffsetDateTime.class,
        INTEGRATION_ID);
  }

  private void setCatalogTimestamp(OffsetDateTime updatedAt) {
    jdbc.update(
        "UPDATE config.integration SET updated_at = ? WHERE id = ?", updatedAt, INTEGRATION_ID);
  }

  private void assertCatalogUnchanged(boolean active) {
    assertThat(activeStates()).containsExactly(active);
    assertThat(updatedAtValues()).containsExactly(FIXED_UPDATED_AT);
  }

  private void seedMapper() {
    jdbc.update(
        """
        INSERT INTO profiles.mapper (id, qualifier)
        VALUES (?, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        MAPPER_ID);
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (?, 1, ?, '{}'::jsonb)
        """,
        MAPPER_ID,
        "activation-test-mapper");
  }

  private void seedUser() {
    jdbc.update(
        """
        INSERT INTO opencop_user(
          id, idp_user_id, idp_username, idp_issuer,
          username, email, first_name, last_name, display_name
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        ADMIN_ID,
        "idp-" + ADMIN_ID,
        "activation-admin",
        "https://issuer.test",
        "activation-admin",
        "activation-admin@example.test",
        "Activation",
        "Admin",
        "Activation Admin");
  }

  private void seedConnection(
      UUID sourceId,
      String name,
      int schemaVersion,
      boolean enabled,
      String assignedInstance,
      boolean archived,
      String runtimeState) {
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, ingress_mapper_id, connection_spec,
          enabled, access, assigned_instance, archived_at, archived_by,
          integration_id, connection_schema_version
        ) VALUES (?, ?, 'default', 'KAFKA', ?, '{}'::jsonb, ?, 'R', ?, ?, ?, ?, ?)
        """,
        sourceId,
        name,
        MAPPER_ID,
        enabled,
        assignedInstance,
        archived ? OffsetDateTime.parse("2026-08-20T11:12:13Z") : null,
        archived ? ADMIN_ID : null,
        INTEGRATION_ID,
        schemaVersion);
    if (runtimeState != null) {
      jdbc.update(
          """
          INSERT INTO profiles.source_runtime_observation(
            source_id, observed_instance_id, state, observed_at, observation_expires_at
          ) VALUES (?, ?, ?, now(), now() + interval '2 minutes')
          """,
          sourceId,
          assignedInstance == null ? "unassigned-observer" : assignedInstance,
          runtimeState);
    }
  }
}
