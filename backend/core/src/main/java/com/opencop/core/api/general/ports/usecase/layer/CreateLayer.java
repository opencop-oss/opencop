// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.layer;

import com.opencop.core.sdk.business.model.iam.access.Layer;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Map;
import java.util.UUID;

/** Creates a layer and seeds who may work on it. */
public interface CreateLayer {

  /**
   * @param accessScope null for the default, which is private to its members
   * @param initialMembers who the layer starts with, beside the creator, who is always its owner
   * @return the layer as it was stored
   */
  Layer create(
      String name,
      LayerAccessScope accessScope,
      Map<UUID, LayerRole> initialMembers,
      OpenCopPrincipal caller);
}
