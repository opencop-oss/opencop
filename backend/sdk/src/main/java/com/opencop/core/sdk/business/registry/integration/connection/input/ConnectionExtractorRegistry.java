// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.input;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable registry of every integration's connection extractors, indexed by connection schema
 * version and transport protocol, and looked up by {@link ExtractorProtocol} so each extractor
 * comes back typed by its protocol.
 *
 * <p>Construction rejects a misconfigured plugin before the application starts: an extractor must
 * belong to a registered schema that declares its protocol, implement that protocol's capability,
 * carry an identifier unique within that schema version, and describe its fields; a schema must
 * register an extractor for every declared protocol that {@linkplain
 * ExtractorProtocol#requiresExtractor() requires one}.
 *
 * <p>These checks are the only line of defence. Request-time readers, such as the connection-setup
 * description, trust what the registry returns and do not check it again, so a rule weakened here
 * surfaces as a server fault on a request rather than as a refusal to start.
 */
public final class ConnectionExtractorRegistry {

  private static final String INTEGRATION_ARGUMENT = "integration";
  private static final String PROTOCOL_ARGUMENT = "protocol";

  private final Map<ProtocolKey, List<ConnectionExtractorCapability<?, ?>>> extractorsByProtocol;
  private final Map<ExtractorKey, ConnectionExtractorCapability<?, ?>> extractorsById;

  public ConnectionExtractorRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<? extends ConnectionExtractorCapability<?, ?>> extractors) {
    Objects.requireNonNull(schemas, "schemas");
    Objects.requireNonNull(extractors, "extractors");

    var declaredSchemas = indexSchemas(schemas);
    var indexedById = new HashMap<ExtractorKey, ConnectionExtractorCapability<?, ?>>();
    var indexedByProtocol = new HashMap<ProtocolKey, List<ConnectionExtractorCapability<?, ?>>>();
    for (var extractor : extractors) {
      Objects.requireNonNull(extractor, "extractor");
      var protocol = requireRegistrable(extractor, declaredSchemas);
      var integration = IntegrationKey.from(extractor.schema());
      var id = indexById(extractor, protocol, integration, indexedById);
      requireDescriptor(extractor, protocol, id, extractor.schema());
      indexedByProtocol
          .computeIfAbsent(new ProtocolKey(integration, protocol), _ -> new ArrayList<>())
          .add(extractor);
    }

    var copiedByProtocol = new HashMap<ProtocolKey, List<ConnectionExtractorCapability<?, ?>>>();
    indexedByProtocol.forEach(
        (key, registered) -> copiedByProtocol.put(key, List.copyOf(registered)));
    extractorsByProtocol = Map.copyOf(copiedByProtocol);
    extractorsById = Map.copyOf(indexedById);

    requireEveryRequiredExtractor(declaredSchemas);
  }

  /** Indexes the schemas by identity and version, in declaration order, rejecting a duplicate. */
  private static Map<IntegrationKey, IntegrationConnectionSchema> indexSchemas(
      List<IntegrationConnectionSchema> schemas) {
    var declaredSchemas = new LinkedHashMap<IntegrationKey, IntegrationConnectionSchema>();
    for (var schema : schemas) {
      if (declaredSchemas.putIfAbsent(IntegrationKey.from(schema), schema) != null) {
        throw new IllegalArgumentException("Duplicate connection schema: " + describe(schema));
      }
      Objects.requireNonNull(schema.protocols(), "schema.protocols");
    }
    return declaredSchemas;
  }

  /**
   * Requires the extractor to declare a protocol that its registered schema declares, and to
   * implement that protocol's capability. Returns the protocol.
   */
  private static TransportProtocol requireRegistrable(
      ConnectionExtractorCapability<?, ?> extractor,
      Map<IntegrationKey, IntegrationConnectionSchema> declaredSchemas) {
    var schema = extractor.schema();
    var protocol = requireProtocol(extractor, schema);
    var declared = declaredSchemas.get(IntegrationKey.from(schema));
    if (declared == null) {
      throw new IllegalArgumentException(
          "Unregistered connection schema for "
              + protocol
              + " extractor '"
              + extractor.id()
              + "': "
              + describe(schema));
    }
    if (!declared.protocols().contains(protocol)) {
      throw new IllegalArgumentException(
          "Connection schema does not declare "
              + protocol
              + " for extractor '"
              + extractor.id()
              + "': "
              + describe(schema));
    }
    var capability = ExtractorProtocol.of(protocol).capability();
    if (!capability.isInstance(extractor)) {
      throw new IllegalArgumentException(
          protocol
              + " extractor '"
              + extractor.id()
              + "' must implement "
              + capability.getName()
              + " for connection schema: "
              + describe(schema));
    }
    return protocol;
  }

  /**
   * Indexes the extractor under its identifier, which must be unique within its schema version
   * across every protocol. Returns the identifier.
   */
  private static UUID indexById(
      ConnectionExtractorCapability<?, ?> extractor,
      TransportProtocol protocol,
      IntegrationKey integration,
      Map<ExtractorKey, ConnectionExtractorCapability<?, ?>> indexedById) {
    var schema = extractor.schema();
    var id = requireId(extractor.id(), schema);
    if (indexedById.putIfAbsent(new ExtractorKey(integration, id), extractor) != null) {
      throw new IllegalArgumentException(
          "Duplicate "
              + protocol
              + " extractor identifier '"
              + id
              + "' for connection schema: "
              + describe(schema));
    }
    return id;
  }

  /** Request-time readers assume every TCP schema registers an extractor and never check it. */
  private void requireEveryRequiredExtractor(
      Map<IntegrationKey, IntegrationConnectionSchema> declaredSchemas) {
    declaredSchemas.forEach(
        (integration, schema) -> {
          for (var protocol : schema.protocols()) {
            if (isMissingRequiredExtractor(integration, protocol)) {
              throw new IllegalArgumentException(
                  "Connection schema declares "
                      + protocol
                      + " but registers no extractor: "
                      + describe(schema));
            }
          }
        });
  }

  private boolean isMissingRequiredExtractor(
      IntegrationKey integration, TransportProtocol protocol) {
    return ExtractorProtocol.of(protocol).requiresExtractor()
        && !extractorsByProtocol.containsKey(new ProtocolKey(integration, protocol));
  }

  /**
   * Returns every extractor the selected connection schema registers for the protocol, in
   * registration order, or an empty list when it registers none.
   *
   * <p>Ordering is stable across runs so a caller rendering the extractors can cache what it built.
   */
  public <I extends ConnectionInput, M> List<ConnectionExtractorCapability<I, M>> extractorsFor(
      IntegrationConnectionSchemaReference integration, ExtractorProtocol<I, M> protocol) {
    return extractorsByProtocol
        .getOrDefault(ProtocolKey.from(integration, protocol), List.of())
        .stream()
        .map(ConnectionExtractorRegistry::<I, M>typed)
        .toList();
  }

  /**
   * Resolves the extractor the selected connection schema registers under the identifier for the
   * protocol.
   *
   * <p>An identifier the schema does not register for that protocol returns an empty result rather
   * than throwing, whether it is unknown, belongs to another protocol, or belongs to another
   * integration.
   *
   * <p>Never invokes the extractor. The result is a map lookup.
   */
  public <I extends ConnectionInput, M> Optional<ConnectionExtractorCapability<I, M>> findById(
      IntegrationConnectionSchemaReference integration,
      ExtractorProtocol<I, M> protocol,
      UUID extractorId) {
    Objects.requireNonNull(protocol, PROTOCOL_ARGUMENT);
    Objects.requireNonNull(extractorId, "extractorId");
    ConnectionExtractorCapability<?, ?> extractor =
        extractorsById.get(new ExtractorKey(IntegrationKey.from(integration), extractorId));
    if (extractor == null || extractor.protocol() != protocol.protocol()) {
      return Optional.empty();
    }
    return Optional.of(typed(extractor));
  }

  /**
   * Types an extractor by the protocol it was indexed under. Sound because construction checks that
   * every extractor of a protocol implements that protocol's capability, whose type arguments fix
   * the input it reads and the material it produces.
   */
  @SuppressWarnings("unchecked")
  private static <I extends ConnectionInput, M> ConnectionExtractorCapability<I, M> typed(
      ConnectionExtractorCapability<?, ?> extractor) {
    return (ConnectionExtractorCapability<I, M>) extractor;
  }

  private static TransportProtocol requireProtocol(
      ConnectionExtractorCapability<?, ?> extractor, IntegrationConnectionSchema schema) {
    var protocol = extractor.protocol();
    if (protocol == null) {
      throw new IllegalArgumentException(
          "Extractor protocol must not be null for extractor '"
              + extractor.id()
              + "' of connection schema: "
              + describe(schema));
    }
    return protocol;
  }

  private static UUID requireId(UUID id, IntegrationConnectionSchema schema) {
    if (id == null) {
      throw new IllegalArgumentException(
          "Extractor identifier must not be null for connection schema: " + describe(schema));
    }
    return id;
  }

  /**
   * Resolves an extractor's descriptor at construction so a malformed declaration aborts startup
   * rather than surfacing on the first connection attempt.
   *
   * <p>The descriptor model self-validates in its compact constructors, so an extractor holding its
   * descriptor in a constant or field initializer fails while its own bean is built, before the
   * registry sees it. Rethrowing here adds the connection schema and extractor the plugin author
   * needs to find it, whichever of the two moments the rejection comes from.
   */
  private static void requireDescriptor(
      ConnectionExtractorCapability<?, ?> extractor,
      TransportProtocol protocol,
      UUID id,
      IntegrationConnectionSchema schema) {
    ExtractorDescriptor descriptor;
    try {
      descriptor = extractor.descriptor();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Invalid descriptor for "
              + protocol
              + " extractor '"
              + id
              + "' for connection schema: "
              + describe(schema),
          exception);
    }
    if (descriptor == null) {
      throw new IllegalArgumentException(
          "Missing descriptor for "
              + protocol
              + " extractor '"
              + id
              + "' for connection schema: "
              + describe(schema));
    }
    requireStandardNamesDeclaredAsStandard(descriptor, protocol, id, schema);
  }

  /**
   * Rejects a field that takes a standard field's name without declaring that standard field. A
   * client localizes and renders a standard field by its key, so a look-alike under the same name
   * would be shown as the standard one while meaning something else.
   */
  private static void requireStandardNamesDeclaredAsStandard(
      ExtractorDescriptor descriptor,
      TransportProtocol protocol,
      UUID id,
      IntegrationConnectionSchema schema) {
    for (var field : descriptor.fields()) {
      var standard = StandardExtractorField.named(field.name());
      if (field.standard().isEmpty() && standard.isPresent()) {
        throw new IllegalArgumentException(
            ("Field '%s' of %s extractor '%s' for connection schema %s reuses the name of standard"
                    + " field %s; declare it with StandardExtractorField.%s.declare(required)")
                .formatted(
                    field.name(), protocol, id, describe(schema), standard.get(), standard.get()));
      }
    }
  }

  /**
   * Formats a connection schema for a construction-time rejection. The registry is a Spring bean,
   * so these messages abort application startup: they carry the stable key an operator can look the
   * plugin up by, and the declared name they can recognize it by without that lookup.
   */
  private static String describe(IntegrationConnectionSchema schema) {
    return IntegrationKey.from(schema) + " (" + schema.integration().name() + ")";
  }

  private record ExtractorKey(IntegrationKey integration, UUID id) {}

  private record ProtocolKey(IntegrationKey integration, TransportProtocol protocol) {

    private static ProtocolKey from(
        IntegrationConnectionSchemaReference integration, ExtractorProtocol<?, ?> protocol) {
      Objects.requireNonNull(protocol, PROTOCOL_ARGUMENT);
      return new ProtocolKey(IntegrationKey.from(integration), protocol.protocol());
    }
  }

  private record IntegrationKey(UUID id, int schemaVersion) {

    private IntegrationKey {
      Objects.requireNonNull(id, "integration.id");
      if (schemaVersion <= 0) {
        throw new IllegalArgumentException("integration.schemaVersion must be positive");
      }
    }

    private static IntegrationKey from(IntegrationConnectionSchema schema) {
      Objects.requireNonNull(schema, "schema");
      Objects.requireNonNull(schema.integration(), "schema.integration");
      return new IntegrationKey(schema.integration().id(), schema.schemaVersion());
    }

    private static IntegrationKey from(IntegrationConnectionSchemaReference integration) {
      Objects.requireNonNull(integration, INTEGRATION_ARGUMENT);
      return new IntegrationKey(integration.integrationId(), integration.schemaVersion());
    }
  }
}
