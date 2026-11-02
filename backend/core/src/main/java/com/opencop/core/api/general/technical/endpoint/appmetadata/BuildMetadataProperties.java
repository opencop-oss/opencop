// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.build")
public record BuildMetadataProperties(
    @Nullable String appVersion,
    @Nullable String revision,
    @Nullable String branch,
    @Nullable String date) {}
