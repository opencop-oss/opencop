// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.lock.geo;

import java.time.Duration;
import java.util.UUID;

public interface GeoCacheHydrationLock {

  boolean tryAcquire(UUID sourceId, String owner, Duration ttl);

  void release(UUID sourceId, String owner);
}
