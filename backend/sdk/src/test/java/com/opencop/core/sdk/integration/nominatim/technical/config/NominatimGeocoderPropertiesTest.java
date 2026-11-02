// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class NominatimGeocoderPropertiesTest {

  @Test
  void appliesTheDefaults() {
    var properties = bind(Map.of());

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getBaseUrl()).isNull();
    assertThat(properties.getUsername()).isNull();
    assertThat(properties.getPassword()).isNull();
    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.getFocusHalfSizeDegrees()).isEqualTo(0.05);
  }

  @Test
  void bindsTheEnvironmentStyleNames() {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled", "true",
                "opencop.integration.nominatim.geocoder.base-url", "https://nominatim.example.org",
                "opencop.integration.nominatim.geocoder.username", "opencop",
                "opencop.integration.nominatim.geocoder.password", "secret",
                "opencop.integration.nominatim.geocoder.read-timeout", "5s"));

    properties.afterPropertiesSet();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getBaseUrl()).isEqualTo("https://nominatim.example.org");
    assertThat(properties.getUsername()).isEqualTo("opencop");
    assertThat(properties.getPassword()).isEqualTo("secret");
    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void acceptsAMissingBaseUrlWhileDisabled() {
    var properties = bind(Map.of());

    properties.afterPropertiesSet();

    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void rejectsAMissingBaseUrlWhenEnabled() {
    var properties = bind(Map.of("opencop.integration.nominatim.geocoder.enabled", "true"));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder.base-url");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nominatim.example.org",
        "ftp://nominatim.example.org",
        "http://",
        "::",
        "https://nominatim.example.org/api?token=abc",
        "https://nominatim.example.org/api?",
        "https://nominatim.example.org/api#anchor",
        "https://nominatim.example.org/api#"
      })
  void rejectsAMalformedBaseUrlWhenEnabled(String baseUrl) {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled",
                "true",
                "opencop.integration.nominatim.geocoder.base-url",
                baseUrl));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder.base-url");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://nominatim.example.org",
        "https://nominatim.example.org/",
        "http://localhost:8080/nominatim",
        "https://proxy.example.org/geo/nominatim/"
      })
  void acceptsARootOrPathBaseUrlWhenEnabled(String baseUrl) {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled",
                "true",
                "opencop.integration.nominatim.geocoder.base-url",
                baseUrl));

    properties.afterPropertiesSet();

    assertThat(properties.getBaseUrl()).isEqualTo(baseUrl);
  }

  @Test
  void acceptsABaseUrlWithAQueryWhileDisabled() {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.base-url",
                "https://nominatim.example.org/api?token=abc"));

    properties.afterPropertiesSet();

    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void rejectsAUsernameWithoutAPassword() {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled", "true",
                "opencop.integration.nominatim.geocoder.base-url", "http://localhost:8080",
                "opencop.integration.nominatim.geocoder.username", "opencop"));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder.password");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-0.5", "90.5", "NaN"})
  void rejectsAnUnusableFocusHalfSizeWhenEnabled(String halfSize) {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled", "true",
                "opencop.integration.nominatim.geocoder.base-url", "http://localhost:8080",
                "opencop.integration.nominatim.geocoder.focus-half-size-degrees", halfSize));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder.focus-half-size-degrees");
  }

  @ParameterizedTest
  @CsvSource({
    "connect-timeout, 0s",
    "connect-timeout, -1s",
    "read-timeout, 0s",
    "read-timeout, -1s"
  })
  void rejectsANonPositiveTimeoutWhenEnabled(String property, String timeout) {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled",
                "true",
                "opencop.integration.nominatim.geocoder.base-url",
                "http://localhost:8080",
                "opencop.integration.nominatim.geocoder." + property,
                timeout));

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder." + property);
  }

  @Test
  void rejectsAMissingReadTimeoutWhenEnabled() {
    var properties =
        bind(
            Map.of(
                "opencop.integration.nominatim.geocoder.enabled", "true",
                "opencop.integration.nominatim.geocoder.base-url", "http://localhost:8080"));
    properties.setReadTimeout(null);

    assertThatThrownBy(properties::afterPropertiesSet)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("opencop.integration.nominatim.geocoder.read-timeout");
  }

  private static NominatimGeocoderProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate(NominatimGeocoderProperties.PREFIX, NominatimGeocoderProperties.class);
  }
}
