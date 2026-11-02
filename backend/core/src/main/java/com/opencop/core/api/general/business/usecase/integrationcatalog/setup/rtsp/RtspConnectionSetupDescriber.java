// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.integrationcatalog.setup.rtsp;

import com.opencop.core.api.general.business.model.integrationcatalog.ProtocolConnectionSetup;
import com.opencop.core.api.general.business.policy.connection.access.AllowedAccessModesPolicy;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupDescriber;
import com.opencop.core.api.general.business.usecase.integrationcatalog.setup.ConnectionSetupFaults;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import java.util.Objects;
import java.util.Optional;

/**
 * Describes what RTSP accepts: its extractors and its mapper rule.
 *
 * <p>A schema may declare RTSP for its mapping rule alone, reading its input natively, and register
 * no extractor; it then has no form to render. The mapping registry refuses to start without a RTSP
 * rule for a schema declaring RTSP.
 */
public class RtspConnectionSetupDescriber implements ConnectionSetupDescriber {

  private final ConnectionExtractorRegistry extractorRegistry;
  private final ConnectionMappingRegistry mappingRegistry;

  public RtspConnectionSetupDescriber(
      ConnectionExtractorRegistry extractorRegistry, ConnectionMappingRegistry mappingRegistry) {
    this.extractorRegistry = Objects.requireNonNull(extractorRegistry, "extractorRegistry");
    this.mappingRegistry = Objects.requireNonNull(mappingRegistry, "mappingRegistry");
  }

  @Override
  public TransportProtocol protocol() {
    return TransportProtocol.RTSP;
  }

  @Override
  public Optional<ProtocolConnectionSetup> describe(IntegrationConnectionSchemaReference schema) {
    var extractors =
        extractorRegistry.extractorsFor(schema, ExtractorProtocol.RTSP).stream()
            .map(ConnectionExtractorCapability::descriptor)
            .toList();
    if (extractors.isEmpty()) {
      return Optional.empty();
    }
    var rule =
        mappingRegistry
            .findMapperOnly(schema, TransportProtocol.RTSP)
            .orElseThrow(
                () -> ConnectionSetupFaults.registeredWithout("a RTSP mapping rule", schema));
    return Optional.of(
        new ProtocolConnectionSetup.Rtsp(
            extractors, rule, AllowedAccessModesPolicy.allowedModes(TransportProtocol.RTSP, rule)));
  }
}
