// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.runtimefeature;

import com.opencop.core.api.general.business.model.runtimefeature.RuntimeFeatureMetadata;
import java.util.UUID;

public interface GetRuntimeFeatureMetadata {
  RuntimeFeatureMetadata getFor(UUID callerUserId, boolean includeAdminState);
}
