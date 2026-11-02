// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import com.opencop.core.sdk.model.media.MediaRecord;
import java.util.List;

public record MediaView(MediaRecord record, String url, List<ThumbnailRef> thumbnails) {}
