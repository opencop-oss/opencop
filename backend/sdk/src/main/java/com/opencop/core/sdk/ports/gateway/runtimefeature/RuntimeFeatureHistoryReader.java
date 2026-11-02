// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryPage;
import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureHistoryQuery;

public interface RuntimeFeatureHistoryReader {
  RuntimeFeatureHistoryPage findPage(RuntimeFeatureHistoryQuery query);
}
