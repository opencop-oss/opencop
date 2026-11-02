// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import java.time.Duration;

/** Maintenance operations for source runtime observations. */
public interface SourceRuntimeObservationMaintenance {

  int deleteExpiredOlderThan(Duration retention);
}
