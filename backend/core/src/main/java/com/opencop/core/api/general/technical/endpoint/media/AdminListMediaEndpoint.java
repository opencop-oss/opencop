// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.media;

import com.opencop.core.api.general.ports.usecase.media.ListMedia;
import com.opencop.core.api.general.ports.usecase.media.MediaListFilter;
import com.opencop.core.api.general.ports.usecase.media.MediaPage;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaUsage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminListMediaEndpoint {

  private final ListMedia usecase;

  AdminListMediaEndpoint(ListMedia usecase) {
    this.usecase = usecase;
  }

  /**
   * List all media with optional filters.
   *
   * <p>Usage filter semantics:
   *
   * <ul>
   *   <li>Omitted or {@code *} — no filter (all usage values)
   *   <li>Empty string ({@code ?usage=}) — "Generic" (records where usage IS NULL)
   *   <li>Enum value ({@code ?usage=AVATAR}) — filter by that value
   * </ul>
   */
  @PreAuthorize("hasRole('admin')")
  @GetMapping("/v1/admin/media")
  public ResponseEntity<MediaPage> list(
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size,
      @RequestParam(value = "contentType", required = false) MediaContentType contentType,
      @RequestParam(value = "usage", required = false) String usage,
      @RequestParam(value = "includeThumbnails", defaultValue = "true") boolean includeThumbnails) {
    var filter = parseFilter(contentType, usage, includeThumbnails);
    return ResponseEntity.ok(usecase.list(page, size, filter));
  }

  private static MediaListFilter parseFilter(
      MediaContentType contentType, String usage, boolean includeThumbnails) {
    if (usage == null || "*".equals(usage)) {
      return new MediaListFilter(contentType, null, false, includeThumbnails);
    }
    if (usage.isEmpty()) {
      return new MediaListFilter(contentType, null, true, includeThumbnails);
    }
    try {
      var usageEnum = MediaUsage.valueOf(usage.toUpperCase());
      return new MediaListFilter(contentType, usageEnum, false, includeThumbnails);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Invalid usage value: '"
              + usage
              + "'. Valid values: AVATAR, LAYER_ICON, EVENT_DETAIL (or empty for generic)");
    }
  }
}
