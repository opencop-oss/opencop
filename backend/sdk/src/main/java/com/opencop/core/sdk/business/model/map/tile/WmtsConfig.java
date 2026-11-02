// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import java.util.Map;

public final class WmtsConfig implements TileConfig {
  private String version; // usually "1.0.0"
  private String layer; // required
  private String style; // often "default"
  private String format; // "image/png"
  private String tileMatrixSet; // e.g. "GoogleMapsCompatible"
  private Map<String, String> extraParams;

  public WmtsConfig() {}

  public WmtsConfig(
      String version,
      String layer,
      String style,
      String format,
      String tileMatrixSet,
      Map<String, String> extraParams) {
    this.version = version;
    this.layer = layer;
    this.style = style;
    this.format = format;
    this.tileMatrixSet = tileMatrixSet;
    this.extraParams = extraParams;
  }

  @Override
  public TileKind kind() {
    return TileKind.WMTS;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getLayer() {
    return layer;
  }

  public void setLayer(String layer) {
    this.layer = layer;
  }

  public String getStyle() {
    return style;
  }

  public void setStyle(String style) {
    this.style = style;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public String getTileMatrixSet() {
    return tileMatrixSet;
  }

  public void setTileMatrixSet(String tileMatrixSet) {
    this.tileMatrixSet = tileMatrixSet;
  }

  public Map<String, String> getExtraParams() {
    return extraParams;
  }

  public void setExtraParams(Map<String, String> extraParams) {
    this.extraParams = extraParams;
  }
}
