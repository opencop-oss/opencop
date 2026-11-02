// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.blob;

public enum CloudStorageExceptionCodes {
  BLOB_DOES_NOT_EXIST("CLOUD-STORAGE-CLIENT-3001", "Source blob does not exist"),
  FILE_MOVE_FAILED("CLOUD-STORAGE-CLIENT-3002", "Failed to move file"),
  CERTIFICATE_READING_FAILED("CLOUD-STORAGE-CLIENT-3004", "Error while reading certificate"),
  CERTIFICATE_NOT_FOUND("CLOUD-STORAGE-CLIENT-3005", "Could not find certificate");

  private final String code;
  private final String description;

  CloudStorageExceptionCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String description() {
    return this.description;
  }
}
