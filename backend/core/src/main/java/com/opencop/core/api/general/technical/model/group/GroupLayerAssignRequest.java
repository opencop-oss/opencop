// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import java.util.UUID;

public record GroupLayerAssignRequest(UUID layerId, LayerRole role) {}
