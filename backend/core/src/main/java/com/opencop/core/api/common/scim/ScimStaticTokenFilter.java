// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ScimStaticTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(ScimStaticTokenFilter.class);

  private final byte[] expectedTokenBytes;

  ScimStaticTokenFilter(String expectedToken) {
    this.expectedTokenBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getServletPath().startsWith("/scim/");
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
      throws IOException, ServletException {

    String token = resolve(req);

    if (token == null
        || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedTokenBytes)) {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    var principal = new UsernamePasswordAuthenticationToken("scim", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(principal);

    chain.doFilter(req, res);
  }

  private static String resolve(HttpServletRequest request) {
    String auth = request.getHeader("Authorization");
    if (auth == null) {
      return null;
    }

    if (auth.length() < 7 || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }

    String token = auth.substring(7);

    if (token.isEmpty()) {
      return null;
    }

    // Reject whitespace in token
    for (int i = 0; i < token.length(); i++) {
      if (Character.isWhitespace(token.charAt(i))) {
        return null;
      }
    }

    return token;
  }
}
