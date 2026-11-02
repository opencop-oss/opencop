// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Optional;
import java.util.UUID;

public interface LayerGroupAuthorization {

  Optional<LayerGroupRole> getEffectiveContainerRole(UUID layerGroupId, OpenCopPrincipal principal);
}
