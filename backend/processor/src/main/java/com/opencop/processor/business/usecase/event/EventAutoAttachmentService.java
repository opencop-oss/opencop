// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.usecase.event;

import com.github.benmanes.caffeine.cache.Cache;
import com.opencop.core.sdk.model.media.MediaAccess;
import com.opencop.core.sdk.model.media.MediaUrlRecord;
import com.opencop.core.sdk.model.media.MediaUsage;
import com.opencop.core.sdk.model.media.StreamKind;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.processor.ports.gateway.db.DataSourceTransportGateway;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Attaches the configured RTSP source URL to each event, with cached duplicate suppression. */
public class EventAutoAttachmentService {
  private static final Logger log = LoggerFactory.getLogger(EventAutoAttachmentService.class);
  private final DataSourceTransportGateway transportGateway;
  private final MediaUrlRepository mediaUrlRepository;
  private final EventAttachmentRepository eventAttachmentRepository;
  private final Cache<String, Optional<String>> sourceUrlCache;
  private final Cache<String, Boolean> attachedCache;

  public EventAutoAttachmentService(
      DataSourceTransportGateway transportGateway,
      MediaUrlRepository mediaUrlRepository,
      EventAttachmentRepository eventAttachmentRepository,
      Cache<String, Optional<String>> sourceUrlCache,
      Cache<String, Boolean> attachedCache) {
    this.transportGateway = transportGateway;
    this.mediaUrlRepository = mediaUrlRepository;
    this.eventAttachmentRepository = eventAttachmentRepository;
    this.sourceUrlCache = sourceUrlCache;
    this.attachedCache = attachedCache;
  }

  public void attachFromRecord(UUID sourceId, String objectId) {
    var sourceCacheKey = sourceId.toString();
    var cached = sourceUrlCache.getIfPresent(sourceCacheKey);
    Optional<String> urlOpt;
    if (cached != null) {
      urlOpt = cached;
    } else {
      urlOpt = transportGateway.getRtspUrl(sourceId);
      sourceUrlCache.put(sourceCacheKey, urlOpt);
    }

    if (urlOpt.isEmpty()) return;

    var objectCacheKey = sourceCacheKey + ":" + objectId;
    if (attachedCache.getIfPresent(objectCacheKey) != null) return;

    final String url = urlOpt.get();
    try {
      var attachmentId = deterministicId(sourceId, objectId, url);
      if (!eventAttachmentRepository.exists(sourceId, objectId, attachmentId)) {
        mediaUrlRepository.save(
            new MediaUrlRecord(
                attachmentId,
                url,
                StreamKind.RTSP,
                MediaAccess.SCOPED,
                null,
                null,
                null,
                MediaUsage.EVENT_DETAIL));
        eventAttachmentRepository.save(sourceId, objectId, attachmentId);
        log.info("Auto-attached URL to event: sourceId={}, objectId={}", sourceId, objectId);
      }
      attachedCache.put(objectCacheKey, Boolean.TRUE);
    } catch (Exception e) {
      log.warn(
          "Failed to auto-attach URL for sourceId={}, objectId={}: {}",
          sourceId,
          objectId,
          e.getMessage());
    }
  }

  private static UUID deterministicId(UUID sourceId, String objectId, String url) {
    return UUID.nameUUIDFromBytes(
        (sourceId + ":" + objectId + ":" + url).getBytes(StandardCharsets.UTF_8));
  }
}
