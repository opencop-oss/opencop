// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.banner;

import java.time.Instant;

public record BannerPatch(
    String name,
    Boolean enabled,
    Instant startsAt,
    Instant endsAt,
    String color,
    String textColor,
    BannerFormat format,
    Boolean dismissible,
    String linkUrl,
    Integer priority) {}
