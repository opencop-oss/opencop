// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layer;

import com.opencop.core.sdk.model.layermerge.LayerMergeOperation;
import java.util.UUID;

public interface LayerMergeOperationRepository {

  void createPending(LayerMergeOperation operation);

  void markFailed(UUID targetLayerId, String failureReason);
}
