// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.runtimefeature;

import static com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureErrorCode.RUNTIME_FEATURE_REQUIRED_ROW_MISSING;

import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.business.runtimefeature.RuntimeFeatureException;
import com.opencop.core.api.general.ports.usecase.runtimefeature.CheckRuntimeFeatureAvailability;
import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureConsumerClass;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureStateReader;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CheckRuntimeFeatureAvailabilityUsecase implements CheckRuntimeFeatureAvailability {

  private final RuntimeFeatureStateReader stateReader;
  private final RuntimeFeatureAudiencePolicy audiencePolicy;

  public CheckRuntimeFeatureAvailabilityUsecase(
      RuntimeFeatureStateReader stateReader, RuntimeFeatureAudiencePolicy audiencePolicy) {
    this.stateReader = stateReader;
    this.audiencePolicy = audiencePolicy;
  }

  @Override
  public boolean isAvailable(BackendRuntimeFeature feature, UUID callerUserId) {
    var state =
        stateReader
            .findByName(feature.wireName())
            .filter(
                candidate ->
                    candidate.consumerClass() == RuntimeFeatureConsumerClass.BACKEND_ENFORCED)
            .orElseThrow(
                () ->
                    new RuntimeFeatureException(
                        RUNTIME_FEATURE_REQUIRED_ROW_MISSING,
                        "Required backend runtime feature row is missing or invalid: "
                            + feature.wireName(),
                        HttpStatus.INTERNAL_SERVER_ERROR));
    return audiencePolicy.isAvailable(state.audience(), callerUserId);
  }
}
