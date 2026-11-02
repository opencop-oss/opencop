// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuth2StateFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(OAuth2StateFilter.class);

  private final URI allowedDomain;
  private final String authorizationPath;

  public OAuth2StateFilter(URI allowedDomain, String authorizationPath) {
    this.allowedDomain = allowedDomain;
    this.authorizationPath = authorizationPath;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    // Request prefix issues mean we should not check with `getRequestURI()`.
    // Traefik appears NOT to be stripping /api *but* it does set X-Forwarded-Prefix: /api
    // so Spring sets that as the context path and thus the servlet path does NOT include /api.
    log.trace(
        "OAuth2StateFilter: servletPath={}, requestURI={}, contextPath={}",
        request.getServletPath(), // does NOT have "/api"
        request.getRequestURI(), // does have "/api"
        request.getContextPath()); // "/api"

    if (!request.getServletPath().startsWith(authorizationPath)) {
      filterChain.doFilter(request, response);
      return;
    }

    // Check if the request contains a redirect_uri parameter
    String redirectUri = request.getParameter("redirect_uri");
    log.debug("OAuth2StateFilter: redirect_uri param={}", redirectUri);

    if (redirectUri != null) {
      try {
        URI uri = new URI(redirectUri);
        if (uri.isAbsolute()) {
          // absolute URL
          if (!allowedDomain.getScheme().equals(uri.getScheme())
              || !allowedDomain.getAuthority().equals(uri.getAuthority())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid redirect URI domain");
            return;
          }
        } else {
          // relative URL
          redirectUri = allowedDomain + redirectUri;
        }

        request.getSession().setAttribute("validated_redirect_uri", redirectUri);
        log.debug("OAuth2StateFilter: stored validated_redirect_uri={} in session", redirectUri);
      } catch (URISyntaxException _) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid redirect URI format");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
