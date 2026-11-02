// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

/** Blob storage key prefix for different content types. */
public enum BlobKeyPrefix {
  MEDIA("media"),
  IMPORT("imports");

  private final String value;

  BlobKeyPrefix(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
