// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.opencop.core.api.common.AppConfig;
import com.opencop.core.api.common.filter.BoundedContentCachingRequestWrapper;
import com.opencop.core.api.common.filter.RequestLoggingFilter;
import com.opencop.core.api.common.filter.RequestPathDebugFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

@ExtendWith(OutputCaptureExtension.class)
class ScimInboundLoggingConfigTest {

  private static final int MAX_LOG_BYTES = 200_000;
  private static final String ENABLED_WARNING =
      "SCIM inbound diagnostics are enabled. Authenticated SCIM identity payloads may be written to application logs; do not enable for routine production use.";

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withUserConfiguration(ScimDiagnosticTestConfig.class)
          .withPropertyValues("opencop.scim.bearer-token=test-scim-token");

  @Test
  void missingDedicatedPropertyRegistersNoScimInboundDiagnostics() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertServletLoggingRequiresDedicatedOptIn();
          assertDiagnosticsAbsent(context);
        });
  }

  @Test
  void dedicatedPropertyFalseRegistersNoScimInboundDiagnostics() {
    contextRunner
        .withPropertyValues("opencop.scim.inbound-logging-enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertDiagnosticsAbsent(context);
            });
  }

  @Test
  void developmentModeDoesNotOverrideDedicatedPropertyFalse() {
    contextRunner
        .withPropertyValues("opencop.dev.mode=true", "opencop.scim.inbound-logging-enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertDiagnosticsAbsent(context);
            });
  }

  @Test
  void applicationConfigurationDefaultsScimInboundLoggingToFalse() {
    var environment = new StandardEnvironment();
    var loader = new YamlPropertySourceLoader();

    try {
      for (var propertySource :
          loader.load("application.yml", new ClassPathResource("application.yml"))) {
        environment.getPropertySources().addFirst(propertySource);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }

    assertThat(environment.getProperty("opencop.scim.inbound-logging-enabled")).isEqualTo("false");
  }

  @Test
  void dedicatedPropertyTrueRegistersAllDiagnosticsAtTheirExistingSeams() {
    contextRunner
        .withPropertyValues("opencop.scim.inbound-logging-enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(RequestPathDebugFilter.class);

              var registration = context.getBean(FilterRegistrationBean.class);
              assertThat(registration.getFilter()).isInstanceOf(RequestLoggingFilter.class);
              assertThat(registration.getUrlPatterns()).containsExactly("/scim/*");
              assertThat(RequestPathDebugFilter.class.getAnnotation(Order.class).value())
                  .isEqualTo(Ordered.HIGHEST_PRECEDENCE)
                  .isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
              assertThat(registration.getOrder())
                  .isGreaterThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);

              var filters = context.getBean(SecurityFilterChain.class).getFilters();
              var inboundLoggingFilter =
                  filters.stream()
                      .filter(ScimInboundLoggingFilter.class::isInstance)
                      .findFirst()
                      .orElseThrow();
              var staticTokenFilter =
                  filters.stream()
                      .filter(ScimStaticTokenFilter.class::isInstance)
                      .findFirst()
                      .orElseThrow();
              assertThat(filters.indexOf(inboundLoggingFilter))
                  .isGreaterThan(filters.indexOf(staticTokenFilter));
            });
  }

  @Test
  void dedicatedPropertyTrueEmitsOperationalWarning(CapturedOutput output) {
    contextRunner
        .withPropertyValues("opencop.scim.inbound-logging-enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(output).contains(ENABLED_WARNING);
            });
  }

  @Test
  void enabledDiagnosticsPreserveTokenStatusAndRequestBodyWithoutInfoPayloads(
      CapturedOutput output) {
    contextRunner
        .withPropertyValues("opencop.scim.inbound-logging-enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var downstreamBody = new AtomicReference<String>();

              var validResponse =
                  executeScimRequest(
                      context, "test-scim-token", "{\"userName\":\"scim-user\"}", downstreamBody);

              assertThat(validResponse.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
              assertThat(downstreamBody).hasValue("{\"userName\":\"scim-user\"}");
              assertThat(output).doesNotContain("{\"userName\":\"scim-user\"}");

              downstreamBody.set(null);
              var invalidResponse =
                  executeScimRequest(context, "wrong-token", "rejected-body", downstreamBody);

              assertThat(invalidResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
              assertThat(downstreamBody).hasValue(null);
              assertThat(output).doesNotContain("rejected-body");
            });
  }

  @Test
  void enabledDiagnosticsBoundCaptureWithoutChangingDownstreamBody() {
    var logger = (Logger) LoggerFactory.getLogger(ScimInboundLoggingFilter.class);
    var previousLevel = logger.getLevel();
    var previousAdditive = logger.isAdditive();
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    try {
      contextRunner
          .withPropertyValues("opencop.scim.inbound-logging-enabled=true")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var body = "x".repeat(MAX_LOG_BYTES + 128);
                var downstreamBody = new AtomicReference<String>();
                var cachingRequest = new AtomicReference<BoundedContentCachingRequestWrapper>();

                var response =
                    executeScimRequest(
                        context, "test-scim-token", body, downstreamBody, cachingRequest);

                assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NO_CONTENT);
                assertThat(downstreamBody).hasValue(body);
                assertThat(cachingRequest)
                    .hasValueSatisfying(
                        request ->
                            assertThat(request.getContentAsByteArray()).hasSize(MAX_LOG_BYTES));
                assertThat(cachingRequest.get().isContentOverflowed()).isTrue();
                assertThat(cachingRequest.get().getCacheLimit()).isEqualTo(MAX_LOG_BYTES);
              });
    } finally {
      logger.setLevel(previousLevel);
      logger.setAdditive(previousAdditive);
    }
  }

  private static MockHttpServletResponse executeScimRequest(
      AssertableWebApplicationContext context,
      String bearerToken,
      String body,
      AtomicReference<String> downstreamBody) {
    return executeScimRequest(context, bearerToken, body, downstreamBody, new AtomicReference<>());
  }

  private static MockHttpServletResponse executeScimRequest(
      AssertableWebApplicationContext context,
      String bearerToken,
      String body,
      AtomicReference<String> downstreamBody,
      AtomicReference<BoundedContentCachingRequestWrapper> cachingRequest) {
    var request = new MockHttpServletRequest("POST", "/scim/v1/provisioning/Users");
    request.setServletPath("/scim/v1/provisioning/Users");
    request.addHeader("Authorization", "Bearer " + bearerToken);
    request.setCharacterEncoding(StandardCharsets.UTF_8);
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse();

    FilterChain downstream =
        (servletRequest, servletResponse) -> {
          var httpRequest = (HttpServletRequest) servletRequest;
          downstreamBody.set(
              new String(httpRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
          cachingRequest.set(findBoundedContentCache(httpRequest));
          ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
    FilterChain servletLogging =
        (servletRequest, servletResponse) ->
            context
                .getBean(FilterRegistrationBean.class)
                .getFilter()
                .doFilter(servletRequest, servletResponse, downstream);
    FilterChain security =
        (servletRequest, servletResponse) ->
            context
                .getBean(FilterChainProxy.class)
                .doFilter(servletRequest, servletResponse, servletLogging);

    try {
      context.getBean(RequestPathDebugFilter.class).doFilter(request, response, security);
      return response;
    } catch (Exception exception) {
      throw new AssertionError("SCIM diagnostic filter chain failed", exception);
    }
  }

  private static BoundedContentCachingRequestWrapper findBoundedContentCache(
      HttpServletRequest request) {
    HttpServletRequest current = request;
    while (current instanceof HttpServletRequestWrapper wrapper) {
      if (current instanceof BoundedContentCachingRequestWrapper boundedRequest) {
        return boundedRequest;
      }
      if (!(wrapper.getRequest() instanceof HttpServletRequest nestedRequest)) {
        return null;
      }
      current = nestedRequest;
    }
    return current instanceof BoundedContentCachingRequestWrapper boundedRequest
        ? boundedRequest
        : null;
  }

  private static void assertDiagnosticsAbsent(AssertableWebApplicationContext context) {
    assertThat(context).doesNotHaveBean(RequestPathDebugFilter.class);
    assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
        .noneMatch(registration -> registration.getFilter() instanceof RequestLoggingFilter);
    assertThat(context.getBean(SecurityFilterChain.class).getFilters())
        .noneMatch(ScimInboundLoggingFilter.class::isInstance);
  }

  private static void assertServletLoggingRequiresDedicatedOptIn() {
    try {
      var condition =
          AppConfig.class.getMethod("loggingFilter").getAnnotation(ConditionalOnProperty.class);

      assertThat(condition).isNotNull();
      assertThat(condition.prefix()).isEqualTo("opencop.scim");
      assertThat(condition.name()).containsExactly("inbound-logging-enabled");
      assertThat(condition.havingValue()).isEqualTo("true");
      assertThat(condition.matchIfMissing()).isFalse();
    } catch (NoSuchMethodException exception) {
      throw new AssertionError("AppConfig logging filter registration is missing", exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSecurity
  @Import({ScimClientConfig.class, RequestPathDebugFilter.class})
  static class ScimDiagnosticTestConfig {

    @Bean
    @ConditionalOnProperty(
        prefix = "opencop.scim",
        name = "inbound-logging-enabled",
        havingValue = "true")
    FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
      return new AppConfig().loggingFilter();
    }
  }
}
