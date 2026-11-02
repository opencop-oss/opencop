// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.usecase;

import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.api.event.business.policy.EventAuthorizationPolicy;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.api.watchbox.business.model.WatchboxDetection;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionDetail;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionFilter;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionPageRequest;
import com.opencop.core.api.watchbox.business.policy.WatchboxLayerAccessPolicy;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxConfigRepository;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxDetectionReader;
import com.opencop.core.api.watchbox.ports.usecase.ReadWatchboxDetections;
import com.opencop.core.api.watchbox.ports.usecase.WatchboxCaller;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rows inherit box visibility, matching live subscribe-time checks and uniform broadcast; rows have
 * no layer attribution to filter on. Every not-fully-visible case uses the box endpoint's same 404
 * to avoid an existence leak.
 */
public class ReadWatchboxDetectionsUsecase implements ReadWatchboxDetections {
  private static final Logger log = LoggerFactory.getLogger(ReadWatchboxDetectionsUsecase.class);
  private final WatchboxConfigRepository repository;
  private final WatchboxLayerAccessPolicy layerAccessPolicy;
  private final WatchboxDetectionReader reader;
  private final EventAuthorizationPolicy eventAuthorizationPolicy;
  private final PostGisService postGisService;

  public ReadWatchboxDetectionsUsecase(
      WatchboxConfigRepository repository,
      WatchboxLayerAccessPolicy layerAccessPolicy,
      WatchboxDetectionReader reader,
      EventAuthorizationPolicy eventAuthorizationPolicy,
      PostGisService postGisService) {
    this.repository = repository;
    this.layerAccessPolicy = layerAccessPolicy;
    this.reader = reader;
    this.eventAuthorizationPolicy = eventAuthorizationPolicy;
    this.postGisService = postGisService;
  }

  @Override
  @Transactional(readOnly = true)
  public PageResponse<WatchboxDetection> list(
      WatchboxDetectionFilter filter, WatchboxDetectionPageRequest page, WatchboxCaller caller) {
    if (filter.watchboxId() != null) {
      var config = repository.findActive(filter.watchboxId()).orElseThrow(this::notFound);
      layerAccessPolicy.assertVisible(config.layerIds(), caller);
      return reader.findPage(Set.of(config.id()), filter.kinds(), page);
    }
    var configs = repository.listActive();
    var layerIds =
        configs.stream().flatMap(config -> config.layerIds().stream()).collect(Collectors.toSet());
    var access = layerAccessPolicy.contextFor(layerIds, caller);
    var visibleIds =
        configs.stream()
            .filter(config -> access.decide(config.layerIds()).fullyVisible())
            .map(config -> config.id())
            .collect(Collectors.toSet());
    if (visibleIds.isEmpty()) {
      return new PageResponse<>(List.of(), page.page(), page.size(), 0, 0);
    }
    return reader.findPage(visibleIds, filter.kinds(), page);
  }

  @Override
  @Transactional(readOnly = true)
  public WatchboxDetectionDetail get(UUID detectionId, WatchboxCaller caller) {
    var detection = reader.findById(detectionId).orElseThrow(this::notFound);
    var config = repository.findActive(detection.watchboxId()).orElseThrow(this::notFound);
    layerAccessPolicy.assertVisible(config.layerIds(), caller);
    if (!eventAuthorizationPolicy.canViewSource(
        caller.userId(), caller.globalAdmin(), detection.sourceId())) {
      return new WatchboxDetectionDetail(detection, Optional.empty(), Optional.empty());
    }
    var triggering =
        readEmbed(
            detection,
            "triggering",
            () ->
                postGisService.findEventDetails(
                    detection.sourceId(), detection.objectId(), detection.eventId()));
    var latest =
        readEmbed(
            detection,
            "latest",
            () ->
                postGisService.findLatestEventDetails(detection.sourceId(), detection.objectId()));
    return new WatchboxDetectionDetail(detection, triggering, latest);
  }

  /**
   * An unreadable stored revision degrades that embed to empty, as the contract promises; the row
   * itself stays readable. Database failures are unchecked and still surface as errors.
   */
  private static Optional<EventDetails> readEmbed(
      WatchboxDetection detection, String which, EmbedRead read) {
    try {
      return read.get();
    } catch (PersistenceException e) {
      log.warn(
          "Detection {} {} event unreadable for source={} object={}: {}",
          detection.id(),
          which,
          detection.sourceId(),
          detection.objectId(),
          e.getMessage());
      return Optional.empty();
    }
  }

  @FunctionalInterface
  private interface EmbedRead {
    Optional<EventDetails> get() throws PersistenceException;
  }

  private WatchboxException notFound() {
    return new WatchboxException(
        WatchboxErrorCode.WATCHBOX_NOT_FOUND,
        "Watchbox not found",
        HttpStatus.NOT_FOUND,
        List.of(),
        List.of());
  }
}
