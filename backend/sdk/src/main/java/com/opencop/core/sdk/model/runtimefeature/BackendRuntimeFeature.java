// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.runtimefeature;

public enum BackendRuntimeFeature {
  WATCHBOX("watchbox");

  private final String wireName;

  BackendRuntimeFeature(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }
}
