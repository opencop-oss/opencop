// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Generates blob storage keys for media files.
 *
 * <p>Delegates to {@link BlobKeyGenerator} with {@link BlobKeyPrefix#MEDIA}.
 */
public final class MediaBlobKey {

  private MediaBlobKey() {}

  public static String generate(UUID mediaId, String originalFilename) {
    return BlobKeyGenerator.generate(BlobKeyPrefix.MEDIA, mediaId, originalFilename);
  }

  public static String generate(UUID mediaId, String originalFilename, LocalDate date) {
    return BlobKeyGenerator.generate(BlobKeyPrefix.MEDIA, mediaId, originalFilename, date);
  }
}
