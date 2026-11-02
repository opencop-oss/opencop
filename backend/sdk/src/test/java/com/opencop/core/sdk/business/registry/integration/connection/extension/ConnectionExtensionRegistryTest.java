// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.model.integration.connection.settings.NoConnectionSettings;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionExtensionRegistryTest {

  private static final TestConnectionSchema SCHEMA =
      new TestConnectionSchema(
          UUID.fromString("0f7b8e7c-49b8-4d0e-8f43-4bb1b0f0a1c2"), 1, "Test", "Test integration");
  private static final TestConnectionSchema NEXT_VERSION =
      new TestConnectionSchema(SCHEMA.integrationId(), 2, "Test", "Test integration");
  private static final TestConnectionSchema OTHER_INTEGRATION =
      new TestConnectionSchema(
          UUID.fromString("6c1f0f6e-0c38-4a5b-9c0e-3f4d2b8a7e91"), 1, "Other", "Other one");
  private static final List<IntegrationConnectionSchema> SCHEMAS =
      List.of(SCHEMA, NEXT_VERSION, OTHER_INTEGRATION);
  private static final String EXTENSION_ID = "data-sync";
  private static final ExtensionDescriptor DESCRIPTOR =
      new ExtensionDescriptor("Data sync", "Synchronises remote data");

  @Test
  void resolvesADeclaredOperationWithItsPathVariables() {
    var subscribe = operation(ExtensionMethod.PUT, "/subscriptions/{name}");
    var registry = registry(extension(SCHEMA, EXTENSION_ID, subscribe));

    var match =
        registry
            .find(SCHEMA.reference(), EXTENSION_ID)
            .orElseThrow()
            .match(ExtensionMethod.PUT, List.of("subscriptions", "alpha"));

    assertThat(match)
        .hasValueSatisfying(
            found -> {
              assertThat(found.operation()).isSameAs(subscribe);
              assertThat(found.pathVariables()).isEqualTo(Map.of("name", "alpha"));
            });
  }

  @Test
  void findsNothingForAnUndeclaredMethodOrPath() {
    var extension =
        registry(extension(SCHEMA, EXTENSION_ID, operation(ExtensionMethod.GET, "/missions")))
            .find(SCHEMA.reference(), EXTENSION_ID)
            .orElseThrow();

    assertThat(extension.match(ExtensionMethod.DELETE, List.of("missions"))).isEmpty();
    assertThat(extension.match(ExtensionMethod.GET, List.of("packages"))).isEmpty();
  }

  @Test
  void prefersALiteralSegmentOverAVariable() {
    var byName = operation(ExtensionMethod.GET, "/missions/{name}");
    var active = operation(ExtensionMethod.GET, "/missions/active");
    var extension =
        registry(extension(SCHEMA, EXTENSION_ID, byName, active))
            .find(SCHEMA.reference(), EXTENSION_ID)
            .orElseThrow();

    assertThat(extension.match(ExtensionMethod.GET, List.of("missions", "active")))
        .hasValueSatisfying(found -> assertThat(found.operation()).isSameAs(active));
    assertThat(extension.match(ExtensionMethod.GET, List.of("missions", "alpha")))
        .hasValueSatisfying(found -> assertThat(found.operation()).isSameAs(byName));
  }

  @Test
  void prefersALiteralSegmentWhateverOtherPathsAreDeclaredBetween() {
    var byName = operation(ExtensionMethod.GET, "/missions/{name}");
    var create = operation(ExtensionMethod.POST, "/missions");
    var active = operation(ExtensionMethod.GET, "/missions/active");
    var extension =
        registry(extension(SCHEMA, EXTENSION_ID, byName, create, active))
            .find(SCHEMA.reference(), EXTENSION_ID)
            .orElseThrow();

    assertThat(extension.match(ExtensionMethod.GET, List.of("missions", "active")))
        .hasValueSatisfying(found -> assertThat(found.operation()).isSameAs(active));
    assertThat(extension.match(ExtensionMethod.GET, List.of("missions", "alpha")))
        .hasValueSatisfying(found -> assertThat(found.operation()).isSameAs(byName));
  }

  @Test
  void findsAnExtensionOnlyUnderItsOwnSchemaVersion() {
    var registry =
        registry(extension(SCHEMA, EXTENSION_ID, operation(ExtensionMethod.GET, "/missions")));

    assertThat(registry.find(SCHEMA.reference(), EXTENSION_ID)).isPresent();
    assertThat(registry.find(NEXT_VERSION.reference(), EXTENSION_ID)).isEmpty();
    assertThat(registry.find(SCHEMA.reference(), "other")).isEmpty();
  }

  @Test
  void letsAnotherSchemaReuseTheIdentifier() {
    var registry =
        registry(
            extension(SCHEMA, EXTENSION_ID, operation(ExtensionMethod.GET, "/missions")),
            extension(NEXT_VERSION, EXTENSION_ID, operation(ExtensionMethod.GET, "/missions")),
            extension(OTHER_INTEGRATION, EXTENSION_ID, operation(ExtensionMethod.GET, "/a")));

    assertThat(registry.find(OTHER_INTEGRATION.reference(), EXTENSION_ID)).isPresent();
    assertThat(registry.find(NEXT_VERSION.reference(), EXTENSION_ID)).isPresent();
  }

  @Test
  void listsASchemasExtensionsByIdentifierWithTheirDeclaredOperations() {
    var missions = operation(ExtensionMethod.GET, "/missions");
    var subscribe = operation(ExtensionMethod.PUT, "/subscriptions/{name}");
    var registry =
        registry(
            extension(SCHEMA, "zeta", missions),
            extension(SCHEMA, EXTENSION_ID, missions, subscribe),
            extension(NEXT_VERSION, "other", missions));

    var extensions = registry.extensionsOf(SCHEMA.reference());

    assertThat(extensions)
        .extracting(RegisteredExtension::id)
        .containsExactly(EXTENSION_ID, "zeta");
    assertThat(extensions.getFirst().descriptor()).isEqualTo(DESCRIPTOR);
    assertThat(extensions.getFirst().operations()).containsExactly(missions, subscribe);
    assertThat(registry.extensionsOf(OTHER_INTEGRATION.reference())).isEmpty();
  }

  @Test
  void listsEveryExtensionByIntegrationNewestVersionFirstThenIdentifier() {
    var missions = operation(ExtensionMethod.GET, "/missions");
    var registry =
        registry(
            extension(SCHEMA, "zeta", missions),
            extension(NEXT_VERSION, EXTENSION_ID, missions),
            extension(OTHER_INTEGRATION, EXTENSION_ID, missions),
            extension(SCHEMA, EXTENSION_ID, missions));

    assertThat(registry.all())
        .extracting(extension -> extension.schema().reference(), RegisteredExtension::id)
        .containsExactly(
            tuple(OTHER_INTEGRATION.reference(), EXTENSION_ID),
            tuple(NEXT_VERSION.reference(), EXTENSION_ID),
            tuple(SCHEMA.reference(), EXTENSION_ID),
            tuple(SCHEMA.reference(), "zeta"));
  }

  @Test
  void anEmptyRegistryIsValid() {
    assertThat(registry().find(SCHEMA.reference(), EXTENSION_ID)).isEmpty();
  }

  @Test
  void rejectsAnExtensionForAnUnregisteredSchema() {
    var unregistered =
        new TestConnectionSchema(
            UUID.fromString("a41e8b07-52cd-4a96-b7e3-1d60f8c45927"), 1, "Gone", "Unregistered");
    var extension = extension(unregistered, EXTENSION_ID);

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unregistered connection schema");
  }

  @Test
  void rejectsAnIdentifierThatIsNotKebabCase() {
    var extension = extension(SCHEMA, "Data_Sync");

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Data_Sync");
  }

  @Test
  void rejectsTwoExtensionsWithOneIdentifierOnOneSchema() {
    var first = extension(SCHEMA, EXTENSION_ID);
    var second = extension(SCHEMA, EXTENSION_ID);

    assertThatThrownBy(() -> registry(first, second))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate connection extension");
  }

  @Test
  void rejectsTwoOperationsAnsweringTheSameRequests() {
    var extension =
        extension(
            SCHEMA,
            EXTENSION_ID,
            operation(ExtensionMethod.PUT, "/subscriptions/{name}"),
            operation(ExtensionMethod.PUT, "/subscriptions/{id}"));

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PUT /subscriptions/{id}");
  }

  @Test
  void acceptsOnePathUnderSeveralMethods() {
    var registry =
        registry(
            extension(
                SCHEMA,
                EXTENSION_ID,
                operation(ExtensionMethod.GET, "/subscriptions/{name}"),
                operation(ExtensionMethod.DELETE, "/subscriptions/{name}")));

    assertThat(registry.find(SCHEMA.reference(), EXTENSION_ID)).isPresent();
  }

  @Test
  void rejectsAPathVariableTheHostReserves() {
    var extension = extension(SCHEMA, EXTENSION_ID, operation(ExtensionMethod.GET, "/{sourceId}"));

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserves");
  }

  @Test
  void rejectsAGetTakingARequestBody() {
    var extension =
        extension(
            SCHEMA,
            EXTENSION_ID,
            ExtensionOperation.of(
                ExtensionMethod.GET,
                "/missions",
                AccessLevel.READ,
                ExtensionBody.json(String.class),
                ExtensionBody.none(),
                (context, request) -> null));

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GET /missions");
  }

  @Test
  void rejectsAMissingDescriptor() {
    var extension = new TestExtension(SCHEMA, EXTENSION_ID, null, List.of());

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("descriptor");
  }

  @Test
  void rejectsAnExtensionReadingAnotherSettingsType() {
    var extension = new MismatchedSettingsExtension(SCHEMA);

    assertThatThrownBy(() -> registry(extension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(NoConnectionSettings.class.getName());
  }

  private static ConnectionExtensionRegistry registry(
      ConnectionExtensionCapability<?>... extensions) {
    return new ConnectionExtensionRegistry(
        SCHEMAS, List.of(extensions), new ConnectionSettingsRegistry(SCHEMAS, List.of()));
  }

  @SafeVarargs
  private static TestExtension extension(
      IntegrationConnectionSchema schema,
      String id,
      ExtensionOperation<NoConnectionSettings, ?, ?>... operations) {
    return new TestExtension(schema, id, DESCRIPTOR, List.of(operations));
  }

  private static ExtensionOperation<NoConnectionSettings, Void, Void> operation(
      ExtensionMethod method, String path) {
    return ExtensionOperation.of(
        method,
        path,
        AccessLevel.READ,
        ExtensionBody.none(),
        ExtensionBody.none(),
        (context, request) -> null);
  }

  private record TestExtension(
      IntegrationConnectionSchema schema,
      String id,
      ExtensionDescriptor descriptor,
      List<ExtensionOperation<NoConnectionSettings, ?, ?>> operations)
      implements ConnectionExtensionCapability<NoConnectionSettings> {

    @Override
    public Class<NoConnectionSettings> settingsType() {
      return NoConnectionSettings.class;
    }
  }

  private record MismatchedSettingsExtension(IntegrationConnectionSchema schema)
      implements ConnectionExtensionCapability<String> {

    @Override
    public Class<String> settingsType() {
      return String.class;
    }

    @Override
    public String id() {
      return EXTENSION_ID;
    }

    @Override
    public ExtensionDescriptor descriptor() {
      return DESCRIPTOR;
    }

    @Override
    public List<ExtensionOperation<String, ?, ?>> operations() {
      return List.of();
    }
  }
}
