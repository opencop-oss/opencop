// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.tile;

import com.opencop.core.sdk.business.model.map.tile.MapTile;
import com.opencop.core.sdk.business.model.map.tile.MapTileConfig;
import com.opencop.core.sdk.business.model.map.tile.RasterConfig;
import com.opencop.core.sdk.business.model.map.tile.RasterDemConfig;
import com.opencop.core.sdk.business.model.map.tile.TerrainMeshConfig;
import com.opencop.core.sdk.business.model.map.tile.Tile3dConfig;
import com.opencop.core.sdk.business.model.map.tile.TileConfig;
import com.opencop.core.sdk.business.model.map.tile.TileConfigExtension;
import com.opencop.core.sdk.business.model.map.tile.TileKind;
import com.opencop.core.sdk.business.model.map.tile.TileType;
import com.opencop.core.sdk.business.model.map.tile.VectorConfig;
import com.opencop.core.sdk.business.model.map.tile.WeatherTileExtension;
import com.opencop.core.sdk.business.model.map.tile.WmsConfig;
import com.opencop.core.sdk.business.model.map.tile.WmtsConfig;
import com.opencop.core.sdk.business.model.map.tile.create.MapTileCreate;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTileConfigPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.MapTilePatch;
import com.opencop.core.sdk.business.model.map.tile.patch.TileConfigExtensionPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.TileConfigPatch;
import com.opencop.core.sdk.business.model.map.tile.patch.WeatherTileExtensionPatch;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public final class MapTileMapper {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MapTileMapper() {}

  public static MapTile toModel(MapTileEntity e) {
    if (e == null) return null;
    MapTileConfig config = e.getConfig();

    var model =
        new MapTile(
            e.getId(),
            e.isPrimary(),
            e.isEnabled(),
            e.getName(),
            e.getDescription(),
            e.getUrl(),
            config,
            e.getTokenRef());
    model.setAccessScope(e.getAccessScope());
    model.setAttribution(e.getAttribution());
    return model;
  }

  /** Returns a model safe for non-admin users (no token). */
  public static MapTile toSanitizedModel(MapTileEntity e) {
    if (e == null) return null;
    MapTileConfig config = e.getConfig();

    var model =
        new MapTile(
            e.getId(),
            e.isPrimary(),
            e.isEnabled(),
            e.getName(),
            e.getDescription(),
            e.getUrl(),
            config,
            null);
    model.setAccessScope(e.getAccessScope());
    model.setAttribution(e.getAttribution());
    return model;
  }

  public static MapTileEntity toNewEntity(UUID id, MapTileCreate create, boolean isPrimary) {
    MapTileEntity e = new MapTileEntity();
    e.setId(id);
    e.setPrimary(isPrimary);

    e.setName(create.name());
    e.setUrl(create.url());
    e.setDescription(create.description());
    e.setAttribution(blankToNull(create.attribution()));

    if (create.config() == null || create.config().getTile() == null) {
      throw new IllegalArgumentException("MapTileCreate.config.tile is required");
    }
    e.setKind(create.config().kind().name());
    e.setConfig(create.config());

    if (create.accessScope() != null) {
      e.setAccessScope(create.accessScope());
    }

    // tokenRef stored, token resolved elsewhere
    e.setTokenRef(create.token());

    return e;
  }

  public static void applyPatch(MapTilePatch patch, MapTileEntity e) {
    if (patch == null || e == null) return;

    patch.name().ifPresent(e::setName);
    patch.url().ifPresent(e::setUrl);
    patch.description().ifPresent(e::setDescription);
    patch.attribution().ifPresent(attribution -> e.setAttribution(blankToNull(attribution)));

    // The use case resolves the token slot to the secret's reference, or to a blank that clears
    // it; the token itself lives in the secret store and never reaches the row.
    patch.token().ifPresent(tokenRef -> e.setTokenRef(blankToNull(tokenRef)));

    patch.enabled().ifPresent(e::setEnabled);
    patch.accessScope().ifPresent(e::setAccessScope);

    if (patch.config().isPresent()) {
      var updatedConfig = applyConfigPatch(e.getConfig(), patch.config().get());
      e.setConfig(updatedConfig);
      e.setKind(updatedConfig.kind().name());
    }
  }

  /** A blank value is stored as none, so a patch clears a field by sending an empty string. */
  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static MapTileConfig applyConfigPatch(
      MapTileConfig existingConfig, MapTileConfigPatch patch) {
    if (patch.kind().isPresent() && patch.tile().isEmpty()) {
      throw new IllegalArgumentException(
          "MapTileConfigPatch.tile is required when kind is changed");
    }

    var merged =
        existingConfig == null
            ? new MapTileConfig()
            : MAPPER.convertValue(existingConfig, MapTileConfig.class);
    TileKind currentKind = merged.kind();

    patch.type().ifPresent(merged::setType);
    patch
        .extension()
        .ifPresent(
            extensionPatch ->
                merged.setExtension(applyExtensionPatch(merged.getExtension(), extensionPatch)));

    if (patch.tile().isPresent()) {
      TileKind kind = patch.kind().orElse(currentKind);
      boolean kindChanged = patch.kind().isPresent() && kind != currentKind;
      merged.setTile(
          applyTilePatch(kind, kindChanged ? null : merged.getTile(), patch.tile().get()));
    }

    normalizeConfig(merged);
    return merged;
  }

  private static TileConfig applyTilePatch(
      TileKind kind, TileConfig existingTile, TileConfigPatch patch) {
    try {
      var baseNode =
          existingTile == null
              ? MAPPER.createObjectNode()
              : (ObjectNode) MAPPER.valueToTree(existingTile);
      mergeNode(baseNode, MAPPER.valueToTree(patch));
      return MAPPER.treeToValue(baseNode, tileConfigClass(kind));
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Unable to apply config patch for kind=" + kind + ": " + ex.getMessage(), ex);
    }
  }

  private static TileConfigExtension applyExtensionPatch(
      TileConfigExtension existingExtension, TileConfigExtensionPatch patch) {
    try {
      var baseNode =
          existingExtension == null
              ? MAPPER.createObjectNode()
              : (ObjectNode) MAPPER.valueToTree(existingExtension);
      mergeNode(baseNode, MAPPER.valueToTree(patch));
      return MAPPER.treeToValue(baseNode, extensionClass(patch));
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to apply extension patch: " + ex.getMessage(), ex);
    }
  }

  private static void mergeNode(ObjectNode baseNode, ObjectNode patchNode) {
    patchNode
        .properties()
        .forEach(
            field -> {
              if (!field.getValue().isNull()) {
                baseNode.set(field.getKey(), field.getValue());
              }
            });
  }

  private static void normalizeConfig(MapTileConfig config) {
    if (config.tileType() == TileType.BASEMAP) {
      config.setExtension(null);
    }
  }

  private static Class<? extends TileConfig> tileConfigClass(TileKind kind) {
    return switch (kind) {
      case VECTOR -> VectorConfig.class;
      case RASTER -> RasterConfig.class;
      case RASTER_DEM -> RasterDemConfig.class;
      case HILLSHADE -> RasterConfig.class;
      case TERRAIN_MESH -> TerrainMeshConfig.class;
      case TILE_3D -> Tile3dConfig.class;
      case WMS -> WmsConfig.class;
      case WMTS -> WmtsConfig.class;
    };
  }

  private static Class<? extends TileConfigExtension> extensionClass(
      TileConfigExtensionPatch patch) {
    if (patch instanceof WeatherTileExtensionPatch) {
      return WeatherTileExtension.class;
    }
    throw new IllegalArgumentException(
        "Unsupported extension patch: " + patch.getClass().getName());
  }
}
