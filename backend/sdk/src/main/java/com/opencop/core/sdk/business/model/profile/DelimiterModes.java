// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.profile;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum DelimiterModes {
  DELIMITER_INCLUDE("delimiterInclude"),
  DELIMITER_SPLIT("delimiterSplit"),
  BINARY("binary"),
  PASSTHROUGH("passthrough");

  private final String mode;

  DelimiterModes(String mode) {
    this.mode = mode;
  }

  @JsonValue
  public String mode() {
    return mode;
  }

  /** The mode stored under {@code mode}, or empty when no mode carries that name. */
  public static Optional<DelimiterModes> find(String mode) {
    return Arrays.stream(values()).filter(value -> value.mode.equals(mode)).findFirst();
  }

  @JsonCreator
  public static DelimiterModes fromMode(String mode) {
    return find(mode)
        .orElseThrow(() -> new IllegalArgumentException("Unknown delimiter mode: " + mode));
  }
}
