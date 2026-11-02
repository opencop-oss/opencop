// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.rules;

public enum TargetField {
  GEOMETRY("geometry", ValueType.GEOMETRY),
  GEOMETRY_LAT("geometry.lat", ValueType.DOUBLE),
  GEOMETRY_LON("geometry.lon", ValueType.DOUBLE),
  GEOMETRY_POINT("geometry.point", ValueType.POINT),

  ELEMENT_POINT_COLOR("element.point.color", ValueType.STRING),
  ELEMENT_SYMBOL_SIDC(
      "element.symbol.sidc", ValueType.STRING), // Map to the same, handle differently
  ELEMENT_ICON_LOCATION("element.icon.location", ValueType.STRING),

  UPDATED_AT("properties.updatedAt", ValueType.INSTANT),
  STALE_AT("properties.staleAt", ValueType.INSTANT),
  START_AT("properties.startAt", ValueType.INSTANT),

  DISPLAY_NAME("properties.displayName", ValueType.STRING),
  OBJECT_ID("properties.objectId", ValueType.STRING),
  SOURCE_ID("properties.source", ValueType.UUID),

  ELEMENT_SPEED("properties.element.speed", ValueType.DOUBLE),
  ELEMENT_COURSE("properties.element.course", ValueType.DOUBLE),
  ELEMENT_HEADING("properties.element.heading", ValueType.DOUBLE),

  STYLE_STROKE_STYLE("properties.style.strokeStyle", ValueType.STRING),
  STYLE_STROKE_COLOR("properties.style.strokeColor", ValueType.STRING),
  STYLE_STROKE_WIDTH("properties.style.strokeWidth", ValueType.DOUBLE),
  STYLE_FILL_COLOR("properties.style.fillColor", ValueType.STRING),
  STYLE_FILL_OPACITY("properties.style.fillOpacity", ValueType.DOUBLE),

  RAW_STYLE_URL("properties.rawStyleUrl", ValueType.STRING),
  ATTRIBUTES("properties.attributes", ValueType.MAP);

  private final String path;
  private final ValueType valueType;

  TargetField(String path, ValueType valueType) {
    this.path = path;
    this.valueType = valueType;
  }

  public String path() {
    return path;
  }

  public ValueType valueType() {
    return valueType;
  }
}
