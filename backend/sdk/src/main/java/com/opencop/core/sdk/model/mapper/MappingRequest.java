// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;
import com.opencop.core.sdk.technical.mapper.MappingProperties;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappingRequest(
    MappingProperties properties, DynamicMapper profile, byte[] payloadBytes) {}
