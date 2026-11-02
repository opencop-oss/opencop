// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import java.util.UUID;

public interface SourceRestartEpochWriter {

  long incrementRestartEpoch(UUID sourceId);
}
