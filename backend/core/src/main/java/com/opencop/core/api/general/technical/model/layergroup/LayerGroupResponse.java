// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import java.time.Instant;
import java.util.UUID;

public record LayerGroupResponse(
    UUID id,
    String name,
    String description,
    LayerGroupAccessScope accessScope,
    Instant createdAt) {}
