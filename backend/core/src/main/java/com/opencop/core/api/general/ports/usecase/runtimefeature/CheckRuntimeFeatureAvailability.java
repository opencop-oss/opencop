// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.BackendRuntimeFeature;
import java.util.UUID;

public interface CheckRuntimeFeatureAvailability {
  boolean isAvailable(BackendRuntimeFeature feature, UUID callerUserId);
}
