// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

public enum LayerGroupRole {
  OWNER,
  ADMIN,
  MAINTAINER,
  MEMBER;

  public boolean isHigherThan(LayerGroupRole other) {
    return this.ordinal() < other.ordinal();
  }

  public boolean isAtLeast(LayerGroupRole other) {
    return this.ordinal() <= other.ordinal();
  }

  public boolean isLowerThan(LayerGroupRole other) {
    return this.ordinal() > other.ordinal();
  }
}
