// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.registry.integration.connection.mapping;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingDefinition;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-process, exact-version lookup of connection schema mapping rules.
 *
 * <p>Construction rejects a misconfigured plugin before the application starts: every registered
 * schema declares exactly one rule, of its protocol's shape, for each protocol it declares, and
 * every framing a rule references is defined by its contribution.
 *
 * <p>These checks are the only line of defence. Request-time readers, such as the connection-setup
 * description, trust that a declared protocol has a rule and that a referenced framing resolves,
 * and do not check either again, so a rule weakened here surfaces as a server fault on a request
 * rather than as a refusal to start.
 */
public final class ConnectionMappingRegistry {
  private final Map<
          IntegrationConnectionSchemaReference,
          Map<TransportProtocol, ConnectionMappingDeclaration>>
      declarations;
  private final Map<UUID, FramingDefinition> framings;

  public ConnectionMappingRegistry(
      List<IntegrationConnectionSchema> schemas,
      List<ConnectionMappingContribution> contributions) {
    Objects.requireNonNull(schemas, "schemas");
    Objects.requireNonNull(contributions, "contributions");
    var registered =
        new HashMap<IntegrationConnectionSchemaReference, IntegrationConnectionSchema>();
    for (var schema : schemas) {
      var reference = reference(schema);
      if (registered.putIfAbsent(reference, schema) != null) {
        throw invalid("Duplicate connection schema", schema);
      }
    }
    var indexed =
        new HashMap<
            IntegrationConnectionSchemaReference,
            Map<TransportProtocol, ConnectionMappingDeclaration>>();
    var indexedFramings = new HashMap<UUID, FramingDefinition>();
    for (var contribution : contributions) {
      Objects.requireNonNull(contribution, "contribution");
      var schema = Objects.requireNonNull(contribution.schema(), "contribution.schema");
      var reference = reference(schema);
      if (!registered.containsKey(reference)) {
        throw invalid("Orphan mapping declaration", schema);
      }
      if (indexed.containsKey(reference)) {
        throw invalid("Duplicate mapping declaration", schema);
      }
      var declaredRules = contribution.declarations();
      if (declaredRules == null
          || declaredRules.entrySet().stream()
              .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
        throw invalid("Missing mapping declaration", schema);
      }
      Map<TransportProtocol, ConnectionMappingDeclaration> rules = Map.copyOf(declaredRules);
      if (!protocols(schema).containsAll(rules.keySet())) {
        throw invalid("Unsupported protocol in mapping declaration", schema);
      }
      rules.forEach((protocol, rule) -> requireShapeOf(protocol, rule, schema));
      // Request-time readers resolve every referenced framing through findFraming without a
      // fallback.
      var defined = definedFramings(contribution, schema);
      if (!defined.containsAll(referencedFramings(rules))) {
        throw invalid("Mapping declaration references an undefined framing", schema);
      }
      // Schema versions of one integration may share a framing, but an identifier means one
      // framing: connections store only the identifier.
      for (var framing : contribution.framings()) {
        var existing = indexedFramings.putIfAbsent(framing.id(), framing);
        if (existing != null && !existing.equals(framing)) {
          throw invalid("Conflicting framing definition " + framing.id(), schema);
        }
      }
      indexed.put(reference, rules);
    }
    // Request-time readers assume a rule for every declared protocol and never check it.
    for (var entry : registered.entrySet()) {
      var expected = protocols(entry.getValue());
      var actual = indexed.getOrDefault(entry.getKey(), Map.of());
      if (!actual.keySet().equals(expected)) {
        throw invalid("Missing mapping declaration for connection protocol", entry.getValue());
      }
    }
    declarations = Map.copyOf(indexed);
    framings = Map.copyOf(indexedFramings);
  }

  /** Finds the declaration of the protocol, whatever its shape. */
  public Optional<ConnectionMappingDeclaration> find(
      IntegrationConnectionSchemaReference reference, TransportProtocol protocol) {
    Objects.requireNonNull(reference, "reference");
    Objects.requireNonNull(protocol, "protocol");
    return Optional.ofNullable(declarations.getOrDefault(reference, Map.of()).get(protocol));
  }

  /** Finds the TCP declaration, which pairs mappers with framings. */
  public Optional<MapperFramingDeclaration> findTcp(
      IntegrationConnectionSchemaReference reference) {
    return find(reference, TransportProtocol.TCP).map(MapperFramingDeclaration.class::cast);
  }

  /** Finds the declaration of a protocol that does not frame, which chooses a mapper alone. */
  public Optional<MapperDeclaration> findMapperOnly(
      IntegrationConnectionSchemaReference reference, TransportProtocol protocol) {
    if (frames(protocol)) {
      throw new IllegalArgumentException(protocol + " declares framing; use findTcp");
    }
    return find(reference, protocol).map(MapperDeclaration.class::cast);
  }

  /**
   * Requires a declaration to have its protocol's shape, which is what lets the typed finders
   * return it as that shape.
   */
  private static void requireShapeOf(
      TransportProtocol protocol,
      ConnectionMappingDeclaration rule,
      IntegrationConnectionSchema schema) {
    if (frames(protocol) && !(rule instanceof MapperFramingDeclaration)) {
      throw invalid(protocol + " mapping declaration must declare framing", schema);
    }
    if (!frames(protocol) && !(rule instanceof MapperDeclaration)) {
      throw invalid(protocol + " mapping declaration must be mapper-only", schema);
    }
  }

  /** Only a TCP stream is framed into messages; every other transport delivers whole messages. */
  private static boolean frames(TransportProtocol protocol) {
    return protocol == TransportProtocol.TCP;
  }

  /** Finds the framing an integration provides in code under this identifier. */
  public Optional<FramingDefinition> findFraming(UUID framingId) {
    Objects.requireNonNull(framingId, "framingId");
    return Optional.ofNullable(framings.get(framingId));
  }

  private static Set<UUID> definedFramings(
      ConnectionMappingContribution contribution, IntegrationConnectionSchema schema) {
    var declared = contribution.framings();
    if (declared == null || declared.stream().anyMatch(Objects::isNull)) {
      throw invalid("Missing framing definitions", schema);
    }
    return declared.stream().map(FramingDefinition::id).collect(Collectors.toSet());
  }

  private static Set<UUID> referencedFramings(
      Map<TransportProtocol, ConnectionMappingDeclaration> rules) {
    return rules.values().stream()
        .filter(MapperFramingDeclaration.class::isInstance)
        .map(MapperFramingDeclaration.class::cast)
        .flatMap(rule -> rule.framingIds().stream())
        .collect(Collectors.toSet());
  }

  private static IntegrationConnectionSchemaReference reference(
      IntegrationConnectionSchema schema) {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(schema.integration(), "schema.integration");
    return schema.reference();
  }

  private static Set<TransportProtocol> protocols(IntegrationConnectionSchema schema) {
    var protocols = schema.protocols();
    if (protocols == null || protocols.stream().anyMatch(Objects::isNull)) {
      throw invalid("Invalid connection protocols", schema);
    }
    return protocols;
  }

  private static IllegalArgumentException invalid(
      String reason, IntegrationConnectionSchema schema) {
    return new IllegalArgumentException(
        reason + ": " + reference(schema) + " (" + schema.integration().name() + ")");
  }
}
