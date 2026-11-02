// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam;

public enum LayerRole {
  OWNER,
  ADMIN,
  EDITOR,
  MEMBER;

  public boolean isHigherThan(LayerRole other) {
    return this.ordinal() < other.ordinal(); // smaller ordinal = higher rank
  }

  public boolean isAtLeast(LayerRole other) {
    return this.ordinal() <= other.ordinal();
  }

  public boolean isLowerThan(LayerRole other) {
    return this.ordinal() > other.ordinal();
  }
}
