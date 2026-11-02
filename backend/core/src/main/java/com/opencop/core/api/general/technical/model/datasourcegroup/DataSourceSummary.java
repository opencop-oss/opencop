// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;

public record DataSourceSummary(UUID id, String name, ServerRole role) {}
