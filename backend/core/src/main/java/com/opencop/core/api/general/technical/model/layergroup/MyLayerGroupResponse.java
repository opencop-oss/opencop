// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyLayerGroupResponse(
    UUID id,
    String name,
    String description,
    LayerGroupRole containerRole,
    LayerGroupAccessScope accessScope,
    Instant createdAt,
    List<LayerSummary> layers) {}
