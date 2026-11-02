// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import java.util.Optional;
import java.util.UUID;

public record UserPreferencePatch(
    Optional<String> username,
    Optional<String> locale,
    Optional<MeasurementSystem> measurementSystem,
    Optional<TemperatureUnit> temperatureUnit,
    Optional<String> timezone,
    Optional<ZoomBehavior> zoomBehavior,
    Optional<UUID> initialView,
    Optional<UUID> defaultTileId,
    Optional<UUID> avatarMediaId) {

  public UserPreferencePatch {
    username = username == null ? Optional.empty() : username;
    locale = locale == null ? Optional.empty() : locale;
    measurementSystem = measurementSystem == null ? Optional.empty() : measurementSystem;
    temperatureUnit = temperatureUnit == null ? Optional.empty() : temperatureUnit;
    timezone = timezone == null ? Optional.empty() : timezone;
    zoomBehavior = zoomBehavior == null ? Optional.empty() : zoomBehavior;
    initialView = initialView == null ? Optional.empty() : initialView;
    defaultTileId = defaultTileId == null ? Optional.empty() : defaultTileId;
    // avatarMediaId intentionally not normalized: null="don't touch",
    // Optional.empty()="set to null", Optional.of(id)="set to id"
  }

  public static UserPreferencePatch avatarOnly(UUID mediaId) {
    return new UserPreferencePatch(
        null, null, null, null, null, null, null, null, Optional.ofNullable(mediaId));
  }

  public UserPreferencePatch withoutAvatar() {
    return new UserPreferencePatch(
        username,
        locale,
        measurementSystem,
        temperatureUnit,
        timezone,
        zoomBehavior,
        initialView,
        defaultTileId,
        null);
  }
}
