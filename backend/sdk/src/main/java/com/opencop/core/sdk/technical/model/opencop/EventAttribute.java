// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

public class EventAttribute {

  private String attribute;
  private String value;

  public EventAttribute() {}

  public EventAttribute(String attribute, String value) {
    this.attribute = attribute;
    this.value = value;
  }

  public String getAttribute() {
    return attribute;
  }

  public void setAttribute(String attribute) {
    this.attribute = attribute;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
