// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.model.geocoding.GeocodingSettings;
import com.opencop.core.sdk.helper.LocaleSupport;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-wide geocoding request defaults and bounds. */
@ConfigurationProperties(prefix = GeocodingProperties.PREFIX)
public class GeocodingProperties implements InitializingBean {

  public static final String PREFIX = "opencop.geocoding";

  private String defaultLanguage = "en";
  private int defaultLimit = 10;
  private int maxLimit = 20;
  private int maxTextLength = 200;
  private RateLimit rateLimit = new RateLimit();

  /**
   * Rejects a default language that is not a language tag at startup, since it would otherwise
   * label every search without a language as undetermined.
   */
  @Override
  public void afterPropertiesSet() {
    defaultLocale();
  }

  public GeocodingSettings toSettings() {
    return new GeocodingSettings(defaultLocale(), defaultLimit, maxLimit, maxTextLength);
  }

  private Locale defaultLocale() {
    return LocaleSupport.parseLanguageTag(defaultLanguage)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    PREFIX + ".default-language must be a BCP 47 language tag"));
  }

  public String getDefaultLanguage() {
    return defaultLanguage;
  }

  public void setDefaultLanguage(String defaultLanguage) {
    this.defaultLanguage = defaultLanguage;
  }

  public int getDefaultLimit() {
    return defaultLimit;
  }

  public void setDefaultLimit(int defaultLimit) {
    this.defaultLimit = defaultLimit;
  }

  public int getMaxLimit() {
    return maxLimit;
  }

  public void setMaxLimit(int maxLimit) {
    this.maxLimit = maxLimit;
  }

  public int getMaxTextLength() {
    return maxTextLength;
  }

  public void setMaxTextLength(int maxTextLength) {
    this.maxTextLength = maxTextLength;
  }

  public RateLimit getRateLimit() {
    return rateLimit;
  }

  public void setRateLimit(RateLimit rateLimit) {
    this.rateLimit = rateLimit;
  }

  /** How fast each user may search: a sustained rate, plus a burst allowance spent at once. */
  public static class RateLimit {

    private int requestsPerSecond = 10;
    private int burst = 20;

    public int getRequestsPerSecond() {
      return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {
      this.requestsPerSecond = requestsPerSecond;
    }

    public int getBurst() {
      return burst;
    }

    public void setBurst(int burst) {
      this.burst = burst;
    }
  }
}
