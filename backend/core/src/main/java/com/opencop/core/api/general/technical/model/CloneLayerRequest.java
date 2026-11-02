// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;
import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.Map;
import java.util.UUID;

public record CloneLayerRequest(
    String name,
    Map<UUID, LayerRole> users,
    // Added for future scope support
    LayerAccessScope accessScope,
    Boolean copyRemarks,
    Boolean copyMedia) {}
