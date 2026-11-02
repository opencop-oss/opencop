// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.layer;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerAccessScope;

public record LayerPatchRequest(
    String name, LayerAccessScope accessScope, Boolean defaultVisible) {}
