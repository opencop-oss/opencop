// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.NON_TCP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.NON_TCP_SCHEMA_VERSION;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.TCP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.TCP_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSettingsResponse;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsFieldKind;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = IntegrationConnectionSetupWebTestApplication.class)
class IntegrationConnectionSettingsEndpointIT extends AbstractWebIT {

  private static final String PATH =
      "/v1/integrations/{integrationId}/versions/{schemaVersion}/connection/settings";
  private static final UUID UNBACKED_ID = UUID.fromString("7f0a1b2c-3d4e-4f50-8a9b-0c1d2e3f4a55");
  private static final UUID ABSENT_ID = UUID.fromString("0e1d2c3b-4a59-4687-9a0b-1c2d3e4f5a66");

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  @AfterEach
  void resetCatalog() {
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update("DELETE FROM config.integration");
  }

  @Test
  void describesTheDeclaredSettingsWithIsoDurations() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.integrationId").value(TCP_ID.toString()))
        .andExpect(jsonPath("$.schemaVersion").value(TCP_SCHEMA_VERSION))
        .andExpect(jsonPath("$.sections.length()").value(1))
        .andExpect(jsonPath("$.sections[0].name").value("feed"))
        .andExpect(jsonPath("$.sections[0].label").value("Feed"))
        .andExpect(jsonPath("$.sections[0].fields[0].name").value("interval"))
        .andExpect(jsonPath("$.sections[0].fields[0].kind").value("DURATION"))
        .andExpect(jsonPath("$.sections[0].fields[0].min").value("PT5S"))
        .andExpect(jsonPath("$.sections[0].fields[0].max").value("PT10M"))
        .andExpect(jsonPath("$.sections[0].fields[0].default").value("PT30S"))
        .andExpect(jsonPath("$.sections[0].fields[0].maxLength").doesNotExist())
        .andExpect(jsonPath("$.sections[0].fields[0].options").doesNotExist());
  }

  @Test
  void describesASchemaWithoutSettingsAsEmpty() throws Exception {
    seedSchema(NON_TCP_ID, NON_TCP_SCHEMA_VERSION, true);

    describe(NON_TCP_ID, NON_TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sections").isArray())
        .andExpect(jsonPath("$.sections.length()").value(0));
  }

  @Test
  void answersUnknownInactiveAndUnavailableAsConnectionSetupDoes() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, false);
    seedSchema(UNBACKED_ID, 1, true);

    describe(ABSENT_ID, 1)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INTEGRATION_NOT_FOUND"));
    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_INACTIVE"));
    describe(UNBACKED_ID, 1)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"));
  }

  @Test
  void refusesAnUnauthenticatedCaller() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    mockMvc.perform(get(PATH, TCP_ID, TCP_SCHEMA_VERSION)).andExpect(status().isUnauthorized());
  }

  @Test
  void publishesTheOperationInTheOpenApiDocument() throws Exception {
    var document =
        objectMapper.readTree(
            mockMvc
                .perform(get("/v3/api-docs").with(authentication(TestAuthentications.oidcUser())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());

    var operation =
        document.at(
            "/paths/~1v1~1integrations~1{integrationId}~1versions~1{schemaVersion}"
                + "~1connection~1settings/get");
    assertThat(operation.path("summary").asText()).isNotBlank();
    assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
    assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
    assertThat(operation.at("/responses/409").isMissingNode()).isFalse();
    var field = document.at("/components/schemas/ConnectionSettingsField/properties");
    assertThat(field.at("/min/oneOf").findValuesAsString("type"))
        .containsExactlyInAnyOrder("number", "string");
    assertThat(field.at("/max/oneOf").findValuesAsString("type"))
        .containsExactlyInAnyOrder("number", "string");
    assertThat(field.at("/default/oneOf").findValuesAsString("type"))
        .containsExactlyInAnyOrder("string", "number", "boolean");
  }

  /** Pins the published success example to the response record it documents. */
  @Test
  void publishesASuccessExampleOfEveryFieldKindThatRoundTrips() throws Exception {
    var document =
        objectMapper.readTree(
            mockMvc
                .perform(get("/v3/api-docs").with(authentication(TestAuthentications.oidcUser())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    var content =
        document.at(
            "/paths/~1v1~1integrations~1{integrationId}~1versions~1{schemaVersion}"
                + "~1connection~1settings/get/responses/200/content");
    var example =
        content.values().iterator().next().at("/examples/One section of every field kind/value");

    var response = objectMapper.treeToValue(example, IntegrationConnectionSettingsResponse.class);

    assertThat(response.sections()).hasSize(1);
    assertThat(response.sections().getFirst().fields())
        .extracting(IntegrationConnectionSettingsResponse.FieldResponse::kind)
        .containsExactlyInAnyOrder(SettingsFieldKind.values());
    assertThat(example.at("/sections/0/fields").findValues("default"))
        .hasSize(SettingsFieldKind.values().length);
    assertThat((JsonNode) objectMapper.valueToTree(response)).isEqualTo(example);
  }

  private ResultActions describe(UUID integrationId, int schemaVersion) throws Exception {
    return mockMvc.perform(
        get(PATH, integrationId, schemaVersion)
            .with(authentication(TestAuthentications.oidcUser())));
  }

  private void seedSchema(UUID integrationId, int schemaVersion, boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description, active)
        VALUES (?, 'Integration', 'Connection settings test integration', ?)
        ON CONFLICT (id) DO NOTHING
        """,
        integrationId,
        active);
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (?, ?)
        """,
        integrationId,
        schemaVersion);
  }
}
