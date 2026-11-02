// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public final class TerrainMeshConfig implements TileConfig {
  private String elevationUrl; // if different from base url
  private ElevationEncoding encoding; // terrarium vs terrain-rgb
  private ElevationDecoderConfig decoderConfig; // Optional
  private Double exaggeration; // UI-friendly
  private Integer meshMaxError; // optional (deck control)
  private Integer boundsPadding; // optional

  public TerrainMeshConfig() {}

  public TerrainMeshConfig(
      String elevationUrl,
      ElevationEncoding encoding,
      ElevationDecoderConfig decoderConfig,
      Double exaggeration,
      Integer meshMaxError,
      Integer boundsPadding) {
    this.elevationUrl = elevationUrl;
    this.encoding = encoding;
    this.decoderConfig = decoderConfig;
    this.exaggeration = exaggeration;
    this.meshMaxError = meshMaxError;
    this.boundsPadding = boundsPadding;
  }

  @Override
  public TileKind kind() {
    return TileKind.TERRAIN_MESH;
  }

  public String getElevationUrl() {
    return elevationUrl;
  }

  public void setElevationUrl(String elevationUrl) {
    this.elevationUrl = elevationUrl;
  }

  public ElevationEncoding getEncoding() {
    return encoding;
  }

  public void setEncoding(ElevationEncoding encoding) {
    this.encoding = encoding;
  }

  public ElevationDecoderConfig getDecoderConfig() {
    return decoderConfig;
  }

  public void setDecoderConfig(ElevationDecoderConfig decoderConfig) {
    this.decoderConfig = decoderConfig;
  }

  public Double getExaggeration() {
    return exaggeration;
  }

  public void setExaggeration(Double exaggeration) {
    this.exaggeration = exaggeration;
  }

  public Integer getMeshMaxError() {
    return meshMaxError;
  }

  public void setMeshMaxError(Integer meshMaxError) {
    this.meshMaxError = meshMaxError;
  }

  public Integer getBoundsPadding() {
    return boundsPadding;
  }

  public void setBoundsPadding(Integer boundsPadding) {
    this.boundsPadding = boundsPadding;
  }
}
