// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.support.fixture;

import com.opencop.core.api.general.technical.gateway.tile.MapTileEntity;
import com.opencop.core.api.general.technical.gateway.tile.MapTileRepository;
import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.business.model.map.tile.RasterConfig;
import com.opencop.core.sdk.business.model.map.tile.TileConfig;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import com.opencop.core.sdk.business.model.map.tile.VectorConfig;
import com.opencop.core.sdk.business.model.map.tile.WeatherTileExtension;
import com.opencop.core.sdk.business.model.map.tile.WmtsConfig;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.weather.WeatherFeatureConfig;
import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.Set;
import java.util.UUID;

public final class MapTileFixtures {

  private final MapTileRepository mapTileRepository;

  public MapTileFixtures(MapTileRepository mapTileRepository) {
    this.mapTileRepository = mapTileRepository;
  }

  public void reset() {
    mapTileRepository.deleteAll();
  }

  public UUID persistMapTile(
      UUID id,
      boolean primary,
      String name,
      String description,
      String url,
      MapTileConfig config,
      String tokenRef) {
    return persistMapTile(id, primary, true, name, description, url, config, tokenRef);
  }

  public UUID persistMapTile(
      UUID id,
      boolean primary,
      boolean enabled,
      String name,
      String description,
      String url,
      MapTileConfig config,
      String tokenRef) {
    return persistMapTile(
        id, primary, enabled, MapTileAccessScope.GLOBAL, name, description, url, config, tokenRef);
  }

  /**
   * Tiles default to {@link MapTileAccessScope#GLOBAL}, matching what V43 backfills onto existing
   * rows. That is what most tests mean by "a tile that exists". Pass {@code PRIVATE} when the test
   * is about access control.
   */
  public UUID persistMapTile(
      UUID id,
      boolean primary,
      boolean enabled,
      MapTileAccessScope accessScope,
      String name,
      String description,
      String url,
      MapTileConfig config,
      String tokenRef) {
    var entity =
        new MapTileEntity(
            id, primary, enabled, name, description, url, config.kind().name(), config, tokenRef);
    entity.setAccessScope(accessScope);
    return mapTileRepository.saveAndFlush(entity).getId();
  }

  public UUID persistMapTile(
      UUID id,
      boolean primary,
      String name,
      String description,
      String url,
      TileConfig tile,
      String tokenRef) {
    return persistMapTile(id, primary, name, description, url, basemapConfig(tile), tokenRef);
  }

  public MapTileEntity load(UUID id) {
    return mapTileRepository.findById(id).orElseThrow();
  }

  public MapTileEntity loadPrimary() {
    return mapTileRepository.findByIsPrimaryTrue().orElseThrow();
  }

  public static MapTileCreate defaultRasterCreate() {
    return new MapTileCreate(
        "Operations raster",
        "https://tiles.example.com/{z}/{x}/{y}.png",
        "Operations base raster",
        "super-secret-token",
        basemapConfig(new RasterConfig(256, 0, 18)));
  }

  public static MapTileCreate alternateVectorCreate() {
    return new MapTileCreate(
        "Operations vector",
        "https://tiles.example.com/vector/{z}/{x}/{y}.pbf",
        "Operations vector overlay",
        null,
        basemapConfig(new VectorConfig(512, 1, 16)));
  }

  public static MapTileCreate defaultGibsWeatherCreate() {
    var tile =
        new WmtsConfig(
            "1.0.0",
            "IMERG_Precipitation_Rate_30min",
            "default",
            "image/png",
            "GoogleMapsCompatible_Level6",
            null);

    return new MapTileCreate(
        "NASA GIBS Precipitation",
        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png",
        "NASA GIBS IMERG precipitation layer",
        null,
        weatherConfig(
            tile,
            new WeatherTileExtension(
                Set.of(
                    new WeatherFeatureConfig(
                        "IMERG_Precipitation_Rate_30min",
                        "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate_30min/default/{Time}/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png")))));
  }

  public static MapTile expectedSanitized(
      UUID id, boolean primary, String name, String description, String url, MapTileConfig config) {
    return expectedSanitized(id, primary, true, name, description, url, config);
  }

  public static MapTile expectedSanitized(
      UUID id,
      boolean primary,
      boolean enabled,
      String name,
      String description,
      String url,
      MapTileConfig config) {
    return new MapTile(id, primary, enabled, name, description, url, config, null);
  }

  public static MapTileConfig basemapConfig(TileConfig tile) {
    return new MapTileConfig(tile);
  }

  public static MapTileConfig weatherConfig(TileConfig tile, WeatherTileExtension extension) {
    return new MapTileConfig(TileType.WEATHER, extension, tile);
  }
}
