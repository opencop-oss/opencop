// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.runtimefeature;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureAvailability;
import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureMetadata;
import com.opencop.core.api.general.business.policy.runtimefeature.RuntimeFeatureAudiencePolicy;
import com.opencop.core.api.general.ports.usecase.runtimefeature.GetRuntimeFeatureMetadata;
import com.opencop.core.sdk.ports.gateway.runtimefeature.RuntimeFeatureStateReader;
import java.util.Optional;
import java.util.UUID;

public class GetRuntimeFeatureMetadataUsecase implements GetRuntimeFeatureMetadata {

  private final RuntimeFeatureStateReader stateReader;
  private final RuntimeFeatureAudiencePolicy audiencePolicy;

  public GetRuntimeFeatureMetadataUsecase(
      RuntimeFeatureStateReader stateReader, RuntimeFeatureAudiencePolicy audiencePolicy) {
    this.stateReader = stateReader;
    this.audiencePolicy = audiencePolicy;
  }

  @Override
  public RuntimeFeatureMetadata getFor(UUID callerUserId, boolean includeAdminState) {
    var states = stateReader.findAll();
    var features =
        states.stream()
            .map(
                state ->
                    new RuntimeFeatureAvailability(
                        state.name(), audiencePolicy.isAvailable(state.audience(), callerUserId)))
            .toList();
    return new RuntimeFeatureMetadata(
        features, includeAdminState ? Optional.of(states) : Optional.empty());
  }
}
