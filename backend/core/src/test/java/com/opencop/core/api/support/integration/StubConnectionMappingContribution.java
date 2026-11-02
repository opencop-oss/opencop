// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.support.integration;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingDefinition;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.profile.DelimiterModes;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Fixed mapper/framing rules for tests that read a schema version's permitted choices. */
public record StubConnectionMappingContribution(
    IntegrationConnectionSchema schema,
    Map<TransportProtocol, ? extends ConnectionMappingDeclaration> declarations,
    Set<FramingDefinition> framings)
    implements ConnectionMappingContribution {

  /**
   * Defines a framing for every identifier the declarations reference, which registry construction
   * requires, so a test states its mapper/framing choices and nothing else.
   */
  public StubConnectionMappingContribution(
      IntegrationConnectionSchema schema,
      Map<TransportProtocol, ? extends ConnectionMappingDeclaration> declarations) {
    this(schema, declarations, framingsFor(declarations));
  }

  private static Set<FramingDefinition> framingsFor(
      Map<TransportProtocol, ? extends ConnectionMappingDeclaration> declarations) {
    return declarations.values().stream()
        .filter(MapperFramingDeclaration.class::isInstance)
        .map(MapperFramingDeclaration.class::cast)
        .flatMap(declaration -> declaration.framingIds().stream())
        .distinct()
        .map(StubConnectionMappingContribution::framing)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static FramingDefinition framing(UUID framingId) {
    return new FramingDefinition(framingId, DelimiterModes.DELIMITER_INCLUDE, "</end>");
  }
}
