// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.mapping.ingress;

import java.util.List;
import java.util.Objects;

/** Admitted ingress mappers and the structured decisions for rejected contributions. */
public record IngressMapperAdmission(
    IngressMapperContributions contributions, List<IngressMapperContributionDecision> rejections) {

  public IngressMapperAdmission {
    Objects.requireNonNull(contributions, "contributions");
    rejections = List.copyOf(Objects.requireNonNull(rejections, "rejections"));
    if (rejections.stream().anyMatch(IngressMapperContributionDecision::admitted)) {
      throw new IllegalArgumentException("rejections must contain only rejected decisions");
    }
  }
}
