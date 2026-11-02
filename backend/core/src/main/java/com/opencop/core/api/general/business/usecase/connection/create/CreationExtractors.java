// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.create;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationDetails;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.business.model.integration.connection.input.ConnectionInput;
import com.opencop.core.sdk.business.registry.integration.connection.input.ConnectionExtractorRegistry;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.input.ExtractorProtocol;

/** Finds the extractor a creation names, whichever transport it creates. */
public final class CreationExtractors {

  private CreationExtractors() {}

  /** The extractor the creation names, once the integration registers it for the protocol. */
  public static <I extends ConnectionInput, M> ConnectionExtractorCapability<I, M> require(
      ConnectionExtractorRegistry extractors,
      ConnectionCreationDetails details,
      ExtractorProtocol<I, M> protocol) {
    var integration = details.integration();
    var extractorId = details.extractorId();
    return extractors
        .findById(integration, protocol, extractorId)
        .orElseThrow(
            () ->
                ConnectionManagementException.forField(
                    ConnectionExceptionCodes.EXTRACTOR_NOT_FOUND,
                    ConnectionCreationDetails.EXTRACTOR_FIELD,
                    integration.integrationId(),
                    integration.schemaVersion(),
                    protocol,
                    extractorId));
  }
}
