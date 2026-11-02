// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

import java.util.List;

public class ShapeElement extends LineElement {
  protected static final String FILL_COLOR = "fillColor";
  protected static final String FILL_OPACITY = "fillOpacity";

  private static final String CENTER = "center";
  private static final String MAJOR = "major";
  private static final String MINOR = "minor";

  public ShapeElement(String type) {
    super(type);
  }

  public ShapeElement(Builder builder) {
    super(builder);
  }

  public String getFillColor() {
    return (String) get(FILL_COLOR);
  }

  public void setFillColor(String fillColor) {
    put(FILL_COLOR, fillColor);
  }

  public List<Double> getCenter() {
    // Only unsafe if you're stupid about it.
    return (List<Double>) get(CENTER);
  }

  public void setCenter(List<Double> center) {
    put(CENTER, center);
  }

  public double getMajor() {
    Object v = get(MAJOR);
    return v == null ? 0.0 : ((Number) v).doubleValue();
  }

  public void setMajor(double major) {
    put(MAJOR, major);
  }

  public double getMinor() {
    Object v = get(MINOR);
    return v == null ? 0.0 : ((Number) v).doubleValue();
  }

  public void setMinor(double minor) {
    put(MINOR, minor);
  }

  public static class Builder extends LineElement.Builder {
    public Builder major(double major) {
      return (Builder) put(MAJOR, major);
    }

    public Builder minor(double minor) {
      return (Builder) put(MINOR, minor);
    }

    public Builder center(List<Double> center) {
      return (Builder) put(CENTER, center);
    }

    public Builder fillColor(String fillColor) {
      return (Builder) put(FILL_COLOR, fillColor);
    }
  }
}
