// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SourceRestartEpochReader {

  Map<UUID, Long> findRestartEpochs(Set<UUID> sourceIds);
}
