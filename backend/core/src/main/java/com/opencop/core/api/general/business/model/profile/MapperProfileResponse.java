// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.profile;

import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import java.time.Instant;
import java.util.UUID;

public record MapperProfileResponse(
    UUID id,
    String name,
    String description,
    Boolean editable,
    Integer version,
    DynamicMapper dynamicMapper,
    Boolean archived,
    MapperQualifier qualifier,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt) {}
