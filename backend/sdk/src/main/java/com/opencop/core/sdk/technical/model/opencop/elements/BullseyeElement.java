// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// TODO: FIX WHEN WE IMPLEMENT BULLSEYE.
@JsonIgnoreProperties(ignoreUnknown = true)
public class BullseyeElement extends Element {

  public static final String TYPE = "bullseye";
  private String direction;
  private boolean showRings;
  private double radius;
  private double rings;
  private double ringRadius;

  public BullseyeElement() {
    super(TYPE);
  }

  private BullseyeElement(Builder builder) {
    super(builder);
    this.direction = builder.direction;
    this.showRings = builder.showRings;
    this.radius = builder.radius;
    this.rings = builder.rings;
    this.ringRadius = builder.ringRadius;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public boolean isShowRings() {
    return showRings;
  }

  public void setShowRings(boolean showRings) {
    this.showRings = showRings;
  }

  public double getRadius() {
    return radius;
  }

  public void setRadius(double radius) {
    this.radius = radius;
  }

  public double getRings() {
    return rings;
  }

  public void setRings(double rings) {
    this.rings = rings;
  }

  public double getRingRadius() {
    return ringRadius;
  }

  public void setRingRadius(double ringRadius) {
    this.ringRadius = ringRadius;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  public static class Builder extends Element.Builder {
    private String direction;
    private boolean showRings;
    private double radius;
    private double rings;
    private double ringRadius;

    public Builder direction(String direction) {
      this.direction = direction;
      return this;
    }

    public Builder ringRadius(double ringRadius) {
      this.ringRadius = ringRadius;
      return this;
    }

    public Builder rings(double rings) {
      this.rings = rings;
      return this;
    }

    public Builder radius(double radius) {
      this.radius = radius;
      return this;
    }

    public Builder showRings(boolean showRings) {
      this.showRings = showRings;
      return this;
    }

    public BullseyeElement build() {
      return new BullseyeElement(this);
    }
  }
}
