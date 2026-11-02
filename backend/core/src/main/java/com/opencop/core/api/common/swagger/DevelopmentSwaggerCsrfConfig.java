// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.swagger;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NonNull;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

@Component
@ConditionalOnProperty(name = "opencop.dev.mode", havingValue = "true")
public class DevelopmentSwaggerCsrfConfig extends SwaggerIndexPageTransformer {

  private static final String SWAGGER_INITIALIZER = "swagger-initializer.js";
  private static final String STANDARD_INTERCEPTOR_START = "requestInterceptor: (request) => {";
  private static final String PRESETS_START = "presets: [";
  private static final String DEVELOPMENT_INTERCEPTOR =
      """
      requestInterceptor: (() => {
        const csrfFailureMessage = 'Swagger could not initialize CSRF protection. Sign in and retry the unsafe operation.';
        let tokenPromise;

        const showCsrfFailure = () => {
          let failure = document.getElementById('opencop-swagger-csrf-failure');
          if (!failure) {
            failure = document.createElement('div');
            failure.id = 'opencop-swagger-csrf-failure';
            failure.setAttribute('role', 'alert');
            failure.style.cssText = 'background:#fee2e2;color:#991b1b;padding:12px 16px;font-family:sans-serif';
            failure.textContent = csrfFailureMessage;
            document.body.prepend(failure);
          }
        };

        const acquireCsrfToken = async () => {
          const csrfResponse = await fetch('../v1/csrf', {
            method: 'GET',
            credentials: 'same-origin'
          });
          if (!csrfResponse.ok) {
            throw new Error('CSRF acquisition request failed');
          }

          const { token } = await csrfResponse.json();
          if (typeof token !== 'string' || token.trim() === '') {
            throw new Error('CSRF acquisition response did not contain a token');
          }

          window.sessionStorage.setItem('XSRF-TOKEN', token);
          document.getElementById('opencop-swagger-csrf-failure')?.remove();
          return token;
        };

        window.sessionStorage.removeItem('XSRF-TOKEN');

        return async (request) => {
          const currentURL = new URL(document.URL);
          const requestURL = new URL(request.url, document.location.origin);
          const isSameOrigin = currentURL.origin === requestURL.origin;
          const method = String(request.method || 'GET').toUpperCase();
          const isUnsafe = !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method);
          if (!isSameOrigin || !isUnsafe) {
            return request;
          }

          if (!tokenPromise) {
            tokenPromise = acquireCsrfToken();
          }

          try {
            request.headers['X-XSRF-TOKEN'] = await tokenPromise;
            return request;
          } catch {
            tokenPromise = undefined;
            window.sessionStorage.removeItem('XSRF-TOKEN');
            console.error(csrfFailureMessage);
            showCsrfFailure();
            throw new Error(csrfFailureMessage);
          }
        };
      })(),
      presets: [
      """;

  public DevelopmentSwaggerCsrfConfig(
      SwaggerUiConfigProperties swaggerUiConfig,
      SwaggerUiOAuthProperties swaggerUiOAuthProperties,
      SwaggerWelcomeCommon swaggerWelcomeCommon,
      ObjectMapperProvider objectMapperProvider) {
    super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
  }

  @Override
  public @NonNull Resource transform(
      HttpServletRequest request, Resource resource, ResourceTransformerChain transformerChain)
      throws IOException {
    var transformed = super.transform(request, resource, transformerChain);
    if (!SWAGGER_INITIALIZER.equals(resource.getFilename())) {
      return transformed;
    }

    var initializer = transformed.getContentAsString(StandardCharsets.UTF_8);
    var developmentInitializer = replaceSessionStorageInterceptor(initializer);
    return new TransformedResource(
        transformed, developmentInitializer.getBytes(StandardCharsets.UTF_8));
  }

  private static String replaceSessionStorageInterceptor(String source) {
    var interceptorIndex = source.indexOf(STANDARD_INTERCEPTOR_START);
    var presetsIndex = source.indexOf(PRESETS_START, interceptorIndex);
    if (interceptorIndex < 0
        || interceptorIndex != source.lastIndexOf(STANDARD_INTERCEPTOR_START)
        || presetsIndex < 0
        || presetsIndex != source.lastIndexOf(PRESETS_START)) {
      throw new IllegalStateException("Unexpected Swagger initializer request interceptor");
    }

    return source.substring(0, interceptorIndex)
        + DEVELOPMENT_INTERCEPTOR
        + source.substring(presetsIndex + PRESETS_START.length());
  }
}
