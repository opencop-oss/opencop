// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

public enum DataSourceGroupRole {
  OWNER,
  ADMIN,
  MAINTAINER,
  MEMBER;

  public boolean isHigherThan(DataSourceGroupRole other) {
    return this.ordinal() < other.ordinal();
  }

  public boolean isAtLeast(DataSourceGroupRole other) {
    return this.ordinal() <= other.ordinal();
  }

  public boolean isLowerThan(DataSourceGroupRole other) {
    return this.ordinal() > other.ordinal();
  }
}
