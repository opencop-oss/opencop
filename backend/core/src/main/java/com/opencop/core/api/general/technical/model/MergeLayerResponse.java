// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model;

import com.opencop.core.sdk.business.model.iam.access.Layer;
import java.util.List;
import java.util.UUID;

/** Response for asynchronous layer merge admission. */
public record MergeLayerResponse(Layer targetLayer, List<UUID> sourceLayerIds) {}
