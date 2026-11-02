// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup.tcp;

import com.opencop.core.api.general.business.model.integrationcatalog.ConnectionFraming;
import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupFaults;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes what TCP accepts: its extractors, its mapper/framing and egress rule, and every framing
 * that rule references.
 *
 * <p>A schema declaring TCP always has a form to render: the extractor registry refuses to start
 * without a TCP extractor, and the mapping registry without a TCP rule or with a rule naming an
 * undefined framing.
 */
public class TcpConnectionSetupDescriber implements ConnectionSetupDescriber {

  private final ConnectionExtractorRegistry extractorRegistry;
  private final ConnectionMappingRegistry mappingRegistry;

  public TcpConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    this.extractorRegistry = Objects.requireNonNull(extractorRegistry, "extractorRegistry");
    this.mappingRegistry = Objects.requireNonNull(mappingRegistry, "mappingRegistry");
  }

  @Override
  public TransportProtocol protocol() {
    return TransportProtocol.TCP;
  }

  @Override
  public Optional<ProtocolConnectionSetup> describe(IntegrationConnectionSchemaReference schema) {
    var extractors =
        extractorRegistry.extractorsFor(schema, ExtractorProtocol.TCP).stream()
            .map(ConnectionExtractorCapability::descriptor)
            .toList();
    if (extractors.isEmpty()) {
      throw ConnectionSetupFaults.registeredWithout("a TCP extractor", schema);
    }
    var rule =
        mappingRegistry
            .findTcp(schema)
            .orElseThrow(
                () -> ConnectionSetupFaults.registeredWithout("a TCP mapping rule", schema));
    return Optional.of(
        new ProtocolConnectionSetup.Tcp(
            extractors,
            rule,
            framings(rule, schema),
            AllowedAccessModesPolicy.allowedModes(TransportProtocol.TCP, rule)));
  }

  /** Resolves every framing the rule references against what the integrations provide in code. */
  private List<ConnectionFraming> framings(
      MapperFramingDeclaration rule, IntegrationConnectionSchemaReference schema) {
    return rule.framingIds().stream()
        .map(
            framingId ->
                mappingRegistry
                    .findFraming(framingId)
                    .orElseThrow(
                        () ->
                            ConnectionSetupFaults.registeredWithout(
                                "framing " + framingId, schema)))
        .map(definition -> new ConnectionFraming(definition.id(), definition.mode()))
        .toList();
  }
}
