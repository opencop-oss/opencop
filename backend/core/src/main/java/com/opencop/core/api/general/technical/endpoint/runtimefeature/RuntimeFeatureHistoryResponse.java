// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureRecordResponse.RuntimeFeatureAudienceResponse;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record RuntimeFeatureHistoryResponse(
    long id,
    String featureName,
    RuntimeFeatureConsumerClass consumerClass,
    RuntimeFeatureHistoryEntry.Operation operation,
    RuntimeFeatureAudienceResponse beforeAudience,
    RuntimeFeatureAudienceResponse afterAudience,
    Long revisionBefore,
    long revisionAfter,
    Instant occurredAt,
    RuntimeFeatureHistoryActorResponse actor) {

  static RuntimeFeatureHistoryResponse from(RuntimeFeatureHistoryEntry entry) {
    return new RuntimeFeatureHistoryResponse(
        entry.id(),
        entry.featureName(),
        entry.consumerClass(),
        entry.operation(),
        entry.beforeAudience() == null
            ? null
            : RuntimeFeatureResponseMapper.toAudience(entry.beforeAudience()),
        RuntimeFeatureResponseMapper.toAudience(entry.afterAudience()),
        entry.revisionBefore(),
        entry.revisionAfter(),
        entry.occurredAt(),
        new RuntimeFeatureHistoryActorResponse(
            entry.actor().userId(),
            entry.actor().fullName(),
            entry.actor().email(),
            entry.actor().globalAdmin()));
  }

  @Schema(name = "RuntimeFeatureHistoryActorResponse")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RuntimeFeatureHistoryActorResponse(
      UUID userId, String fullName, String email, boolean globalAdmin) {}
}
