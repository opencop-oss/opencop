// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.map;

import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.ports.exception.ExceptionCode;
import org.springframework.http.HttpStatus;

public enum MapTileExceptionCodes implements ExceptionCode {
  BAD_URL("MAP-TILE-1000", "Bad url", HttpStatus.BAD_REQUEST),
  INVALID_WEATHER_KIND(
      "MAP-TILE-1001", "Weather tiles must use RASTER, WMS, or WMTS kind", HttpStatus.BAD_REQUEST),
  EXTENSION_REQUIRED(
      "MAP-TILE-1002", "Extension is required when type is WEATHER", HttpStatus.BAD_REQUEST),
  EXTENSION_NOT_ALLOWED(
      "MAP-TILE-1003", "Extension is only allowed when type is WEATHER", HttpStatus.BAD_REQUEST),
  KIND_CHANGE_REQUIRES_CONFIG(
      "MAP-TILE-1004", "Config is required when kind is changed", HttpStatus.BAD_REQUEST),
  PRIMARY_MUST_BE_BASEMAP(
      "MAP-TILE-1005", "Primary tile must be a basemap", HttpStatus.BAD_REQUEST),
  CONFIG_REQUIRED("MAP-TILE-1006", "Config is required", HttpStatus.BAD_REQUEST),
  WEATHER_FEATURE_REQUIRED(
      "MAP-TILE-1007", "Weather feature id is required", HttpStatus.BAD_REQUEST),
  WEATHER_FEATURE_URL_INVALID(
      "MAP-TILE-1008", "Weather feature url must be nonblank and valid", HttpStatus.BAD_REQUEST),
  DUPLICATE_WEATHER_FEATURE(
      "MAP-TILE-1009", "Weather feature ids must be unique within a tile", HttpStatus.BAD_REQUEST),
  PRIMARY_MUST_BE_ENABLED(
      "MAP-TILE-1010",
      "Primary tile must stay enabled; make another basemap primary first",
      HttpStatus.BAD_REQUEST),
  PRIMARY_MUST_BE_GLOBAL(
      "MAP-TILE-1011",
      "Primary tile must stay global; make another basemap primary first",
      HttpStatus.BAD_REQUEST),
  GLOBAL_SCOPE_REQUIRES_ADMIN(
      "MAP-TILE-1012", "Only an administrator can make a tile global", HttpStatus.FORBIDDEN),
  PRIMARY_CANNOT_BE_DELETED(
      "MAP-TILE-1013",
      "Primary tile cannot be deleted; no other basemap is eligible to replace it",
      HttpStatus.CONFLICT),
  PRIMARY_NOT_SET("MAP-TILE-1014", "No primary basemap is configured", HttpStatus.NOT_FOUND),
  DEFAULT_TILE_NOT_AVAILABLE(
      "MAP-TILE-1015", "Default tile is not available to this user", HttpStatus.BAD_REQUEST),
  ATTRIBUTION_TOO_LONG(
      "MAP-TILE-1016",
      "Attribution must be at most " + MapTile.MAX_ATTRIBUTION_LENGTH + " characters",
      HttpStatus.BAD_REQUEST),
  NAME_TAKEN("MAP-TILE-1017", "Another map tile already has this name", HttpStatus.CONFLICT);

  private final String code;
  private final String description;
  private final HttpStatus httpStatus;

  MapTileExceptionCodes(String code, String description, HttpStatus httpStatus) {
    this.code = code;
    this.description = description;
    this.httpStatus = httpStatus;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
