// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import jakarta.annotation.Nullable;

/**
 * Normalized build identity; null means "unset". Values are normalized from environment-backed
 * properties by {@link BuildMetadataConfiguration}.
 */
public record BuildMetadata(
    @Nullable String appVersion,
    @Nullable String revision,
    @Nullable String branch,
    @Nullable String buildDate) {}
