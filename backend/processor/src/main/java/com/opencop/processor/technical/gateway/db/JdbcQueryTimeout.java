// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import java.time.Duration;
import java.util.Objects;

public final class JdbcQueryTimeout {

  private JdbcQueryTimeout() {}

  public static int seconds(Duration timeout) {
    Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("JDBC query timeout must be greater than zero");
    }
    if (!fitsSeconds(timeout)) {
      throw new IllegalArgumentException("JDBC query timeout seconds must fit int");
    }
    long seconds = timeout.getSeconds() + (timeout.getNano() > 0 ? 1 : 0);
    return Math.toIntExact(seconds);
  }

  public static boolean fitsSeconds(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      return true;
    }
    return timeout.getSeconds() < Integer.MAX_VALUE
        || (timeout.getSeconds() == Integer.MAX_VALUE && timeout.getNano() == 0);
  }
}
