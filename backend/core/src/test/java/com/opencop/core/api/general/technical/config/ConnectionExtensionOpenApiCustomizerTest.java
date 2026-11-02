// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.registry.integration.connection.extension.ConnectionExtensionRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConnectionExtensionOpenApiCustomizerTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2");
  private static final TestConnectionSchema V1 =
      new TestConnectionSchema(INTEGRATION_ID, 1, "Relay", "Test integration");
  private static final TestConnectionSchema V2 =
      new TestConnectionSchema(INTEGRATION_ID, 2, "Relay", "Test integration");
  private static final TestConnectionSchema OTHER =
      new TestConnectionSchema(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1, "Beacon", "Other one");
  private static final String OPENAPI_31 = "3.1.0";
  private static final String EXTENSION = "/v1/connections/{sourceId}/extensions/data-sync";
  private static final String JSON = "application/json";
  private static final String ERROR_SCHEMA = "GlobalErrorResponse";
  private static final String ERROR_REF = "#/components/schemas/" + ERROR_SCHEMA;
  private static final Pattern VARIABLE = Pattern.compile("\\{([^}]+)}");
  private static final List<String> ERROR_STATUSES =
      List.of("400", "404", "409", "500", "502", "504");
  private static final String FORBIDDEN = "403";

  private final OpenAPI document = customised();

  @Test
  void documentsEachOperationUnderItsExtensionWithItsPathVariables() {
    var subscribe = document.getPaths().get(EXTENSION + "/subscriptions/{name}").getPut();

    assertThat(subscribe.getParameters())
        .extracting(Parameter::getName, Parameter::getIn, Parameter::getRequired)
        .containsExactly(tuple("sourceId", "path", true), tuple("name", "path", true));
    assertThat(subscribe.getParameters().getFirst().getSchema().getFormat()).isEqualTo("uuid");
    assertThat(subscribe.getTags()).containsExactly("Connection extensions – Relay");
  }

  @Test
  void describesTheJsonBodiesWithTheirDeclaredTypes() {
    var subscribe = document.getPaths().get(EXTENSION + "/subscriptions/{name}").getPut();

    assertThat(subscribe.getRequestBody().getContent().get(JSON).getSchema().get$ref())
        .isEqualTo("#/components/schemas/Subscribe");
    assertThat(subscribe.getResponses().get("200").getContent().get(JSON).getSchema().get$ref())
        .isEqualTo("#/components/schemas/Subscribed");
    assertThat(document.getComponents().getSchemas().get("Subscribe").getProperties())
        .containsKey("role");
  }

  @Test
  void anOperationWithoutResponseBodyAnswersNoContent() {
    var root = document.getPaths().get(EXTENSION).getGet();

    assertThat(root.getResponses()).containsKey("204").doesNotContainKey("200");
    assertThat(root.getRequestBody()).isNull();
  }

  @Test
  void everyOperationDocumentsTheHostsErrors() {
    var subscribe = document.getPaths().get(EXTENSION + "/subscriptions/{name}").getPut();

    assertThat(subscribe.getResponses()).containsKeys(ERROR_STATUSES.toArray(String[]::new));
    assertThat(subscribe.getResponses()).containsKey(FORBIDDEN);
  }

  /** A refused caller is answered by the security layer, with its own body. */
  @Test
  void aRefusedCallerGetsTheSecurityLayersBody() {
    var subscribe = document.getPaths().get(EXTENSION + "/subscriptions/{name}").getPut();

    var body = subscribe.getResponses().get(FORBIDDEN).getContent().get(JSON).getSchema();

    assertThat(body.get$ref()).isNull();
    assertThat(body.getProperties()).containsOnlyKeys("error", "code");
    assertThat(body.getRequired()).containsExactly("error");
  }

  /** The host answers its errors with its own error body, whichever operation is invoked. */
  @Test
  void theHostsErrorsCarryTheHostsErrorBody() {
    var subscribe = document.getPaths().get(EXTENSION + "/subscriptions/{name}").getPut();

    ERROR_STATUSES.forEach(
        status ->
            assertThat(
                    subscribe
                        .getResponses()
                        .get(status)
                        .getContent()
                        .get(JSON)
                        .getSchema()
                        .get$ref())
                .as(status)
                .isEqualTo(ERROR_REF));
    assertThat(document.getComponents().getSchemas().get(ERROR_SCHEMA).getProperties())
        .containsKeys("code", "message");
  }

  /**
   * Core's own endpoints may already describe the error body; the extensions refer to that
   * description rather than adding their own.
   */
  @Test
  void anErrorBodyTheDocumentAlreadyDescribesIsReferredTo() {
    var existing = new ObjectSchema().description("Core's error body");
    var withErrors =
        customised(
            new OpenAPI()
                .openapi(OPENAPI_31)
                .components(new Components().addSchemas(ERROR_SCHEMA, existing)));

    assertThat(withErrors.getComponents().getSchemas().get(ERROR_SCHEMA)).isSameAs(existing);
    assertThat(
            withErrors
                .getPaths()
                .get(EXTENSION + "/subscriptions/{name}")
                .getPut()
                .getResponses()
                .get("409")
                .getContent()
                .get(JSON)
                .getSchema()
                .get$ref())
        .isEqualTo(ERROR_REF);
  }

  @Test
  void anOperationSeveralVersionsDeclareIsDocumentedOnceNamingThemAll() {
    var missions = document.getPaths().get(EXTENSION + "/missions").getGet();

    assertThat(missions.getDescription()).contains("Relay connection schema versions 2, 1");
    assertThat(missions.getResponses().get("200").getContent().get(JSON).getSchema().getTypes())
        .containsExactly("array");
  }

  @Test
  void anOperationSeveralIntegrationsDeclareCarriesEachOnesTag() {
    var missions = document.getPaths().get(EXTENSION + "/missions").getGet();

    assertThat(missions.getTags())
        .containsExactly("Connection extensions – Beacon", "Connection extensions – Relay");
    assertThat(missions.getDescription()).contains("Beacon connection schema version 1");
  }

  @Test
  void pathsDifferingOnlyInVariableNamesArePublishedOnce() {
    assertThat(document.getPaths()).containsKey(EXTENSION + "/items/{item}");
    assertThat(document.getPaths()).doesNotContainKey(EXTENSION + "/items/{name}");
    assertThat(document.getPaths().get(EXTENSION + "/items/{item}").getGet().getDescription())
        .contains("versions 2, 1");
  }

  /**
   * Another method declaring the same path under other variable names is published under the path's
   * first names, so its parameters must carry those names too.
   */
  @Test
  void anOperationPublishedUnderAnotherDeclarationsPathNamesItsVariables() {
    var remove = document.getPaths().get(EXTENSION + "/items/{item}").getDelete();

    assertThat(remove.getParameters())
        .extracting(Parameter::getName)
        .containsExactly("sourceId", "item");
  }

  @Test
  void everyOperationNamesExactlyTheVariablesOfItsPath() {
    document
        .getPaths()
        .forEach(
            (path, item) ->
                item.readOperations()
                    .forEach(
                        operation ->
                            assertThat(operation.getParameters())
                                .extracting(Parameter::getName)
                                .as(path)
                                .containsExactlyElementsOf(variablesOf(path))));
  }

  /**
   * A body type published under a name the document already gives another schema would silently
   * replace it, so building the document fails instead.
   */
  @Test
  void aBodyTypeNamedLikeADifferentExistingSchemaFailsTheDocument() {
    var document =
        new OpenAPI()
            .openapi(OPENAPI_31)
            .components(new Components().addSchemas("Subscribe", new StringSchema()));

    assertThatThrownBy(() -> customised(document))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Subscribe")
        .hasMessageContaining(Subscribe.class.getName());
  }

  @Test
  void aBodyTypeTheDocumentAlreadyDescribesAlikeIsKept() {
    var existing = document.getComponents().getSchemas().get("Subscribe");
    var again =
        new OpenAPI()
            .openapi(OPENAPI_31)
            .components(new Components().addSchemas("Subscribe", existing));

    assertThatCode(() -> customised(again)).doesNotThrowAnyException();
  }

  @Test
  void everyOperationHasItsOwnIdentifier() {
    var identifiers =
        document.getPaths().values().stream()
            .flatMap(path -> path.readOperations().stream())
            .map(Operation::getOperationId)
            .toList();

    assertThat(identifiers).doesNotHaveDuplicates().doesNotContainNull();
  }

  private static List<String> variablesOf(String path) {
    return VARIABLE.matcher(path).results().map(match -> match.group(1)).toList();
  }

  private static OpenAPI customised() {
    return customised(new OpenAPI().openapi(OPENAPI_31));
  }

  private static OpenAPI customised(OpenAPI document) {
    List<IntegrationConnectionSchema> schemas = List.of(V1, V2, OTHER);
    var registry =
        new ConnectionExtensionRegistry(
            schemas,
            List.of(
                new Extension(
                    V1,
                    List.of(
                        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
                            ExtensionMethod.GET,
                            "/",
                            AccessLevel.READ,
                            ExtensionBody.none(),
                            ExtensionBody.none(),
                            (context, request) -> null),
                        ExtensionOperation.<NoConnectionSettings, Void, String[]>of(
                            ExtensionMethod.GET,
                            "/missions",
                            AccessLevel.READ,
                            ExtensionBody.none(),
                            ExtensionBody.json(String[].class),
                            (context, request) -> new String[0]),
                        ExtensionOperation.<NoConnectionSettings, Subscribe, Subscribed>of(
                            ExtensionMethod.PUT,
                            "/subscriptions/{name}",
                            AccessLevel.MANAGE,
                            ExtensionBody.json(Subscribe.class),
                            ExtensionBody.json(Subscribed.class),
                            (context, request) -> null),
                        read("/items/{name}"),
                        ExtensionOperation.<NoConnectionSettings, Void, Void>of(
                            ExtensionMethod.DELETE,
                            "/items/{key}",
                            AccessLevel.MANAGE,
                            ExtensionBody.none(),
                            ExtensionBody.none(),
                            (context, request) -> null),
                        read("/things/{id}"),
                        read("/things/id"))),
                new Extension(V2, List.of(missions(), read("/items/{item}"))),
                new Extension(OTHER, List.of(missions()))),
            new ConnectionSettingsRegistry(schemas, List.of()));
    new ConnectionExtensionOpenApiCustomizer(registry).customise(document);
    return document;
  }

  private static ExtensionOperation<NoConnectionSettings, Void, String[]> missions() {
    return ExtensionOperation.of(
        ExtensionMethod.GET,
        "/missions",
        AccessLevel.READ,
        ExtensionBody.none(),
        ExtensionBody.json(String[].class),
        (context, request) -> new String[0]);
  }

  private static ExtensionOperation<NoConnectionSettings, Void, Void> read(String path) {
    return ExtensionOperation.of(
        ExtensionMethod.GET,
        path,
        AccessLevel.READ,
        ExtensionBody.none(),
        ExtensionBody.none(),
        (context, request) -> null);
  }

  record Subscribe(String role) {}

  record Subscribed(String mission, String role) {}

  private record Extension(
      IntegrationConnectionSchema schema,
      List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations)
      implements ConnectionExtensionCapability<NoConnectionSettings> {

    @Override
    public Class<NoConnectionSettings> settingsType() {
      return NoConnectionSettings.class;
    }

    @Override
    public String id() {
      return "data-sync";
    }

    @Override
    public ExtensionDescriptor descriptor() {
      return new ExtensionDescriptor("Data sync", "Synchronises remote data");
    }
  }
}
