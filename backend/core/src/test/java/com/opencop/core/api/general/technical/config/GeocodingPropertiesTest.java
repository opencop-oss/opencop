// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.api.general.business.model.geocoding.GeocodingSettings;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GeocodingPropertiesTest {

  @Test
  void appliesTheDefaults() {
    var properties = bind(Map.of());

    properties.afterPropertiesSet();

    assertThat(properties.toSettings())
        .isEqualTo(new GeocodingSettings(Locale.ENGLISH, 10, 20, 200));
    assertThat(properties.getRateLimit().getRequestsPerSecond()).isEqualTo(10);
    assertThat(properties.getRateLimit().getBurst()).isEqualTo(20);
  }

  @Test
  void bindsTheEnvironmentStyleNames() {
    var properties =
        bind(
            Map.of(
                "opencop.geocoding.default-language", "zh-CN",
                "opencop.geocoding.default-limit", "5",
                "opencop.geocoding.max-limit", "15",
                "opencop.geocoding.max-text-length", "120",
                "opencop.geocoding.rate-limit.requests-per-second", "4",
                "opencop.geocoding.rate-limit.burst", "8"));

    properties.afterPropertiesSet();

    assertThat(properties.toSettings())
        .isEqualTo(new GeocodingSettings(Locale.forLanguageTag("zh-CN"), 5, 15, 120));
    assertThat(properties.getRateLimit().getRequestsPerSecond()).isEqualTo(4);
    assertThat(properties.getRateLimit().getBurst()).isEqualTo(8);
  }

  @ParameterizedTest
  @ValueSource(strings = {"en_US", "123", " ", "!!", "zh-CN-!", "en--US", "en-US-u", "en-", "en-@"})
  void rejectsADefaultLanguageThatIsNotALanguageTag(String language) {
    var properties = bind(Map.of("opencop.geocoding.default-language", language));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.geocoding.default-language");
  }

  @ParameterizedTest
  @CsvSource({
    "' en-GB ', en-GB",
    "zh-Hant-TW, zh-Hant-TW",
    "de-DE-u-co-phonebk, de-DE-u-co-phonebk"
  })
  void acceptsAWellFormedDefaultLanguage(String language, String expectedTag) {
    var properties = bind(Map.of("opencop.geocoding.default-language", language));

    properties.afterPropertiesSet();

    assertThat(properties.toSettings().defaultLanguage().toLanguageTag()).isEqualTo(expectedTag);
  }

  @ParameterizedTest
  @CsvSource({"0, 20, 200", "21, 20, 200", "10, 0, 200", "10, 20, 0"})
  void rejectsLimitsOutOfBounds(String defaultLimit, String maxLimit, String maxTextLength) {
    var properties =
        bind(
            Map.of(
                "opencop.geocoding.default-limit", defaultLimit,
                "opencop.geocoding.max-limit", maxLimit,
                "opencop.geocoding.max-text-length", maxTextLength));

    assertThatThrownBy(properties::toSettings).isInstanceOf(IllegalArgumentException.class);
  }

  private static GeocodingProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate(GeocodingProperties.PREFIX, GeocodingProperties.class);
  }
}
