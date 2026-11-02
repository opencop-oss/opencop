// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.model;

import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.ConnectionMappingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingDefinition;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native schema version one's mapping rules. A TCP, UDP or Kafka connection decodes with any
 * available ingress mapper, since Native sets up connections for payloads no plugin owns. Native
 * provides no framing in code, so a TCP connection frames its stream with a delimiter of its own. A
 * TCP connection may also write with any available egress mapper. An RTSP connection carries KLV
 * metadata, which only the KLV mapper decodes.
 */
public record NativeConnectionMappingContribution(NativeConnectionSchema schema)
    implements ConnectionMappingContribution {

  private static final Map<TransportProtocol, ConnectionMappingDeclaration> DECLARATIONS =
      Map.of(
          TransportProtocol.TCP,
          MapperFramingDeclaration.of(new FramingRule.AnyMapperCustomFraming())
              .withEgress(EgressMapperRule.openMapper()),
          TransportProtocol.UDP,
          MapperDeclaration.openMapper(),
          TransportProtocol.RTSP,
          MapperDeclaration.closedTo(Set.of(NativeMappers.KLV_INGRESS)),
          TransportProtocol.KAFKA,
          MapperDeclaration.openMapper());

  public NativeConnectionMappingContribution {
    Objects.requireNonNull(schema, "schema");
  }

  @Override
  public Map<TransportProtocol, ConnectionMappingDeclaration> declarations() {
    return DECLARATIONS;
  }

  @Override
  public Set<FramingDefinition> framings() {
    return Set.of();
  }
}
