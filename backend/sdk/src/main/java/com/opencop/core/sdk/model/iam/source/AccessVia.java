// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.iam.source;

public enum AccessVia {
  DIRECT,
  INDIRECT,
  BOTH;

  public AccessVia merge(AccessVia other) {
    if (this == other) {
      return this;
    }
    return BOTH;
  }
}
