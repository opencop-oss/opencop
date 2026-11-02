// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.audit;

import com.opencop.core.sdk.observability.BoundedMetricTagValue;

public enum AuthzPermission implements BoundedMetricTagValue {
  NONE("NONE"),
  SERVER_OWNER("SERVER_OWNER"),
  SERVER_ADMIN("SERVER_ADMIN"),
  SERVER_VIEWER("SERVER_VIEWER"),
  LAYER_OWNER("LAYER_OWNER"),
  LAYER_ADMIN("LAYER_ADMIN"),
  LAYER_EDITOR("LAYER_EDITOR"),
  LAYER_MEMBER("LAYER_MEMBER");

  private final String value;

  AuthzPermission(String value) {
    this.value = value;
  }

  @Override
  public String value() {
    return value;
  }
}
