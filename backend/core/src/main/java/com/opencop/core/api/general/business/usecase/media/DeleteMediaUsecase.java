// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.DeleteMedia;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class DeleteMediaUsecase implements DeleteMedia {

  private static final Logger log = LoggerFactory.getLogger(DeleteMediaUsecase.class);

  private final MediaRepository mediaRepository;
  private final MediaStore mediaStore;

  public DeleteMediaUsecase(MediaRepository mediaRepository, MediaStore mediaStore) {
    this.mediaRepository = mediaRepository;
    this.mediaStore = mediaStore;
  }

  // media_file rows own metadata and attachment relationships; blob_key is a shared storage
  // reference. Merge can clone media rows to new ids while preserving blob_key, so deleting one DB
  // row must not delete the physical blob while another row still references the same key.
  @Override
  public void delete(UUID mediaId) {
    var record =
        mediaRepository
            .findById(mediaId)
            .orElseThrow(
                () ->
                    new GlobalOpenCopException(
                        "Media not found: " + mediaId, HttpStatus.NOT_FOUND));

    var thumbnails = mediaRepository.findThumbnailsOf(mediaId);
    var recordsDeletedByCascade = new ArrayList<MediaRecord>();
    recordsDeletedByCascade.add(record);
    recordsDeletedByCascade.addAll(thumbnails);
    var deletedIds = recordsDeletedByCascade.stream().map(MediaRecord::id).toList();
    var orphanedBlobKeys = orphanedBlobKeysAfterDelete(recordsDeletedByCascade, deletedIds);

    mediaRepository.deleteById(mediaId);

    for (var blobKey : orphanedBlobKeys) {
      try {
        mediaStore.delete(blobKey);
      } catch (Exception e) {
        log.warn(
            "Deleted media {}, but blob cleanup failed for {}: {}",
            mediaId,
            blobKey,
            e.getMessage());
      }
    }

    log.info("Media deleted: id={}, blobKey={}", mediaId, record.blobKey());
  }

  private List<String> orphanedBlobKeysAfterDelete(
      List<MediaRecord> recordsDeletedByCascade, List<UUID> deletedIds) {
    var orphanedBlobKeys = new LinkedHashSet<String>();
    for (var mediaRecord : recordsDeletedByCascade) {
      if (!mediaRepository.existsByBlobKeyExcludingIds(mediaRecord.blobKey(), deletedIds)) {
        orphanedBlobKeys.add(mediaRecord.blobKey());
      }
    }
    return orphanedBlobKeys.stream().toList();
  }
}
