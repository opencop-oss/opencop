// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.media;

import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves URL attachments that MediaMTX converts to HLS, by the scheme of the stored URL. Adding a
 * convertible scheme is one more row in {@link #SOURCE_TYPES_BY_PREFIX}.
 */
@Component
public class UrlStreamableAttachmentResolver implements StreamableAttachmentResolver {

  /** Matched literally: registration stores the scheme in lowercase. */
  private static final Map<String, StreamableAttachmentSourceType> SOURCE_TYPES_BY_PREFIX =
      Map.of(
          "rtsp://", StreamableAttachmentSourceType.RTSP_URL,
          "rtsps://", StreamableAttachmentSourceType.RTSP_URL,
          "srt://", StreamableAttachmentSourceType.SRT_URL);

  private final MediaUrlRepository mediaUrlRepository;

  public UrlStreamableAttachmentResolver(MediaUrlRepository mediaUrlRepository) {
    this.mediaUrlRepository = mediaUrlRepository;
  }

  @Override
  public Optional<StreamableAttachment> resolve(UUID attachmentId) {
    return mediaUrlRepository
        .findById(attachmentId)
        .map(MediaUrlRecord::url)
        .flatMap(url -> sourceTypeOf(url).map(type -> toAttachment(attachmentId, type, url)));
  }

  private static Optional<StreamableAttachmentSourceType> sourceTypeOf(String url) {
    if (url == null) return Optional.empty();
    return SOURCE_TYPES_BY_PREFIX.entrySet().stream()
        .filter(entry -> url.startsWith(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst();
  }

  private static StreamableAttachment toAttachment(
      UUID attachmentId, StreamableAttachmentSourceType sourceType, String url) {
    return new StreamableAttachment(
        attachmentId, AttachmentStreamPath.pathName(attachmentId), sourceType, url);
  }
}
