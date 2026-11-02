// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.datasourcegroup;

import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyDataSourceGroupResponse(
    UUID id,
    String name,
    String description,
    DataSourceGroupRole containerRole,
    Instant createdAt,
    List<DataSourceSummary> dataSources) {}
