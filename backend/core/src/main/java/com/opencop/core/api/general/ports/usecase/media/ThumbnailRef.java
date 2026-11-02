// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import java.util.UUID;

public record ThumbnailRef(
    UUID id, String url, Integer widthPx, Integer heightPx, String mimeType) {}
