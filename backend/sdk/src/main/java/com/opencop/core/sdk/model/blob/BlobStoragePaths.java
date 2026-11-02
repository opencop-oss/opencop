// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.blob;

public enum BlobStoragePaths {
  LAYER_ICON("layers/imports/", "layers/imports/%s/%s");

  private final String base;
  private final String format;

  BlobStoragePaths(String base, String format) {
    this.base = base;
    this.format = format;
  }

  public String getBase() {
    return base;
  }

  public String getFormat() {
    return format;
  }
}
