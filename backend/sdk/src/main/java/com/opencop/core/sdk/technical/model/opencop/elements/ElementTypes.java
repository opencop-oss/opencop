// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop.elements;

public enum ElementTypes {
  LINE("line"),
  ARROW("arrow"),
  ELLIPSE("ellipse"),
  POLYGON("polygon"),
  RECTANGLE("rectangle"),
  SYMBOL("symbol"),
  POINT("point"),
  /** A point drawn as an image; see {@link MediaIconElement}. */
  MEDIA_ICON("media-icon");

  private final String type;

  ElementTypes(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}
