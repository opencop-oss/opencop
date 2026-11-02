// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.business.factory;

import com.opencop.core.api.watchbox.business.model.WatchboxConfig;
import com.opencop.core.api.watchbox.business.service.WatchboxException;
import com.opencop.core.sdk.model.watchbox.WatchboxConfigurationLimits;
import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxErrorCode;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxPatchRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geojson.Polygon;
import org.springframework.http.HttpStatus;

public class WatchboxConfigFactory {

  public WatchboxConfig create(
      String name,
      String description,
      boolean enabled,
      WatchboxDirection direction,
      Polygon polygon,
      WatchboxFilter filter,
      Set<UUID> layerIds) {
    return new WatchboxConfig(
        null, name, description, enabled, direction, polygon, filter, layerIds, 1L, null, null);
  }

  public WatchboxConfig patch(
      WatchboxConfig existing,
      WatchboxPatchRequest request,
      Set<UUID> layerIds,
      Polygon polygon,
      WatchboxFilter filter) {
    return new WatchboxConfig(
        existing.id(),
        request.name() == null ? existing.name() : normalizeName(request.name()),
        request.description() == null
            ? existing.description()
            : normalizeDescription(request.description()),
        request.enabled() == null ? existing.enabled() : request.enabled(),
        request.direction() == null ? existing.direction() : request.direction(),
        polygon,
        filter,
        layerIds,
        existing.configEpoch(),
        existing.createdAt(),
        existing.updatedAt());
  }

  public WatchboxConfig withConfigEpoch(WatchboxConfig config, long configEpoch) {
    return new WatchboxConfig(
        config.id(),
        config.name(),
        config.description(),
        config.enabled(),
        config.direction(),
        config.polygon(),
        config.filter(),
        config.layerIds(),
        configEpoch,
        config.createdAt(),
        config.updatedAt());
  }

  public String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_NAME,
          "Watchbox name is required",
          HttpStatus.BAD_REQUEST,
          List.of("name"));
    }
    String trimmed = name.trim();
    if (codePointCount(trimmed) > WatchboxConfigurationLimits.MAX_NAME_CODE_POINTS) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_NAME,
          "Watchbox name must not exceed 255 characters",
          HttpStatus.BAD_REQUEST,
          List.of("name"));
    }
    return trimmed;
  }

  public String normalizeDescription(String description) {
    if (description == null) {
      return null;
    }
    String trimmed = description.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (codePointCount(trimmed) > WatchboxConfigurationLimits.MAX_DESCRIPTION_CODE_POINTS) {
      throw new WatchboxException(
          WatchboxErrorCode.WATCHBOX_INVALID_DESCRIPTION,
          "Watchbox description must not exceed 4000 characters",
          HttpStatus.BAD_REQUEST,
          List.of("description"));
    }
    return trimmed;
  }

  private static int codePointCount(String value) {
    return value.codePointCount(0, value.length());
  }

  public WatchboxDirection defaultDirection(WatchboxDirection direction) {
    return direction == null ? WatchboxDirection.BOTH : direction;
  }
}
