// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PointElement extends Element {

  public static final String FILL_COLOR = "fillColor";
  public static final String SYMBOL = "symbol";

  public PointElement(String type) {
    super(type);
  }

  private PointElement(Builder builder) {
    super(builder);
  }

  public String getFillColor() {
    return (String) get(FILL_COLOR);
  }

  public void setFillColor(String fillColor) {
    put(FILL_COLOR, fillColor);
  }

  public String getSymbol() {
    return (String) get(SYMBOL);
  }

  public void setSymbol(String symbol) {
    put(SYMBOL, symbol);
  }

  public static class Builder extends Element.Builder {
    public Builder fillColor(String fillColor) {
      return (Builder) put(FILL_COLOR, fillColor);
    }

    public Builder symbol(String symbol) {
      return (Builder) put(SYMBOL, symbol);
    }
  }
}
