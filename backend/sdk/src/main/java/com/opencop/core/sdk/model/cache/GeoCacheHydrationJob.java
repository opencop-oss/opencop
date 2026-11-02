// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.cache;

import java.util.UUID;

public record GeoCacheHydrationJob(UUID jobId, UUID sourceId, Mode mode, Trigger trigger) {

  public enum Mode {
    LATEST_ACTIVE_ONLY
  }

  public enum Trigger {
    LAYER_CLONE,
    DATA_SOURCE_REFRESH,
    MANUAL
  }

  public static GeoCacheHydrationJob create(UUID sourceId, Mode mode, Trigger trigger) {
    return new GeoCacheHydrationJob(UUID.randomUUID(), sourceId, mode, trigger);
  }

  public static GeoCacheHydrationJob forSource(UUID sourceId, Trigger trigger) {
    return create(sourceId, Mode.LATEST_ACTIVE_ONLY, trigger);
  }

  public static GeoCacheHydrationJob forClonedLayer(UUID sourceId) {
    return forSource(sourceId, Trigger.LAYER_CLONE);
  }
}
