// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.runtimefeature;

import com.opencop.core.sdk.model.runtimefeature.RuntimeFeatureAudience;

public interface WatchboxTrackingResetTelemetry {

  /** Emits reset evidence only if the surrounding runtime-feature transaction commits. */
  void recordResetAfterCommit(RuntimeFeatureAudience audience, int activeConfigEpochsBumped);
}
