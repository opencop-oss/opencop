// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.CachedIcon;
import com.opencop.core.api.general.ports.usecase.media.ServeLayerIcon;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;

public class ServeLayerIconUsecase implements ServeLayerIcon {

  private static final Logger log = LoggerFactory.getLogger(ServeLayerIconUsecase.class);

  private final MediaRepository mediaRepository;
  private final MediaStore mediaStore;

  public ServeLayerIconUsecase(MediaRepository mediaRepository, MediaStore mediaStore) {
    this.mediaRepository = mediaRepository;
    this.mediaStore = mediaStore;
  }

  @Override
  @Cacheable(value = "layerIcons", key = "#mediaId.toString()")
  public CachedIcon get(UUID mediaId) {
    var record =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Media not found: " + mediaId, HttpStatus.NOT_FOUND));

    var bytes = mediaStore.download(record.blobKey());
    var mimeType = record.mimeType() != null ? record.mimeType() : "application/octet-stream";
    log.debug("Downloaded icon for media {}: {} bytes, type={}", mediaId, bytes.length, mimeType);
    return new CachedIcon(bytes, mimeType);
  }
}
