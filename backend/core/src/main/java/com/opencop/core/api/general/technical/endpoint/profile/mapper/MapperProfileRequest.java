// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import java.time.Instant;
import java.util.UUID;

/** HTTP request fields accepted for a user-authored mapper profile. */
public record MapperProfileRequest(
    UUID id,
    String name,
    String description,
    Boolean editable,
    Integer version,
    DynamicMapper dynamicMapper,
    Boolean archived,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy,
    Instant lastEventAt) {

  MapperProfile toBusinessModel() {
    return MapperProfile.builder()
        .withId(id)
        .withName(name)
        .withDescription(description)
        .withEditable(editable)
        .withVersion(version)
        .withDynamicMapper(dynamicMapper)
        .withArchived(archived)
        .withCreatedAt(createdAt)
        .withCreatedBy(createdBy)
        .withUpdatedAt(updatedAt)
        .withUpdatedBy(updatedBy)
        .withLastEventAt(lastEventAt)
        .build();
  }
}
