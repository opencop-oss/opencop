// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import com.opencop.core.api.common.filter.BoundedContentCachingRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ScimInboundLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ScimInboundLoggingFilter.class);
  private static final int MAX_LOG_BYTES = 200_000;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith("/scim/");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String method = request.getMethod();
    String path =
        request.getRequestURI()
            + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
    boolean mayHaveBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

    log.info("SCIM INBOUND {} {}", method, path);
    if (!mayHaveBody || !log.isDebugEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    var cachedRequest =
        request instanceof BoundedContentCachingRequestWrapper boundedRequest
            ? boundedRequest
            : new BoundedContentCachingRequestWrapper(request, MAX_LOG_BYTES);
    try {
      chain.doFilter(cachedRequest, response);
    } finally {
      log.debug("SCIM INBOUND PAYLOAD {} {}\n{}", method, path, toStringTruncated(cachedRequest));
    }
  }

  private static String toStringTruncated(BoundedContentCachingRequestWrapper request) {
    byte[] body = request.getContentAsByteArray();
    if (body.length == 0) {
      return "";
    }
    String encoding = request.getCharacterEncoding();
    String cs = encoding != null ? encoding : StandardCharsets.UTF_8.name();
    String s;
    try {
      s = new String(body, cs);
    } catch (Exception _) {
      s = new String(body, StandardCharsets.UTF_8);
    }
    if (request.isContentOverflowed()) {
      s += "\n... (truncated at " + request.getCacheLimit() + " bytes)";
    }
    return s;
  }
}
