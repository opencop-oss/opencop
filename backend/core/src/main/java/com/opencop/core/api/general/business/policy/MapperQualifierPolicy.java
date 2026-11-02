// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;

/** What a mapper's direction lets it do. */
public final class MapperQualifierPolicy {

  private MapperQualifierPolicy() {}

  /**
   * Whether the mapper decodes payloads into features. Only an ingress mapper does, so only one can
   * back a connection's reads or a layer import; an egress mapper encodes features instead.
   */
  public static boolean decodes(MapperProfile mapper) {
    return mapper.qualifier() == MapperQualifier.INGRESS;
  }

  /**
   * Whether the mapper can be deleted. An egress mapper belongs to the integration contributing it,
   * never to a user, so it cannot.
   */
  public static boolean deletable(MapperProfile mapper) {
    return mapper.qualifier() != MapperQualifier.EGRESS;
  }
}
