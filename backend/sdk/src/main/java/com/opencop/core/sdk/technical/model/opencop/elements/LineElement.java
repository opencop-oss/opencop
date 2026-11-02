// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

public class LineElement extends Element {
  protected static final String STROKE_COLOR = "strokeColor";
  protected static final String STROKE_WEIGHT = "strokeWeight";
  protected static final String STROKE_STYLE = "strokeStyle";

  protected static final String RANGE = "range";
  protected static final String BEARING = "bearing";

  public LineElement(String type) {
    super(type);
  }

  public LineElement(Builder builder) {
    super(builder);
  }

  public double getStrokeWeight() {
    Object v = get(STROKE_WEIGHT);
    return v == null ? 0.0 : ((Number) v).doubleValue();
  }

  public void setStrokeWeight(double strokeWeight) {
    put(STROKE_WEIGHT, strokeWeight);
  }

  public String getStrokeStyle() {
    return (String) get(STROKE_STYLE);
  }

  public void setStrokeStyle(String strokeStyle) {
    put(STROKE_STYLE, strokeStyle);
  }

  public String getStrokeColor() {
    return (String) get(STROKE_COLOR);
  }

  public void setStrokeColor(String strokeColor) {
    put(STROKE_COLOR, strokeColor);
  }

  public double getRange() {
    Object v = get(RANGE);
    return v == null ? 0.0 : ((Number) v).doubleValue();
  }

  public void setRange(double range) {
    put(RANGE, range);
  }

  public double getBearing() {
    Object v = get(BEARING);
    return v == null ? 0.0 : ((Number) v).doubleValue();
  }

  public void setBearing(double bearing) {
    put(BEARING, bearing);
  }

  public static class Builder extends Element.Builder {
    public Builder range(double range) {
      return (Builder) put(RANGE, range);
    }

    public Builder bearing(double bearing) {
      return (Builder) put(BEARING, bearing);
    }

    public Builder strokeWeight(double strokeWeight) {
      return (Builder) put(STROKE_WEIGHT, strokeWeight);
    }

    public Builder strokeStyle(String strokeStyle) {
      return (Builder) put(STROKE_STYLE, strokeStyle);
    }

    public Builder strokeColor(String strokeColor) {
      return (Builder) put(STROKE_COLOR, strokeColor);
    }
  }
}
