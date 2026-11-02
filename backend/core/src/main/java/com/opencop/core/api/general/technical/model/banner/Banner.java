// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.banner;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Banner(
    UUID id,
    String name,
    BannerKind kind,
    boolean enabled,
    Instant startsAt,
    Instant endsAt,
    String color,
    String textColor,
    BannerFormat format,
    boolean dismissible,
    String linkUrl,
    int priority,
    Instant createdAt,
    Instant updatedAt,
    List<BannerTranslation> translations) {}
