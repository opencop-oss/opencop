// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

public class ElevationDecoderConfig {

  private double rScaler;
  private double gScaler;
  private double bScaler;
  private double offset;

  public ElevationDecoderConfig() {}

  public ElevationDecoderConfig(double rScaler, double gScaler, double bScaler, double offset) {
    this.rScaler = rScaler;
    this.gScaler = gScaler;
    this.bScaler = bScaler;
    this.offset = offset;
  }

  public double getrScaler() {
    return rScaler;
  }

  public void setrScaler(double rScaler) {
    this.rScaler = rScaler;
  }

  public double getgScaler() {
    return gScaler;
  }

  public void setgScaler(double gScaler) {
    this.gScaler = gScaler;
  }

  public double getbScaler() {
    return bScaler;
  }

  public void setbScaler(double bScaler) {
    this.bScaler = bScaler;
  }

  public double getOffset() {
    return offset;
  }

  public void setOffset(double offset) {
    this.offset = offset;
  }
}
