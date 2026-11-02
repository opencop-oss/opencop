// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.sdk.model.mapper.profile.DynamicMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreviewMappingRequest(DynamicMapper profile, byte[] payloadBytes) {}
