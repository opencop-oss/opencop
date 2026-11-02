// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestLoggingFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final int MAX_LOG_BYTES = 200_000;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;

    log.info(
        "Request Method: {} | Request URI: {}",
        httpRequest.getMethod(),
        httpRequest.getRequestURI());

    if (!mayHaveBody(httpRequest.getMethod()) || !log.isDebugEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    var existingCache = findBoundedContentCache(httpRequest);
    var cachedRequest =
        existingCache.orElseGet(
            () -> new BoundedContentCachingRequestWrapper(httpRequest, MAX_LOG_BYTES));
    var requestToFilter = existingCache.isPresent() ? request : cachedRequest;
    try {
      chain.doFilter(requestToFilter, response);
    } finally {
      var requestBody = cachedRequest.getContentAsString();
      if (cachedRequest.isContentOverflowed()) {
        requestBody += "\n... (truncated at " + cachedRequest.getCacheLimit() + " bytes)";
      }
      // careful with level, requestBody may have sensitive details
      log.debug("Request Body: {}", requestBody);
    }
  }

  private static boolean mayHaveBody(String method) {
    return "POST".equals(method)
        || "PUT".equals(method)
        || "PATCH".equals(method)
        || "DELETE".equals(method);
  }

  private static Optional<BoundedContentCachingRequestWrapper> findBoundedContentCache(
      HttpServletRequest request) {
    HttpServletRequest current = request;
    while (true) {
      if (current instanceof BoundedContentCachingRequestWrapper boundedRequest) {
        return Optional.of(boundedRequest);
      }
      if (current instanceof HttpServletRequestWrapper wrapper
          && wrapper.getRequest() instanceof HttpServletRequest nestedRequest) {
        current = nestedRequest;
        continue;
      }
      return Optional.empty();
    }
  }

  @Override
  public void destroy() {}
}
