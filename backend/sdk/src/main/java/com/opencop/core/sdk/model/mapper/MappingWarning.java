// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper;

import java.util.List;
import java.util.UUID;

public record MappingWarning(
    String code, String message, List<UUID> ruleIds, Integer recordIndex) {}
