// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RuntimeFeatureMetadata(
    List<RuntimeFeatureAvailability> features,
    Optional<List<RuntimeFeatureState>> adminRuntimeFeatures) {
  public RuntimeFeatureMetadata {
    features = List.copyOf(features);
    Objects.requireNonNull(adminRuntimeFeatures, "adminRuntimeFeatures is required");
    adminRuntimeFeatures = adminRuntimeFeatures.map(List::copyOf);
  }
}
