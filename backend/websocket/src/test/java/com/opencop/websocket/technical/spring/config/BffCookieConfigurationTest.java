// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.security.session.SessionCookieProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

class BffCookieConfigurationTest {

  @Test
  void defaultConfigurationUsesProductionBffCookieContract() throws NoSuchFieldException {
    var environment = loadConfiguration("application.yml");
    var cookie = bind(environment, "spring.session.servlet.cookie", SessionCookieProperties.class);
    var server = bind(environment, "server", ServerProperties.class);
    var allowedOrigins =
        WebSocketConfig.class.getDeclaredField("allowedOriginsConfig").getAnnotation(Value.class);

    assertThat(cookie.getName()).isEqualTo("OPENCOP_BFF");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getDomain()).isNull();
    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.getHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
    assertThat(server.getForwardHeadersStrategy())
        .isEqualTo(ServerProperties.ForwardHeadersStrategy.FRAMEWORK);
    assertThat(environment.getProperty("spring.session.redis.namespace")).isEqualTo("opencop:bff");
    assertThat(allowedOrigins).isNotNull();
    assertThat(allowedOrigins.value())
        .isEqualTo("${websocket.allowed-origins:http://localhost:3000,http://localhost:8086}");
  }

  @Test
  void localConfigurationPreservesDevelopmentBffCookieContract() {
    assertDevelopmentCookieContract("application-local.yml");
  }

  @Test
  void dockerLocalConfigurationPreservesDevelopmentBffCookieContract() {
    assertDevelopmentCookieContract("application-docker-local.yml");
  }

  private static void assertDevelopmentCookieContract(String profileResourceName) {
    var environment = loadConfiguration("application.yml", profileResourceName);
    var cookie = bind(environment, "spring.session.servlet.cookie", SessionCookieProperties.class);

    assertThat(cookie.getName()).isEqualTo("OPENCOP_BFF");
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(cookie.getDomain()).isEmpty();
    assertThat(cookie.getSecure()).isFalse();
    assertThat(cookie.getHttpOnly()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  private static ConfigurableEnvironment loadConfiguration(String... resourceNames) {
    var environment = new StandardEnvironment();
    var loader = new YamlPropertySourceLoader();

    for (var resourceName : resourceNames) {
      var resource = new ClassPathResource(resourceName);
      try {
        for (var propertySource : loader.load(resourceName, resource)) {
          environment.getPropertySources().addFirst(propertySource);
        }
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    }

    return environment;
  }

  private static <T> T bind(ConfigurableEnvironment environment, String prefix, Class<T> type) {
    return Binder.get(environment)
        .bind(prefix, type)
        .orElseThrow(() -> new IllegalStateException("Missing configuration: " + prefix));
  }
}
