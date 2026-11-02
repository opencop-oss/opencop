// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaUsage;

/**
 * Filters for the admin media list endpoint.
 *
 * @param contentType Filter by content type (IMAGE, VIDEO, OTHER). Null = no filter.
 * @param usage Filter by specific usage value. Null = no filter (unless usageIsNull).
 * @param usageIsNull When true, filter for records where usage IS NULL ("Generic"). Only meaningful
 *     when usage is null.
 * @param includeThumbnails When false, exclude records where thumbnailOf IS NOT NULL.
 */
public record MediaListFilter(
    MediaContentType contentType,
    MediaUsage usage,
    boolean usageIsNull,
    boolean includeThumbnails) {

  public static MediaListFilter unfiltered() {
    return new MediaListFilter(null, null, false, true);
  }
}
