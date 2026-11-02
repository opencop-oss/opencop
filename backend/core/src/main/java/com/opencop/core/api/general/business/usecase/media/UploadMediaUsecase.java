// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.api.general.ports.gateway.media.MediaRepository;
import com.opencop.core.api.general.ports.usecase.media.UploadMedia;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaAttributes;
import com.opencop.core.sdk.model.media.MediaBlobKey;
import com.opencop.core.sdk.model.media.MediaContentType;
import com.opencop.core.sdk.model.media.MediaRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.ports.gateway.media.MediaStore;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class UploadMediaUsecase implements UploadMedia {

  private static final Logger log = LoggerFactory.getLogger(UploadMediaUsecase.class);

  private final MediaRepository mediaRepository;
  private final MediaStore mediaStore;

  public UploadMediaUsecase(MediaRepository mediaRepository, MediaStore mediaStore) {
    this.mediaRepository = mediaRepository;
    this.mediaStore = mediaStore;
  }

  @Override
  public MediaRecord upload(
      UUID userId,
      String originalFilename,
      String mimeType,
      long size,
      MediaAccess access,
      InputStream data,
      UUID thumbnailOf,
      MediaUsage usage) {

    // Reject thumbnail-of-thumbnail (no chains allowed). findByIdForUpdate acquires a row lock
    // (SELECT ... FOR UPDATE) so no concurrent transaction can modify the parent between our
    // check and the insert — replaces the need for a DB trigger.
    if (thumbnailOf != null) {
      var parent =
          mediaRepository
              .findByIdForUpdate(thumbnailOf)
              .orElseThrow(
                  () ->
                      new GlobalOpenCopException(
                          "Thumbnail parent not found: " + thumbnailOf, HttpStatus.BAD_REQUEST));
      if (parent.thumbnailOf() != null) {
        throw new GlobalOpenCopException(
            "Cannot create thumbnail of a thumbnail", HttpStatus.BAD_REQUEST);
      }
    }

    // Secondary size check — hardcoded per-usage limit. The primary limit is the Spring multipart
    // config (spring.servlet.multipart.max-file-size). This catches uploads that pass Spring's
    // global limit but exceed the per-usage limit.
    long maxBytes = maxBytesForUsage(usage);
    if (size > maxBytes) {
      log.warn(
          "Upload rejected: {} file size {} exceeds limit {}. "
              + "This is a hardcoded secondary limit; the primary limit is the Spring multipart config.",
          usage,
          size,
          maxBytes);
      throw new GlobalOpenCopException(
          "File size " + size + " exceeds limit " + maxBytes + " for " + usage,
          HttpStatus.BAD_REQUEST);
    }

    // Image-only validation: avatar, event detail, and layer icon uploads must be images.
    // Thumbnails must also be images (thumbnails are always images, even if the parent is not).
    var contentType = classifyContentType(mimeType);
    if (contentType != MediaContentType.IMAGE) {
      if (thumbnailOf != null) {
        log.warn("Thumbnail upload rejected: MIME {} is not an image", mimeType);
        throw new GlobalOpenCopException(
            "Thumbnails must be images, got: " + mimeType, HttpStatus.BAD_REQUEST);
      }
      if (usage == MediaUsage.AVATAR || usage == MediaUsage.EVENT_DETAIL) {
        log.warn("Upload rejected: {} requires image MIME, got {}", usage, mimeType);
        throw new GlobalOpenCopException(
            usage + " uploads must be images, got: " + mimeType, HttpStatus.BAD_REQUEST);
      }
    }

    // UUID v4 for now. Prefer v7 (time-ordered) when JDK ships UUID.ofEpochMillis() —
    // targeted for a future JDK release (JDK-8357251). Not yet in Java 25.
    var mediaId = UUID.randomUUID();
    var blobKey = MediaBlobKey.generate(mediaId, originalFilename);

    // Spool the upload stream to a temp file so each processing pass (dimensions, blob upload)
    // gets a fresh FileInputStream. This avoids BufferedInputStream.mark(MAX_VALUE) which can
    // buffer the entire file in heap.
    // Future optimization: return a pre-signed upload URL to the client so the file bypasses the
    // server entirely.
    Path tempFile;
    try {
      tempFile = Files.createTempFile("media-upload-", ".tmp");
      try (var out = Files.newOutputStream(tempFile)) {
        data.transferTo(out);
      }
    } catch (IOException e) {
      throw new GlobalOpenCopException(
          "Failed to spool upload to temp file: " + e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }

    try {
      MediaAttributes attributes = null;

      if (contentType == MediaContentType.IMAGE) {
        boolean isSvg = "image/svg+xml".equals(mimeType);
        if (!isSvg) {
          try (var dimStream = new FileInputStream(tempFile.toFile())) {
            var buffered = new java.io.BufferedInputStream(dimStream);
            var dims = ImageDimensions.read(buffered);
            if (dims != null) {
              attributes = new MediaAttributes.Image(dims[0], dims[1]);
            } else {
              // File claims image/* but ImageIO could not parse it as an image
              throw new GlobalOpenCopException(
                  "File claims " + mimeType + " but could not be parsed as an image",
                  HttpStatus.BAD_REQUEST);
            }
          }
        }
        // SVG is inherently scalable — null attributes are expected and valid.
      }

      // Blob upload
      try (var uploadStream = new FileInputStream(tempFile.toFile())) {
        mediaStore.upload(blobKey, uploadStream, size, mimeType, mediaId);
      }

      var record =
          new MediaRecord(
              mediaId,
              blobKey,
              originalFilename,
              contentType,
              size,
              access,
              userId,
              userId,
              Instant.now(),
              mimeType,
              attributes,
              thumbnailOf,
              usage);

      var saved = mediaRepository.save(record);
      log.info(
          "Media uploaded: id={}, filename={}, contentType={}, size={}, access={}, userId={}",
          saved.id(),
          originalFilename,
          contentType,
          size,
          access,
          userId);
      return saved;

    } catch (IOException e) {
      throw new GlobalOpenCopException(
          "IO error during upload processing: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    } finally {
      try {
        Files.deleteIfExists(tempFile);
      } catch (IOException e) {
        log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
      }
    }
  }

  private static long maxBytesForUsage(MediaUsage usage) {
    return switch (usage) {
      case AVATAR, LAYER_ICON -> 10L * 1_024 * 1_024; // 10MB
      case EVENT_DETAIL -> 50L * 1_024 * 1_024; // 50MB
    };
  }

  static MediaContentType classifyContentType(String mimeType) {
    if (mimeType == null) return MediaContentType.OTHER;
    if (mimeType.startsWith("image/")) return MediaContentType.IMAGE;
    if (mimeType.startsWith("video/")) return MediaContentType.VIDEO;
    return MediaContentType.OTHER;
  }
}
