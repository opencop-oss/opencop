// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.model.banner;

import java.time.Instant;
import java.util.UUID;

public record BannerTranslation(
    UUID id, UUID bannerId, String locale, String content, Instant createdAt, Instant updatedAt) {}
