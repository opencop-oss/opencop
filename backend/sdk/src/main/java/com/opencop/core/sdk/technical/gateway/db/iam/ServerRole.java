// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam;

public enum ServerRole {
  OWNER,
  ADMIN,
  VIEWER;

  public boolean isHigherThan(ServerRole other) {
    return this.ordinal() < other.ordinal(); // smaller ordinal = higher rank
  }

  public boolean isAtLeast(ServerRole other) {
    return this.ordinal() <= other.ordinal();
  }

  public boolean isLowerThan(ServerRole other) {
    return this.ordinal() > other.ordinal();
  }
}
