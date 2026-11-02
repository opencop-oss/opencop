// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.group;

import jakarta.annotation.Nullable;

public record GroupPatchRequest(@Nullable String name, @Nullable String description) {}
