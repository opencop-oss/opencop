// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RuntimeFeatureRecordResponse(
    String name,
    String consumerClass,
    RuntimeFeatureAudienceResponse audience,
    long revision,
    Instant updatedAt) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RuntimeFeatureAudienceResponse(String mode, List<UUID> userGroupIds) {

    public RuntimeFeatureAudienceResponse {
      userGroupIds = userGroupIds == null ? null : List.copyOf(userGroupIds);
    }
  }
}
