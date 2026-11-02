// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.service;

import com.opencop.core.api.watchbox.ports.usecase.WatchboxDevStatusOverrides;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxSourceStatusResponse;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusOverrideRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;

public class WatchboxDevStatusOverrideService implements WatchboxDevStatusOverrides {

  private final ConcurrentMap<UUID, WatchboxStatusOverrideRequest> overrides =
      new ConcurrentHashMap<>();

  public WatchboxStatusOverrideRequest set(UUID watchboxId, WatchboxStatusOverrideRequest request) {
    WatchboxStatusOverrideRequest normalized = validate(request);
    overrides.put(watchboxId, normalized);
    return normalized;
  }

  public Optional<WatchboxStatusOverrideRequest> get(UUID watchboxId) {
    return Optional.ofNullable(overrides.get(watchboxId));
  }

  public void clear(UUID watchboxId) {
    overrides.remove(watchboxId);
  }

  public WatchboxResponse apply(WatchboxResponse response, boolean includeSourceStatuses) {
    WatchboxStatusOverrideRequest override = overrides.get(response.id());
    if (override == null) {
      return response;
    }
    return new WatchboxResponse(
        response.id(),
        response.name(),
        response.description(),
        response.enabled(),
        response.direction(),
        response.polygon(),
        response.filter(),
        response.layerIds(),
        response.configEpoch(),
        override.alertingStatus(),
        override.alertingReasons(),
        includeSourceStatuses ? sourceStatuses(override) : List.of(),
        response.capabilities(),
        response.createdAt(),
        response.updatedAt());
  }

  private WatchboxStatusOverrideRequest validate(WatchboxStatusOverrideRequest request) {
    if (request == null || request.alertingStatus() == null) {
      throw invalid("alertingStatus");
    }
    if (request.alertingReasons() == null) {
      throw invalid("alertingReasons");
    }
    for (int i = 0; i < request.sourceStatuses().size(); i++) {
      var status = request.sourceStatuses().get(i);
      String field = "sourceStatuses[" + i + "]";
      if (status == null) {
        throw invalid(field);
      }
      if (status.sourceId() == null) {
        throw invalid(field + ".sourceId");
      }
      if (status.status() == null) {
        throw invalid(field + ".status");
      }
      if (status.reason() == null) {
        throw invalid(field + ".reason");
      }
    }
    return request;
  }

  private List<WatchboxSourceStatusResponse> sourceStatuses(
      WatchboxStatusOverrideRequest override) {
    return override.sourceStatuses().stream()
        .map(
            status ->
                new WatchboxSourceStatusResponse(
                    status.sourceId(),
                    status.sourceName(),
                    status.status(),
                    status.reason(),
                    status.sinceAt(),
                    status.observedCount(),
                    status.cap(),
                    status.updatedAt()))
        .toList();
  }

  private static WatchboxException invalid(String field) {
    return new WatchboxException(
        WatchboxErrorCode.WATCHBOX_INVALID_FILTER,
        "Watchbox status override is invalid",
        HttpStatus.BAD_REQUEST,
        List.of(field));
  }
}
