// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.egress;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import com.opencop.core.sdk.ports.integration.mapping.EgressMappingContribution;
import java.util.List;
import java.util.Objects;

/** The egress mappers one integration contributes, with the integration that owns them. */
public record IntegrationEgressMappers(Integration integration, List<PayloadEgressMapper> mappers)
    implements EgressMappingContribution {

  public IntegrationEgressMappers {
    Objects.requireNonNull(integration, "integration");
    mappers = List.copyOf(Objects.requireNonNull(mappers, "mappers"));
  }
}
