// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.policy.integration.mapping.ingress;

import com.opencop.core.sdk.business.model.profile.MapperProfile;

/**
 * Determines whether persisted catalog ownership permits use of an admitted contribution: only a
 * mapper an integration contributes is decoded by that integration's code.
 */
public final class IngressMapperCatalogOwnershipPolicy {

  private IngressMapperCatalogOwnershipPolicy() {}

  public static boolean canUseContribution(MapperProfile mapper) {
    return mapper != null && mapper.integrationId() != null;
  }
}
