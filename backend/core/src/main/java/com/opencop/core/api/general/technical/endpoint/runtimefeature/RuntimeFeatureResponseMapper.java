// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureReconciliation;
import com.opencop.core.api.general.technical.endpoint.runtimefeature.RuntimeFeatureRecordResponse.RuntimeFeatureAudienceResponse;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;

public final class RuntimeFeatureResponseMapper {

  private RuntimeFeatureResponseMapper() {}

  public static RuntimeFeatureReconcileResponse toReconcileResponse(
      RuntimeFeatureReconciliation reconciliation) {
    return new RuntimeFeatureReconcileResponse(
        reconciliation.created().stream().map(RuntimeFeatureResponseMapper::toRecord).toList(),
        reconciliation.alreadyRegistered().stream()
            .map(RuntimeFeatureResponseMapper::toRecord)
            .toList(),
        reconciliation.rejected().stream()
            .map(
                rejection ->
                    new RuntimeFeatureReconcileResponse.Rejection(
                        rejection.name(), rejection.code().name(), rejection.message()))
            .toList(),
        reconciliation.complete());
  }

  public static RuntimeFeatureRecordResponse toRecord(RuntimeFeatureState state) {
    return new RuntimeFeatureRecordResponse(
        state.name(),
        state.consumerClass().name(),
        toAudience(state.audience()),
        state.revision(),
        state.updatedAt());
  }

  public static RuntimeFeatureAudienceResponse toAudience(RuntimeFeatureAudience audience) {
    return switch (audience) {
      case RuntimeFeatureAudience.None _ -> new RuntimeFeatureAudienceResponse("NONE", null);
      case RuntimeFeatureAudience.All _ -> new RuntimeFeatureAudienceResponse("ALL", null);
      case RuntimeFeatureAudience.UserGroups(var userGroupIds) ->
          new RuntimeFeatureAudienceResponse("USER_GROUPS", userGroupIds.stream().toList());
    };
  }
}
