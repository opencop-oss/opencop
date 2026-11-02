// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.integration.mapping;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.ports.gateway.mapping.egress.PayloadEgressMapper;
import java.util.List;

/** The egress mappers one integration contributes, with the integration that owns them. */
public interface EgressMappingContribution {

  Integration integration();

  List<PayloadEgressMapper> mappers();
}
