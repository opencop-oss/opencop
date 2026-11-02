// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AccessMode {
  READ("R"),
  WRITE("W"),
  READ_WRITE("RW");

  private final String access;

  private AccessMode(String access) {
    this.access = access;
  }

  @JsonValue
  public String getAccess() {
    return this.access;
  }

  public boolean canWrite() {
    return this == WRITE || this == READ_WRITE;
  }

  public boolean canRead() {
    return this == READ || this == READ_WRITE;
  }

  @JsonCreator
  public static AccessMode fromString(String access) {
    if (access == null) {
      throw new IllegalArgumentException("Access mode is required");
    }
    return switch (access) {
      case "R" -> AccessMode.READ;
      case "W" -> AccessMode.WRITE;
      case "RW" -> AccessMode.READ_WRITE;
      default -> throw new IllegalArgumentException("Unknown access mode: " + access);
    };
  }
}
