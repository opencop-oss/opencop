// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.geocoding;

import java.util.UUID;

/** Paces each user's geocoding searches so one client cannot flood the provider. */
public interface GeocodingRateLimiter {

  /**
   * Spends one search from the user's allowance.
   *
   * @return {@code false} when the user has used up their allowance for now
   */
  boolean tryAcquire(UUID internalUserId);
}
