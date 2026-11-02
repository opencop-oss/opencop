// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import java.util.Optional;
import java.util.UUID;

/** Reads fresh source runtime observations. */
public interface SourceRuntimeObservationReader {

  Optional<RuntimeStatus> findFresh(UUID sourceId);

  Optional<RuntimeStatus> findFreshForInstance(UUID sourceId, String observedInstanceId);
}
