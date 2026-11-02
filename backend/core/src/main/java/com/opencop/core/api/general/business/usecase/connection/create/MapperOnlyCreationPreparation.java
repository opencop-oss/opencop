// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.business.policy.connection.ConnectionIntegrationReferencePolicy;
import com.opencop.core.api.general.business.policy.connection.access.ConnectionAccessPolicy;
import com.opencop.core.api.general.business.usecase.connection.integration.ConnectionIntegrationSelection;
import com.opencop.core.api.general.business.usecase.connection.mapping.ConnectionMapperSelection;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.business.registry.integration.connection.mapping.ConnectionMappingRegistry;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;
import org.springframework.stereotype.Component;

/**
 * Checks everything a connection on a transport that does not frame its stream selects, before
 * anything is provisioned or stored: the integration can be selected, declares a mapper rule for
 * the transport and permits the mapper, the mapper is a selectable ingress mapper, and the
 * extractor accepts the input. Transport rules, such as which access modes the transport allows,
 * stay with its caller.
 */
@Component
public class MapperOnlyCreationPreparation {

  private final ConnectionIntegrationSelection integrationSelection;
  private final ConnectionMappingRegistry mappingRegistry;
  private final ConnectionMapperSelection mapperSelection;
  private final ConnectionExtractorRegistry extractors;

  public MapperOnlyCreationPreparation(
      ConnectionIntegrationSelection integrationSelection,
      ConnectionMappingRegistry mappingRegistry,
      ConnectionMapperSelection mapperSelection,
      ConnectionExtractorRegistry extractors) {
    this.integrationSelection = integrationSelection;
    this.mappingRegistry = mappingRegistry;
    this.mapperSelection = mapperSelection;
    this.extractors = extractors;
  }

  /**
   * The connection's access and the material its extractor produced, once every selection is known
   * to be valid.
   *
   * @param mode the access mode, which its caller already allowed for the transport
   */
  public <M> Prepared<M> prepare(
      ConnectionCreationDetails details,
      AccessMode mode,
      ExtractorProtocol<ConnectionInput, M> protocol) {
    var integration = details.integration();
    var access = ConnectionAccessPolicy.resolveSelection(mode, details.ingressMapperId(), null);
    integrationSelection.requireSelectable(integration);
    var ingressMapperId = access.ingressMapperId().orElseThrow();
    var declaration =
        ConnectionIntegrationReferencePolicy.requireDeclaration(
            mappingRegistry.findMapperOnly(integration, protocol.protocol()),
            integration,
            protocol.protocol().name());
    mapperSelection.ingress(declaration, ingressMapperId);
    var extractor = CreationExtractors.require(extractors, details, protocol);
    var material = ConnectionExtractorInvocation.extract(integration, extractor, details.input());
    return new Prepared<>(access, material, extractor.descriptor());
  }

  /**
   * What a creation selected, once valid.
   *
   * @param access the access mode and the mapper it reads with
   * @param material what the extractor produced from the request's input
   * @param extractor the descriptor of the extractor that read the input
   */
  public record Prepared<M>(ConnectionAccess access, M material, ExtractorDescriptor extractor) {}
}
