// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.egress;

import java.util.List;
import java.util.Objects;

/** Admitted egress mappers and the structured decisions for rejected contributions. */
public record EgressMapperAdmission(
    EgressMapperContributions contributions, List<EgressMapperContributionDecision> rejections) {

  public EgressMapperAdmission {
    Objects.requireNonNull(contributions, "contributions");
    rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
    if (rejections.stream().anyMatch(EgressMapperContributionDecision::admitted)) {
      throw new IllegalArgumentException("rejections must contain only rejected decisions");
    }
  }
}
