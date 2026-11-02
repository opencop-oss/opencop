// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import com.opencop.core.sdk.business.model.profile.MapperProfile;

/** Determines whether persisted mapper lifecycle state permits ingress execution. */
public final class IngressMapperAvailabilityPolicy {

  private IngressMapperAvailabilityPolicy() {}

  public static boolean isAvailable(MapperProfile mapper) {
    return !Boolean.TRUE.equals(mapper.archived());
  }
}
