// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security.session;

import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "opencop.auth.session")
public class AuthSessionProperties implements InitializingBean {

  static final String IDLE_TIMEOUT_PROPERTY = "opencop.auth.session.idle-timeout";
  static final String ABSOLUTE_LIFETIME_PROPERTY = "opencop.auth.session.absolute-lifetime";

  private Duration idleTimeout = Duration.ofMinutes(30);
  private Duration absoluteLifetime = Duration.ofHours(8);

  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(Duration idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public Duration getAbsoluteLifetime() {
    return absoluteLifetime;
  }

  public void setAbsoluteLifetime(Duration absoluteLifetime) {
    this.absoluteLifetime = absoluteLifetime;
  }

  @Override
  public void afterPropertiesSet() {
    requirePositiveWholeSeconds(IDLE_TIMEOUT_PROPERTY, idleTimeout);
    requirePositiveWholeSeconds(ABSOLUTE_LIFETIME_PROPERTY, absoluteLifetime);
    if (!absoluteLifetime.minus(idleTimeout).isPositive()) {
      throw invalid(ABSOLUTE_LIFETIME_PROPERTY, "must be greater than " + IDLE_TIMEOUT_PROPERTY);
    }
    if (absoluteLifetime.getSeconds() > Integer.MAX_VALUE) {
      throw invalid(ABSOLUTE_LIFETIME_PROPERTY, "exceeds the cookie Max-Age range");
    }
  }

  private static void requirePositiveWholeSeconds(String property, Duration value) {
    if (value == null) {
      throw invalid(property, "must be configured");
    }
    if (!value.isPositive()) {
      throw invalid(property, "must be positive");
    }
    if (!value.equals(Duration.ofSeconds(value.getSeconds()))) {
      throw invalid(property, "must use whole-second precision");
    }
  }

  private static IllegalStateException invalid(String property, String reason) {
    return new IllegalStateException("Invalid " + property + ": " + reason);
  }
}
