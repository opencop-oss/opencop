// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Deployment properties for the Nominatim geocoder.
 *
 * <p>There is deliberately no default base URL: the public OpenStreetMap Foundation instance bans
 * autocomplete, so a deployment must point at a Nominatim it hosts.
 */
@Validated
@ConfigurationProperties(prefix = NominatimGeocoderProperties.PREFIX)
public class NominatimGeocoderProperties implements InitializingBean {

  public static final String PREFIX = "opencop.integration.nominatim.geocoder";

  private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");
  private static final double MAX_FOCUS_HALF_SIZE_DEGREES = 90;

  private boolean enabled;
  private String baseUrl;
  private String username;
  private String password;
  private Duration connectTimeout = Duration.ofSeconds(1);
  private Duration readTimeout = Duration.ofSeconds(3);
  private double focusHalfSizeDegrees = 0.05;

  /**
   * Rejects an unusable configuration at startup, so a deployment never runs with a geocoder that
   * fails every search.
   */
  @Override
  public void afterPropertiesSet() {
    if (!enabled) {
      return;
    }
    if (!isHttpUrl(baseUrl)) {
      throw new IllegalStateException(
          PREFIX
              + ".base-url must be an absolute http(s) URL without a query or fragment when the"
              + " geocoder is enabled");
    }
    if (hasUsername() && (password == null || password.isEmpty())) {
      throw new IllegalStateException(PREFIX + ".password is required when a username is set");
    }
    requirePositive(connectTimeout, "connect-timeout");
    requirePositive(readTimeout, "read-timeout");
    if (!isUsableFocusHalfSize(focusHalfSizeDegrees)) {
      throw new IllegalStateException(
          PREFIX
              + ".focus-half-size-degrees must be greater than 0 and at most "
              + MAX_FOCUS_HALF_SIZE_DEGREES);
    }
  }

  /** Whether requests carry HTTP Basic credentials, such as for a reverse proxy. */
  public boolean hasUsername() {
    return username != null && !username.isBlank();
  }

  /** The HTTP client refuses a null, zero or negative timeout, so it is caught here instead. */
  private static void requirePositive(Duration timeout, String property) {
    if (timeout == null || !timeout.isPositive()) {
      throw new IllegalStateException(PREFIX + "." + property + " must be a positive duration");
    }
  }

  /** Written to accept only a number in range, so NaN is refused too. */
  private static boolean isUsableFocusHalfSize(double degrees) {
    return degrees > 0 && degrees <= MAX_FOCUS_HALF_SIZE_DEGREES;
  }

  /**
   * The search path and parameters are appended to the base URL, so a query or fragment in it would
   * swallow them.
   */
  private static boolean isHttpUrl(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      var uri = new URI(value);
      return uri.isAbsolute()
          && HTTP_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
          && uri.getHost() != null
          && uri.getRawQuery() == null
          && uri.getRawFragment() == null;
    } catch (URISyntaxException _) {
      return false;
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public double getFocusHalfSizeDegrees() {
    return focusHalfSizeDegrees;
  }

  public void setFocusHalfSizeDegrees(double focusHalfSizeDegrees) {
    this.focusHalfSizeDegrees = focusHalfSizeDegrees;
  }
}
