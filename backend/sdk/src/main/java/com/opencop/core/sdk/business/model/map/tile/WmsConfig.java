// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import java.util.List;
import java.util.Map;

public final class WmsConfig implements TileConfig {
  private String version; // "1.3.0" or "1.1.1"
  private List<String> layers; // required
  private List<String> styles; // optional
  private String format; // "image/png"
  private boolean transparent; // usually true
  private String crs; // "EPSG:3857" (note WMS 1.3.0 uses CRS param)
  private Map<String, String> extraParams; // vendor params

  public WmsConfig() {}

  public WmsConfig(
      String version,
      List<String> layers,
      List<String> styles,
      String format,
      boolean transparent,
      String crs,
      Map<String, String> extraParams) {
    this.version = version;
    this.layers = layers;
    this.styles = styles;
    this.format = format;
    this.transparent = transparent;
    this.crs = crs;
    this.extraParams = extraParams;
  }

  @Override
  public TileKind kind() {
    return TileKind.WMS;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public List<String> getLayers() {
    return layers;
  }

  public void setLayers(List<String> layers) {
    this.layers = layers;
  }

  public List<String> getStyles() {
    return styles;
  }

  public void setStyles(List<String> styles) {
    this.styles = styles;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public boolean isTransparent() {
    return transparent;
  }

  public void setTransparent(boolean transparent) {
    this.transparent = transparent;
  }

  public String getCrs() {
    return crs;
  }

  public void setCrs(String crs) {
    this.crs = crs;
  }

  public Map<String, String> getExtraParams() {
    return extraParams;
  }

  public void setExtraParams(Map<String, String> extraParams) {
    this.extraParams = extraParams;
  }
}
