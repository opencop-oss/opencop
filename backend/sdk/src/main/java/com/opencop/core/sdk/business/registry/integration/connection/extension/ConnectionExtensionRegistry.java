// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.StableKeySyntax;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionBody;
import com.opencop.core.sdk.business.model.integration.connection.extension.ExtensionMethod;
import com.opencop.core.sdk.business.registry.integration.connection.settings.ConnectionSettingsRegistry;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionCapability;
import com.opencop.core.sdk.ports.integration.connection.extension.ExtensionOperation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable registry of the connection extensions integrations contribute, addressed by the schema
 * version a connection binds to and the extension's identifier.
 *
 * <p>Every declaration is checked at construction, so a malformed extension aborts startup instead
 * of failing a request. An empty registry is valid: no integration need declare an extension.
 */
public final class ConnectionExtensionRegistry {

  private static final Set<String> RESERVED_VARIABLES = Set.of("sourceId", "extensionId");

  private final Map<ExtensionKey, RegisteredExtension> extensions;

  public ConnectionExtensionRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<? extends ConnectionExtensionCapability<?>> capabilities,
      ConnectionSettingsRegistry settingsRegistry) {
    Objects.requireNonNull(schemas, "schemas");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(settingsRegistry, "settingsRegistry");

    var registeredSchemas =
        new HashMap<IntegrationConnectionSchemaReference, IntegrationConnectionSchema>();
    for (var schema : schemas) {
      registeredSchemas.putIfAbsent(schema.reference(), schema);
    }

    var indexed = new HashMap<ExtensionKey, RegisteredExtension>();
    for (var capability : capabilities) {
      Objects.requireNonNull(capability, "capability");
      var reference = Objects.requireNonNull(capability.schema(), "schema").reference();
      var schema = registeredSchemas.get(reference);
      if (schema == null) {
        throw new IllegalArgumentException(
            "Unregistered connection schema for connection extension: " + reference);
      }
      var key = new ExtensionKey(reference, requireIdentifier(capability.id()));
      requireDescriptor(capability, key);
      requireSettingsType(capability, settingsRegistry.settingsType(schema), key);
      var extension =
          new RegisteredExtension(
              capability,
              schema,
              key.extensionId(),
              capability.descriptor(),
              requireDistinctOperations(capability, key));
      if (indexed.putIfAbsent(key, extension) != null) {
        throw new IllegalArgumentException("Duplicate connection extension " + key);
      }
    }
    this.extensions = Map.copyOf(indexed);
  }

  /** Resolves an extension of the schema version a connection binds to. */
  public Optional<RegisteredExtension> find(
      IntegrationConnectionSchemaReference schema, String extensionId) {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(extensionId, "extensionId");
    return Optional.ofNullable(extensions.get(new ExtensionKey(schema, extensionId)));
  }

  /**
   * Every registered extension: by integration name, its newest schema version first, then by
   * identifier.
   */
  public List<RegisteredExtension> all() {
    return extensions.values().stream()
        .sorted(
            Comparator.comparing(
                    (RegisteredExtension extension) -> extension.schema().integration().name())
                .thenComparing(
                    extension -> extension.schema().schemaVersion(), Comparator.reverseOrder())
                .thenComparing(RegisteredExtension::id))
        .toList();
  }

  /** The extensions of a schema version, by identifier. */
  public List<RegisteredExtension> extensionsOf(IntegrationConnectionSchemaReference schema) {
    Objects.requireNonNull(schema, "schema");
    return extensions.entrySet().stream()
        .filter(entry -> entry.getKey().schema().equals(schema))
        .map(Map.Entry::getValue)
        .sorted(Comparator.comparing(RegisteredExtension::id))
        .toList();
  }

  private static String requireIdentifier(String id) {
    if (!StableKeySyntax.isValid(id)) {
      throw new IllegalArgumentException(
          "Connection extension identifier '" + id + "' is not " + StableKeySyntax.DESCRIPTION);
    }
    return id;
  }

  private static void requireDescriptor(
      ConnectionExtensionCapability<?> capability, ExtensionKey key) {
    if (capability.descriptor() == null) {
      throw new IllegalArgumentException("Missing descriptor for connection extension " + key);
    }
  }

  /**
   * The host hands every operation the effective settings its schema resolves to, so an extension
   * reading another type would fail on its first request rather than at startup.
   */
  private static void requireSettingsType(
      ConnectionExtensionCapability<?> capability, Class<?> expected, ExtensionKey key) {
    if (!expected.equals(capability.settingsType())) {
      throw new IllegalArgumentException(
          "Connection extension "
              + key
              + " reads "
              + capability.settingsType().getName()
              + " settings, but the schema's settings are "
              + expected.getName());
    }
  }

  private static List<ExtensionOperation<?, ?, ?>> requireDistinctOperations(
      ConnectionExtensionCapability<?> capability, ExtensionKey key) {
    var accepted = new ArrayList<ExtensionOperation<?, ?, ?>>();
    for (var operation : Objects.requireNonNull(capability.operations(), "operations")) {
      Objects.requireNonNull(operation, "operation");
      if (namesAReservedVariable(operation)) {
        throw new IllegalArgumentException(
            "Operation "
                + describe(operation)
                + " of connection extension "
                + key
                + " names a path variable the host reserves: "
                + RESERVED_VARIABLES);
      }
      if (isGetWithBody(operation)) {
        throw new IllegalArgumentException(
            "Operation "
                + describe(operation)
                + " of connection extension "
                + key
                + " takes a request body, which GET cannot carry");
      }
      if (accepted.stream().anyMatch(other -> answersTheSameRequests(other, operation))) {
        throw new IllegalArgumentException(
            "Operation "
                + describe(operation)
                + " of connection extension "
                + key
                + " answers the same requests as another of its operations");
      }
      accepted.add(operation);
    }
    return List.copyOf(accepted);
  }

  /** The host's own path variables precede every operation's, so an operation cannot reuse them. */
  private static boolean namesAReservedVariable(ExtensionOperation<?, ?, ?> operation) {
    return operation.path().variableNames().stream().anyMatch(RESERVED_VARIABLES::contains);
  }

  private static boolean isGetWithBody(ExtensionOperation<?, ?, ?> operation) {
    return operation.method() == ExtensionMethod.GET
        && !(operation.request() instanceof ExtensionBody.None);
  }

  private static boolean answersTheSameRequests(
      ExtensionOperation<?, ?, ?> first, ExtensionOperation<?, ?, ?> second) {
    return first.method() == second.method() && first.path().hasSameShapeAs(second.path());
  }

  private static String describe(ExtensionOperation<?, ?, ?> operation) {
    return operation.method() + " " + operation.path();
  }

  private record ExtensionKey(IntegrationConnectionSchemaReference schema, String extensionId) {

    @Override
    public String toString() {
      return "'"
          + extensionId
          + "' of connection schema "
          + schema.integrationId()
          + " v"
          + schema.schemaVersion();
    }
  }
}
