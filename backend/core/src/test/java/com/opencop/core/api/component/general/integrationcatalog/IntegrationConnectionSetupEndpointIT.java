// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.EGRESS_MAPPER_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.FIRST_EXTRACTOR_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.FRAMING_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.MAPPER_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.NON_TCP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.NON_TCP_SCHEMA_VERSION;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.OPEN_FRAMING_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.SECOND_EXTRACTOR_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.TCP_ID;
import static com.opencop.core.api.component.general.integrationcatalog.IntegrationConnectionSetupWebTestApplication.TCP_SCHEMA_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorFieldKind;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.security.TestAuthentications;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

@ComponentWebIT(classes = IntegrationConnectionSetupWebTestApplication.class)
class IntegrationConnectionSetupEndpointIT extends AbstractWebIT {

  /** The TCP entry, which the fixture's TCP schema version offers alone. */
  private static final String TCP = "$.protocols[0]";

  private static final Pattern PROPERTY_NAME = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:");

  private static final UUID UNBACKED_ID = UUID.fromString("7f0a1b2c-3d4e-4f50-8a9b-0c1d2e3f4a55");
  private static final UUID ABSENT_ID = UUID.fromString("0e1d2c3b-4a59-4687-9a0b-1c2d3e4f5a66");

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetCatalog() {
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update("DELETE FROM config.integration");
  }

  @AfterEach
  void cleanCatalog() {
    jdbc.update("DELETE FROM config.integration_connection_schema");
    jdbc.update("DELETE FROM config.integration");
  }

  @Test
  void returnsEveryRegisteredExtractorWithItsDeclaredFieldsAndAcceptedFormats() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.integrationId").value(TCP_ID.toString()))
        .andExpect(jsonPath("$.schemaVersion").value(TCP_SCHEMA_VERSION))
        .andExpect(jsonPath("$.protocols.length()").value(1))
        .andExpect(jsonPath(TCP + ".kind").value("TCP"))
        .andExpect(jsonPath(TCP + ".extractors.length()").value(2))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].label")
                .value("Signed bundle"))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields.length()")
                .value(3))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[0].name")
                .value("host"))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[0].kind")
                .value("TEXT"))
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + FIRST_EXTRACTOR_ID
                        + "')].fields[0].standardKey")
                .value("HOST"))
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + FIRST_EXTRACTOR_ID
                        + "')].fields[1].standardKey")
                .doesNotExist())
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[1].kind")
                .value("SECRET"))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[2].kind")
                .value("FILE"))
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + FIRST_EXTRACTOR_ID
                        + "')].fields[2].extensions[0]")
                .value(".p12"))
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + FIRST_EXTRACTOR_ID
                        + "')].fields[2].mediaTypes[0]")
                .value("application/x-pkcs12"))
        .andExpect(
            jsonPath(TCP + ".extractors[?(@.id=='" + SECOND_EXTRACTOR_ID + "')].fields[0].kind")
                .value("FILE_LIST"));
  }

  @Test
  void returnsTheSameExtractorsInTheSameOrderOnEveryRequest() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    var first = body(TCP_ID, TCP_SCHEMA_VERSION);
    var second = body(TCP_ID, TCP_SCHEMA_VERSION);

    assertThat(first).isEqualTo(second);
    assertThat(first).contains(FIRST_EXTRACTOR_ID.toString(), SECOND_EXTRACTOR_ID.toString());
  }

  @Test
  void omitsFormatsFromScalarFieldsRatherThanRenderingThemEmpty() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[0].extensions")
                .doesNotExist())
        .andExpect(
            jsonPath(
                    TCP + ".extractors[?(@.id=='" + FIRST_EXTRACTOR_ID + "')].fields[1].mediaTypes")
                .doesNotExist());
  }

  @Test
  void tagsEachIngressRuleWithoutCrossCombiningThem() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath(TCP + ".ingress.length()").value(2))
        .andExpect(jsonPath(TCP + ".ingress[0].type").value("EXACT_PAIR"))
        .andExpect(jsonPath(TCP + ".ingress[0].mapperId").value(MAPPER_ID.toString()))
        .andExpect(jsonPath(TCP + ".ingress[0].framingId").value(FRAMING_ID.toString()))
        .andExpect(jsonPath(TCP + ".ingress[1].type").value("ANY_MAPPER_WITH"))
        .andExpect(jsonPath(TCP + ".ingress[1].framingId").value(OPEN_FRAMING_ID.toString()))
        // An open framing widens only the mapper side, so it names no mapper of its own.
        .andExpect(jsonPath(TCP + ".ingress[1].mapperId").doesNotExist());
  }

  @Test
  void publishesTheEgressRuleOfAProtocolThatWrites() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath(TCP + ".egress.anyMapper").value(false))
        .andExpect(jsonPath(TCP + ".egress.allowedMappers.length()").value(1))
        .andExpect(jsonPath(TCP + ".egress.allowedMappers[0]").value(EGRESS_MAPPER_ID.toString()));
  }

  @Test
  void letsAProtocolThatWritesTakeEveryAccessMode() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath(TCP + ".accessModes").value(contains("R", "W", "RW")));
  }

  @Test
  void resolvesEveryFramingItsChoicesReference() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(jsonPath(TCP + ".framings.length()").value(2))
        .andExpect(
            jsonPath(TCP + ".framings[?(@.id=='" + FRAMING_ID + "')].mode")
                .value("delimiterInclude"))
        .andExpect(
            jsonPath(TCP + ".framings[?(@.id=='" + OPEN_FRAMING_ID + "')].mode")
                .value("delimiterInclude"));
  }

  @Test
  void doesNotReturnADeactivatedIntegration() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, false);

    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_INACTIVE"));
  }

  @Test
  void distinguishesAnUnknownReferenceFromOneThisRuntimeCannotServe() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);
    seedSchema(UNBACKED_ID, 1, true);

    var unknown = describe(ABSENT_ID, 1).andExpect(status().isNotFound()).andReturn();
    var unavailable = describe(UNBACKED_ID, 1).andExpect(status().isConflict()).andReturn();

    var unknownCode = readResponse(unknown, GlobalErrorResponse.class).code();
    var unavailableCode = readResponse(unavailable, GlobalErrorResponse.class).code();
    assertThat(unknownCode).isEqualTo("INTEGRATION_NOT_FOUND");
    assertThat(unavailableCode).isEqualTo("INTEGRATION_UNAVAILABLE");
    assertThat(unknownCode).isNotEqualTo(unavailableCode);
  }

  @Test
  void answersASchemaVersionNoCatalogRowCouldCarryAsNotFound() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    // The key rejects a non-positive version, so without the guard this would surface through the
    // global handler as a 500 rather than as the unknown reference it is.
    for (var unusable : new int[] {0, -1}) {
      var result = describe(TCP_ID, unusable).andExpect(status().isNotFound()).andReturn();

      assertThat(readResponse(result, GlobalErrorResponse.class).code())
          .as("schemaVersion %d", unusable)
          .isEqualTo("INTEGRATION_NOT_FOUND");
    }
  }

  @Test
  void answersANonNumericSchemaVersionAsABadRequest() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    var result =
        mockMvc
            .perform(
                get("/v1/integrations/{integrationId}/versions/abc/connection/setup", TCP_ID)
                    .with(authentication(TestAuthentications.oidcUser())))
            .andExpect(status().isBadRequest())
            .andReturn();

    assertThat(readResponse(result, GlobalErrorResponse.class).code())
        .isEqualTo("INVALID_PARAMETER");
  }

  @Test
  void omitsMediaTypesEntirelyWhenAFileFieldDeclaresNone() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    // An empty set would otherwise serialize as [], which the published example does not show.
    describe(TCP_ID, TCP_SCHEMA_VERSION)
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + SECOND_EXTRACTOR_ID
                        + "')].fields[0].extensions[0]")
                .value(".pem"))
        .andExpect(
            jsonPath(
                    TCP
                        + ".extractors[?(@.id=='"
                        + SECOND_EXTRACTOR_ID
                        + "')].fields[0].mediaTypes")
                .doesNotExist());
  }

  @Test
  void answersAKnownSchemaVersionOfferingNoProtocolExplicitly() throws Exception {
    seedSchema(NON_TCP_ID, NON_TCP_SCHEMA_VERSION, true);

    describe(NON_TCP_ID, NON_TCP_SCHEMA_VERSION)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_PROTOCOL_UNSUPPORTED"))
        .andExpect(jsonPath("$.protocols").doesNotExist());
  }

  @Test
  void namesNoSpecificIntegrationAnywhereInTheSerializedResponse() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    var propertyNames = propertyNames(body(TCP_ID, TCP_SCHEMA_VERSION));

    // An exact whitelist rather than a blocklist of provider names: a field added later that names
    // an integration fails here even when nobody thought to forbid that name.
    assertThat(propertyNames)
        .containsExactlyInAnyOrder(
            "integrationId",
            "schemaVersion",
            "protocols",
            "accessModes",
            "extractors",
            "ingress",
            "type",
            "egress",
            "anyMapper",
            "allowedMappers",
            "framings",
            "mode",
            "id",
            "label",
            "labelKey",
            "fields",
            "name",
            "required",
            "kind",
            "extensions",
            "mediaTypes",
            "standardKey",
            "mapperId",
            "framingId");
  }

  @Test
  void mutatesNoCatalogRow() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);
    var before = catalogRows();

    describe(TCP_ID, TCP_SCHEMA_VERSION).andExpect(status().isOk());

    assertThat(catalogRows()).isEqualTo(before);
  }

  @Test
  void refusesAnUnauthenticatedCaller() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    mockMvc
        .perform(
            get(
                "/v1/integrations/{integrationId}/versions/{schemaVersion}/connection/setup",
                TCP_ID,
                TCP_SCHEMA_VERSION))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void noLongerServesTheFormerHyphenatedPath() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    mockMvc
        .perform(
            get(
                    "/v1/integrations/{integrationId}/versions/{schemaVersion}/connection-setup",
                    TCP_ID,
                    TCP_SCHEMA_VERSION)
                .with(authentication(TestAuthentications.oidcUser())))
        .andExpect(status().isNotFound());
  }

  @Test
  void servesAnAuthenticatedNonAdministrator() throws Exception {
    seedSchema(TCP_ID, TCP_SCHEMA_VERSION, true);

    describe(TCP_ID, TCP_SCHEMA_VERSION).andExpect(status().isOk());
  }

  @Test
  void publishesTheFormRenderingContractInTheOpenApiDocument() throws Exception {
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
                + "~1connection~1setup/get");
    assertThat(operation.path("summary").asText()).isNotBlank();
    assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
    assertThat(operation.at("/responses/404").isMissingNode()).isFalse();
    assertThat(operation.at("/responses/409").isMissingNode()).isFalse();

    // Each transport kind and each kind's rule set is published as its own discriminated union.
    assertThat(
            document
                .at("/components/schemas/ProtocolSetupResponse/discriminator/propertyName")
                .asString())
        .isEqualTo("kind");
    for (var rules : List.of("TcpIngressRule", "MapperIngressRule")) {
      assertThat(
              document
                  .at("/components/schemas/" + rules + "/discriminator/propertyName")
                  .asString())
          .as(rules)
          .isEqualTo("type");
    }

    // Access modes travel as their wire values, which is what a client submits back on create.
    // springdoc publishes a subtype's own properties beside the reference to its union.
    var tcpProperties =
        document
            .at("/components/schemas/TcpConnectionSetupResponse/allOf")
            .valueStream()
            .map(part -> part.path("properties"))
            .filter(properties -> !properties.isMissingNode())
            .findFirst()
            .orElseThrow();
    assertThat(
            tcpProperties
                .at("/accessModes/items/enum")
                .valueStream()
                .map(JsonNode::asString)
                .toList())
        .containsExactlyElementsOf(
            Stream.of(AccessMode.values()).map(AccessMode::getAccess).toList());

    // An integration that cannot write has no egress rule, so a client must accept null there.
    var egress = tcpProperties.path("egress");
    assertThat(allowsNull(egress)).as(egress.toPrettyString()).isTrue();
    assertThat(egress.toString()).contains("#/components/schemas/EgressRuleResponse");

    // The published schema is what a client picks a form component from, so it has to name every
    // kind rather than leave the mapping to the reader.
    var fieldDescription =
        document.at("/components/schemas/ExtractorFieldResponse").path("description").asText();
    assertThat(fieldDescription).isNotBlank();
    for (var kind : ExtractorFieldKind.values()) {
      assertThat(fieldDescription).as(kind.name()).contains(kind.name());
    }

    // springdoc inlines the enum into the property rather than emitting its own schema.
    var publishedKind = document.at("/components/schemas/ExtractorFieldResponse/properties/kind");
    assertThat(publishedKind.isMissingNode()).isFalse();
    for (var kind : ExtractorFieldKind.values()) {
      assertThat(publishedKind.toString()).as(kind.name()).contains(kind.name());
    }
  }

  private ResultActions describe(UUID integrationId, int schemaVersion) throws Exception {
    return mockMvc.perform(
        get(
                "/v1/integrations/{integrationId}/versions/{schemaVersion}/connection/setup",
                integrationId,
                schemaVersion)
            .with(authentication(TestAuthentications.oidcUser())));
  }

  private String body(UUID integrationId, int schemaVersion) throws Exception {
    return describe(integrationId, schemaVersion)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * Collects the distinct JSON property names of a response, so the wire contract is asserted as
   * serialized rather than through the records that produce it.
   */
  private static Set<String> propertyNames(String body) {
    var names = new TreeSet<String>();
    var matcher = PROPERTY_NAME.matcher(body);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }
    return names;
  }

  private List<String> catalogRows() {
    return jdbc
        .queryForList(
            """
            SELECT i.id, s.schema_version, i.name, i.description, i.active, i.updated_at
            FROM config.integration i
            JOIN config.integration_connection_schema s ON s.integration_id = i.id
            ORDER BY i.id, s.schema_version
            """)
        .stream()
        .map(Object::toString)
        .toList();
  }

  /**
   * Activation belongs to the integration, so the identity row carries it and the schema row only
   * records that the version exists.
   */
  private void seedSchema(UUID integrationId, int schemaVersion, boolean active) {
    jdbc.update(
        """
        INSERT INTO config.integration (id, name, description, active)
        VALUES (?, 'Integration', 'Connection setup test integration', ?)
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

  /** Whether an OpenAPI 3.1 schema admits null, as a listed type or as a union branch. */
  private static boolean allowsNull(JsonNode schema) {
    if (schema.path("type").valueStream().anyMatch(type -> "null".equals(type.asString()))) {
      return true;
    }
    return Stream.of("oneOf", "anyOf")
        .flatMap(union -> schema.path(union).valueStream())
        .anyMatch(branch -> "null".equals(branch.path("type").asString()));
  }
}
