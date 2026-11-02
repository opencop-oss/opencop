// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.MediaNotFoundException;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.ports.usecase.media.ThumbnailRef;
import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class RetrieveMediaUsecase implements RetrieveMedia {

  private static final Logger log = LoggerFactory.getLogger(RetrieveMediaUsecase.class);

  private final MediaRepository mediaRepository;
  private final MediaStore mediaStore;

  public RetrieveMediaUsecase(MediaRepository mediaRepository, MediaStore mediaStore) {
    this.mediaRepository = mediaRepository;
    this.mediaStore = mediaStore;
  }

  @Override
  public MediaView getAsUser(UUID mediaId, UUID requestingUserId) throws MediaNotFoundException {
    return get(mediaId, requestingUserId, false);
  }

  @Override
  public MediaView getWithoutAccessCheck(UUID mediaId) throws MediaNotFoundException {
    return get(mediaId, null, true);
  }

  private MediaView get(UUID mediaId, UUID requestingUserId, boolean bypassAccessChecks)
      throws MediaNotFoundException {
    var mediaRecord =
        mediaRepository.findById(mediaId).orElseThrow(() -> new MediaNotFoundException(mediaId));

    if (!bypassAccessChecks) {
      switch (mediaRecord.access()) {
        case LOGGED_IN -> {
          // Access granted to any authenticated user.
        }
        case SCOPED -> {
          log.warn(
              "Access denied: user {} attempted to access SCOPED media {}",
              requestingUserId,
              mediaId);
          throw new GlobalOpenCopException("Access denied", HttpStatus.FORBIDDEN);
        }
      }
    }

    var url = mediaStore.resolve(mediaRecord.blobKey());

    // Resolve thumbnails for originals (thumbnailOf == null). Thumbnails themselves get an empty
    // list.
    List<ThumbnailRef> thumbnails;
    if (mediaRecord.thumbnailOf() == null) {
      thumbnails =
          mediaRepository.findThumbnailsOf(mediaId).stream()
              .map(
                  t -> {
                    Integer w = null;
                    Integer h = null;
                    if (t.attributes()
                        instanceof MediaAttributes.Image(Integer widthPx, Integer heightPx)) {
                      w = widthPx;
                      h = heightPx;
                    }
                    return new ThumbnailRef(
                        t.id(), mediaStore.resolve(t.blobKey()), w, h, t.mimeType());
                  })
              .toList();
    } else {
      thumbnails = List.of();
    }

    return new MediaView(mediaRecord, url, thumbnails);
  }
}
