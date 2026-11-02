// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import com.opencop.core.sdk.technical.gateway.db.iam.MapTileAccessScope;
import java.util.UUID;

public class MapTile {

  /**
   * The longest {@link #attribution} the API accepts, once surrounding whitespace is removed. The
   * map_tile_attribution_length check constraint mirrors it.
   */
  public static final int MAX_ATTRIBUTION_LENGTH = 1_000;

  private UUID id;
  private boolean primary;

  /** When false the tile is retained but withheld from every operator-facing surface. */
  private boolean enabled = true;

  private MapTileAccessScope accessScope = MapTileAccessScope.PRIVATE;

  private String name;
  private String description;

  private String url;
  private MapTileConfig config;
  private String token;

  /**
   * Credit for this tile, or null: free text that may contain {@code <a href>} links, as provider
   * credits often do. Stored with surrounding whitespace removed and checked for length only, so it
   * is untrusted: reduce it to text and http(s) links before display; never insert it as HTML.
   */
  private String attribution;

  public MapTile() {}

  public MapTile(
      UUID id,
      boolean primary,
      boolean enabled,
      String name,
      String description,
      String url,
      MapTileConfig config,
      String token) {

    this.id = id;
    this.primary = primary;
    this.enabled = enabled;
    this.name = name;
    this.description = description;
    this.url = url;
    this.config = config;
    this.token = token;
  }

  public UUID getId() {
    return id;
  }

  public boolean isPrimary() {
    return primary;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public MapTileAccessScope getAccessScope() {
    return accessScope;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  public MapTileConfig getConfig() {
    return config;
  }

  public String getToken() {
    return token;
  }

  public String getAttribution() {
    return attribution;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public void setPrimary(boolean primary) {
    this.primary = primary;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setAccessScope(MapTileAccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setConfig(MapTileConfig config) {
    this.config = config;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setAttribution(String attribution) {
    this.attribution = attribution;
  }
}
