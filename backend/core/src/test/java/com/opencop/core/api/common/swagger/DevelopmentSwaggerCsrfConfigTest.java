// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DevelopmentSwaggerCsrfConfigTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebMvcAutoConfiguration.class,
                  SpringDocConfiguration.class,
                  SpringDocConfigProperties.class,
                  SwaggerUiConfigProperties.class,
                  SwaggerUiOAuthProperties.class,
                  SwaggerConfig.class))
          .withUserConfiguration(DevelopmentSwaggerCsrfConfig.class)
          .withPropertyValues(
              "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true");

  @Test
  void developmentModeUsesOneAsyncCsrfRequestInterceptor() {
    contextRunner
        .withPropertyValues(
            "opencop.dev.mode=true",
            "springdoc.swagger-ui.csrf.enabled=true",
            "springdoc.swagger-ui.csrf.use-session-storage=true",
            "springdoc.swagger-ui.csrf.session-storage-key=XSRF-TOKEN",
            "springdoc.swagger-ui.csrf.header-name=X-XSRF-TOKEN")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(DevelopmentSwaggerCsrfConfig.class);

              var output =
                  renderSwaggerInitializer(
                      context.getBean(SwaggerIndexTransformer.class),
                      context.getBean(SwaggerUiConfigProperties.class));

              assertThat(count(output, "fetch('../v1/csrf'")).isOne();
              assertThat(count(output, "sessionStorage.setItem('XSRF-TOKEN'")).isOne();
              assertThat(count(output, "requestInterceptor:")).isOne();
              assertThat(output)
                  .contains(
                      "window.onload = function() {",
                      "requestInterceptor: (() => {",
                      "let tokenPromise;",
                      "return async (request) => {",
                      "const method = String(request.method || 'GET').toUpperCase()",
                      "const isUnsafe = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)",
                      "if (!isSameOrigin || !isUnsafe)",
                      "credentials: 'same-origin'",
                      "tokenPromise = acquireCsrfToken()",
                      "request.headers['X-XSRF-TOKEN'] = await tokenPromise")
                  .doesNotContain(
                      "window.onload = async function()",
                      "requestInterceptor: (request) =>",
                      "fetch('/csrf'",
                      "fetch('../csrf'",
                      "window.sessionStorage.getItem('XSRF-TOKEN')",
                      "localStorage.setItem",
                      "document.cookie");
            });
  }

  @Test
  void developmentModeAbortsUnsafeRequestWithBoundedCsrfFailure() {
    contextRunner
        .withPropertyValues(
            "opencop.dev.mode=true",
            "springdoc.swagger-ui.csrf.enabled=true",
            "springdoc.swagger-ui.csrf.use-session-storage=true",
            "springdoc.swagger-ui.csrf.session-storage-key=XSRF-TOKEN",
            "springdoc.swagger-ui.csrf.header-name=X-XSRF-TOKEN")
        .run(
            context -> {
              var output =
                  renderSwaggerInitializer(
                      context.getBean(SwaggerIndexTransformer.class),
                      context.getBean(SwaggerUiConfigProperties.class));

              assertThat(output)
                  .contains(
                      "if (!csrfResponse.ok)",
                      "typeof token !== 'string' || token.trim() === ''",
                      "window.sessionStorage.removeItem('XSRF-TOKEN')",
                      "tokenPromise = undefined",
                      "failure.setAttribute('role', 'alert')",
                      "throw new Error(csrfFailureMessage)",
                      "Swagger could not initialize CSRF protection. Sign in and retry the unsafe operation.");
            });
  }

  @Test
  void defaultModeUsesStandardSwaggerTransformationWithoutBrowserCsrfState() {
    contextRunner
        .withPropertyValues("opencop.dev.mode=false", "springdoc.swagger-ui.csrf.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(DevelopmentSwaggerCsrfConfig.class);

              var output =
                  renderSwaggerInitializer(
                      context.getBean(SwaggerIndexTransformer.class),
                      context.getBean(SwaggerUiConfigProperties.class));

              assertThat(output)
                  .doesNotContain(
                      "fetch('/csrf'",
                      "fetch('../csrf'",
                      "fetch('../v1/csrf'",
                      "sessionStorage.setItem",
                      "localStorage.setItem",
                      "document.cookie",
                      "XSRF-TOKEN");
            });
  }

  @Test
  void localProfileUsesExactSessionStorageCsrfMapping() {
    assertExactCsrfMapping("application-local.yml");
  }

  @Test
  void dockerLocalProfileUsesExactSessionStorageCsrfMapping() {
    assertExactCsrfMapping("application-docker-local.yml");
  }

  private static String renderSwaggerInitializer(
      SwaggerIndexTransformer transformer, SwaggerUiConfigProperties properties) {
    try {
      var resource =
          new ClassPathResource(
              "META-INF/resources/webjars/swagger-ui/"
                  + properties.getVersion()
                  + "/swagger-initializer.js");
      var request = new MockHttpServletRequest();
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      try {
        var transformed = transformer.transform(request, resource, null);
        return transformed.getContentAsString(StandardCharsets.UTF_8);
      } finally {
        RequestContextHolder.resetRequestAttributes();
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void assertExactCsrfMapping(String resourceName) {
    var environment = new StandardEnvironment();
    var resource = new ClassPathResource(resourceName);
    var loader = new YamlPropertySourceLoader();

    try {
      for (var propertySource : loader.load(resourceName, resource)) {
        environment.getPropertySources().addFirst(propertySource);
      }
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }

    var properties =
        Binder.get(environment)
            .bind("springdoc.swagger-ui", Bindable.of(SwaggerUiConfigProperties.class))
            .orElseThrow(
                () -> new IllegalStateException("Springdoc Swagger UI properties missing"));
    var csrf = properties.getCsrf();

    assertThat(csrf.isEnabled()).isTrue();
    assertThat(csrf.isUseSessionStorage()).isTrue();
    assertThat(csrf.getSessionStorageKey()).isEqualTo("XSRF-TOKEN");
    assertThat(csrf.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
    assertThat(environment.containsProperty("springdoc.swagger-ui.csrf.cookie-name")).isFalse();
    assertThat(environment.containsProperty("springdoc.swagger-ui.csrf.use-local-storage"))
        .isFalse();
    assertThat(environment.containsProperty("springdoc.swagger-ui.csrf.local-storage-key"))
        .isFalse();
  }

  private static int count(String value, String fragment) {
    return (value.length() - value.replace(fragment, "").length()) / fragment.length();
  }
}
