// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.user;

import com.opencop.core.api.general.business.model.map.MapCapture;
import com.opencop.core.api.general.ports.usecase.media.MediaView;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import java.util.UUID;

public record UserPreference(
    String username,
    String locale,
    MeasurementSystem measurementSystem,
    TemperatureUnit temperatureUnit,
    String timezone,
    ZoomBehavior zoomBehavior,
    UUID avatarMediaId,
    MediaView avatar,
    MapCapture initialView,
    MapTile defaultTile) {

  /** Used to drop a default tile the caller may no longer see. */
  public UserPreference withoutDefaultTile() {
    return new UserPreference(
        username,
        locale,
        measurementSystem,
        temperatureUnit,
        timezone,
        zoomBehavior,
        avatarMediaId,
        avatar,
        initialView,
        null);
  }

  public UserPreference withAvatar(MediaView avatar) {
    return new UserPreference(
        username,
        locale,
        measurementSystem,
        temperatureUnit,
        timezone,
        zoomBehavior,
        avatarMediaId,
        avatar,
        initialView,
        defaultTile);
  }
}
