// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.cache;

public enum CacheExceptionCodes {
  VALUE_DOES_NOT_EXIST("CACHE-EXCEPTION-1000", "Key/Value not present in Cache"),
  SERIALIZATION_FAILED("CACHE-EXCEPTION-1001", "Could not serialize object"),
  DESERIALIZATION_FAILED("CACHE-EXCEPTION-1002", "Could not deserialize object");

  private final String code;
  private final String description;

  CacheExceptionCodes(String code, String description) {
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
