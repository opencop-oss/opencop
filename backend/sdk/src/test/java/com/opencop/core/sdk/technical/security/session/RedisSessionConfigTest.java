// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;

class RedisSessionConfigTest {

  private final ApplicationContextRunner propertiesContext =
      new ApplicationContextRunner().withUserConfiguration(PropertiesTestConfig.class);

  @Test
  void defaultsConfigureTheRepositoryAndAbsoluteLifetimeCookie() {
    var sessionProperties = new AuthSessionProperties();
    sessionProperties.afterPropertiesSet();
    var cookieProperties = new SessionCookieProperties();
    cookieProperties.setName("OPENCOP_BFF");
    cookieProperties.setPath("/");
    var config = new RedisSessionConfig();
    var repository = new RedisSessionRepository(mock(RedisOperations.class));

    config.sessionRepositoryCustomizer(sessionProperties).customize(repository);
    var cookieSerializer = config.cookieSerializer(cookieProperties, sessionProperties);
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, "session-id"));

    assertThat(sessionProperties.getIdleTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(sessionProperties.getAbsoluteLifetime()).isEqualTo(Duration.ofHours(8));
    Session session = repository.createSession();
    assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
    assertThat(response.getHeader("Set-Cookie"))
        .contains("OPENCOP_BFF=")
        .contains("Max-Age=28800")
        .contains("Expires=");
    assertThat(
            RedisSessionConfig.class.getAnnotation(EnableRedisHttpSession.class).redisNamespace())
        .isEqualTo("${spring.session.redis.namespace:opencop:bff}");
  }

  @Test
  void environmentOverridesBindAtWholeSecondPrecision() {
    propertiesContext
        .withPropertyValues(
            "opencop.auth.session.idle-timeout=45m", "opencop.auth.session.absolute-lifetime=12h")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var properties = context.getBean(AuthSessionProperties.class);
              assertThat(properties.getIdleTimeout()).isEqualTo(Duration.ofMinutes(45));
              assertThat(properties.getAbsoluteLifetime()).isEqualTo(Duration.ofHours(12));
            });
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void invalidDurationsFailStartupAndNameTheOffendingProperty(
      String propertyValue, String expectedProperty) {
    propertiesContext
        .withPropertyValues(propertyValue)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(causeMessages(context.getStartupFailure())).contains(expectedProperty);
            });
  }

  private static Stream<Arguments> invalidDurations() {
    return Stream.of(
        Arguments.of(
            "opencop.auth.session.idle-timeout=0s", AuthSessionProperties.IDLE_TIMEOUT_PROPERTY),
        Arguments.of(
            "opencop.auth.session.absolute-lifetime=500ms",
            AuthSessionProperties.ABSOLUTE_LIFETIME_PROPERTY),
        Arguments.of(
            "opencop.auth.session.absolute-lifetime=30m",
            AuthSessionProperties.ABSOLUTE_LIFETIME_PROPERTY),
        Arguments.of(
            "opencop.auth.session.absolute-lifetime=2147483648s",
            AuthSessionProperties.ABSOLUTE_LIFETIME_PROPERTY));
  }

  private static String causeMessages(Throwable failure) {
    var messages = new StringBuilder();
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      messages.append(cause.getMessage()).append('\n');
    }
    return messages.toString();
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AuthSessionProperties.class)
  static class PropertiesTestConfig {}
}
