// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.layergroup;

import com.opencop.core.sdk.technical.gateway.db.iam.layergroup.LayerGroupAccessScope;
import jakarta.annotation.Nullable;

public record LayerGroupPatchRequest(
    @Nullable String name,
    @Nullable String description,
    @Nullable LayerGroupAccessScope accessScope) {}
