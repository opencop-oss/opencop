// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.DUAL_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.DUAL_NAME;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.MAPPER_ONLY_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.MAPPER_ONLY_NAME;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.TCP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.TCP_NAME;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.UDP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.UDP_NAME;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.UNLOADED_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationCatalogDiscoveryWebTestApplication.UNLOADED_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = IntegrationCatalogDiscoveryWebTestApplication.class)
class IntegrationCatalogDiscoveryEndpointIT extends AbstractWebIT {

  private static final UUID CALLER_ID = UUID.fromString("c7f2b1a4-8e3d-4f5a-9b6c-1d2e3f4a5b60");
  private static final OffsetDateTime FIXED_UPDATED_AT =
      OffsetDateTime.parse("2026-08-19T10:11:12Z");

  record Entry(
      UUID integrationId, int schemaVersion, String name, String description, boolean available) {}

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedCatalog() {
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update("DELETE FROM config.integration");

    seedIntegration(TCP_ID, TCP_NAME, true);
    seedIntegration(UDP_ID, UDP_NAME, true);
    seedIntegration(DUAL_ID, DUAL_NAME, true);
    seedIntegration(UNLOADED_ID, UNLOADED_NAME, true);
    seedIntegration(MAPPER_ONLY_ID, MAPPER_ONLY_NAME, true);

    seedSchema(TCP_ID, 1);
    seedSchema(TCP_ID, 2);
    seedSchema(UDP_ID, 1);
    seedSchema(DUAL_ID, 1);
    seedSchema(UNLOADED_ID, 1);
  }

  @Test
  void unfilteredListingReturnsEveryVersionWithAvailabilityFlags() throws Exception {
    assertThat(listIntegrations(null))
        .extracting(Entry::integrationId, Entry::schemaVersion, Entry::available)
        .containsExactly(
            tuple(UDP_ID, 1, true),
            tuple(DUAL_ID, 1, true),
            tuple(UNLOADED_ID, 1, false),
            tuple(TCP_ID, 1, true),
            tuple(TCP_ID, 2, true));
  }

  @Test
  void theResponseNamesTheIntegrationIdentifierAndEveryDocumentedField() throws Exception {
    request(null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].integrationId").value(UDP_ID.toString()))
        .andExpect(jsonPath("$[0].schemaVersion").value(1))
        .andExpect(jsonPath("$[0].name").value(UDP_NAME))
        .andExpect(jsonPath("$[0].description").value(UDP_NAME + " description"))
        .andExpect(jsonPath("$[0].nameKey").value("integrations." + UDP_ID + ".name"))
        .andExpect(jsonPath("$[0].descriptionKey").value("integrations." + UDP_ID + ".description"))
        .andExpect(jsonPath("$[0].available").value(true))
        .andExpect(jsonPath("$[0].id").doesNotExist());
  }

  @Test
  void protocolFilterReturnsOnlyVersionsDeclaringThatProtocol() throws Exception {
    assertThat(listIntegrations("TCP"))
        .extracting(Entry::integrationId, Entry::schemaVersion)
        .containsExactly(tuple(DUAL_ID, 1), tuple(TCP_ID, 1), tuple(TCP_ID, 2));

    assertThat(listIntegrations("UDP"))
        .extracting(Entry::integrationId, Entry::schemaVersion)
        .containsExactly(tuple(UDP_ID, 1), tuple(DUAL_ID, 1));
  }

  @Test
  void aVersionDeclaringSeveralProtocolsAppearsUnderEachOfThem() throws Exception {
    assertThat(listIntegrations("TCP")).extracting(Entry::integrationId).contains(DUAL_ID);
    assertThat(listIntegrations("UDP")).extracting(Entry::integrationId).contains(DUAL_ID);
  }

  @Test
  void aVersionWithNoLoadedSchemaIsUnavailableUnfilteredAndAbsentWhenFiltered() throws Exception {
    assertThat(listIntegrations(null))
        .filteredOn(entry -> entry.integrationId().equals(UNLOADED_ID))
        .singleElement()
        .extracting(Entry::available, Entry::name)
        .containsExactly(false, UNLOADED_NAME);

    assertThat(listIntegrations("TCP"))
        .extracting(Entry::integrationId)
        .doesNotContain(UNLOADED_ID);
    assertThat(listIntegrations("UDP"))
        .extracting(Entry::integrationId)
        .doesNotContain(UNLOADED_ID);
  }

  @Test
  void aDeactivatedIntegrationWithholdsEveryVersionFromEveryListing() throws Exception {
    jdbc.update("UPDATE config.integration SET active = false WHERE id = ?", TCP_ID);

    assertThat(listIntegrations(null)).extracting(Entry::integrationId).doesNotContain(TCP_ID);
    assertThat(listIntegrations("TCP")).extracting(Entry::integrationId).doesNotContain(TCP_ID);
  }

  @Test
  void anIntegrationOfferingNoConnectionContractIsAbsent() throws Exception {
    assertThat(listIntegrations(null))
        .extracting(Entry::integrationId)
        .doesNotContain(MAPPER_ONLY_ID);
  }

  @Test
  void listingIsOrderedByNameThenIntegrationThenSchemaVersion() throws Exception {
    assertThat(listIntegrations(null))
        .extracting(Entry::name, Entry::schemaVersion)
        .containsExactly(
            tuple(UDP_NAME, 1),
            tuple(DUAL_NAME, 1),
            tuple(UNLOADED_NAME, 1),
            tuple(TCP_NAME, 1),
            tuple(TCP_NAME, 2));
  }

  @Test
  void repeatedRequestsReturnTheSameOrder() throws Exception {
    assertThat(listIntegrations(null)).isEqualTo(listIntegrations(null));
  }

  @Test
  void protocolFilterIsCaseInsensitive() throws Exception {
    assertThat(listIntegrations("tcp")).isEqualTo(listIntegrations("TCP"));
  }

  @Test
  void anUnknownProtocolIsRejectedWithAnActionableResponseRatherThanAnEmptySuccess()
      throws Exception {
    var result = request("SCTP").andExpect(status().isBadRequest()).andReturn();

    assertThat(readResponse(result, GlobalErrorResponse.class))
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo("UNKNOWN_TRANSPORT_PROTOCOL");
              assertThat(error.message()).contains("SCTP", "TCP", "UDP");
            });
  }

  @Test
  void aBlankProtocolIsRejectedRatherThanTreatedAsNoFilter() throws Exception {
    request("")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_TRANSPORT_PROTOCOL"));
  }

  @Test
  void listingMutatesNoCatalogRow() throws Exception {
    jdbc.update("UPDATE config.integration SET updated_at = ?", FIXED_UPDATED_AT);

    listIntegrations(null);
    listIntegrations("TCP");
    request("SCTP").andExpect(status().isBadRequest());

    assertThat(jdbc.queryForList("SELECT updated_at FROM config.integration", OffsetDateTime.class))
        .isNotEmpty()
        .allMatch(FIXED_UPDATED_AT::isEqual);
    assertThat(jdbc.queryForList("SELECT active FROM config.integration", Boolean.class))
        .isNotEmpty()
        .allMatch(Boolean::booleanValue);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM config.integration_connection_schema", Integer.class))
        .isEqualTo(5);
  }

  @Test
  void unauthenticatedCallerIsUnauthorized() throws Exception {
    mockMvc.perform(get("/v1/integrations")).andExpect(status().isUnauthorized());
  }

  @Test
  void anAuthenticatedNonAdministratorMayListTheCatalog() throws Exception {
    request(null).andExpect(status().isOk());
  }

  private List<Entry> listIntegrations(String protocol) throws Exception {
    var result = request(protocol).andExpect(status().isOk()).andReturn();
    return readResponse(result, new TypeReference<List<Entry>>() {});
  }

  private ResultActions request(String protocol) throws Exception {
    var request =
        get("/v1/integrations").with(authentication(TestAuthentications.oidcUser(CALLER_ID)));
    if (protocol != null) {
      request = request.param("protocol", protocol);
    }
    return mockMvc.perform(request);
  }

  private void seedIntegration(UUID id, String name, boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration(id, name, description, active)
        VALUES (?, ?, ?, ?)
        """,
        id,
        name,
        name + " description",
        active);
  }

  private void seedSchema(UUID integrationId, int schemaVersion) {
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema(integration_id, schema_version)
        VALUES (?, ?)
        """,
        integrationId,
        schemaVersion);
  }
}
