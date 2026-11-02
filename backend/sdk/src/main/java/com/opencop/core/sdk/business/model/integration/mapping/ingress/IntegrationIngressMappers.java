// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.ingress;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.ports.integration.mapping.IngressMappingContribution;
import java.util.List;
import java.util.Objects;
import org.geojson.Feature;

/**
 * The ingress mappers one integration contributes. The owning integration travels with its mappers
 * as data, so a host that decodes payloads without registering integration identities can still
 * load them.
 */
public record IntegrationIngressMappers(
    Integration integration, List<PayloadIngressMapper<? extends Feature>> mappers)
    implements IngressMappingContribution {

  public IntegrationIngressMappers {
    Objects.requireNonNull(integration, "integration");
    mappers = List.copyOf(Objects.requireNonNull(mappers, "mappers"));
  }
}
