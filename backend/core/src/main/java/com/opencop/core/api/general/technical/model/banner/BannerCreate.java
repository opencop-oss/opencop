// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.banner;

import java.time.Instant;
import java.util.List;

public record BannerCreate(
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
    List<BannerTranslationCreate> translations) {}
