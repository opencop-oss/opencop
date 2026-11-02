// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AbstractAuthenticationTargetUrlRequestHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

public class CustomAuthenticationSuccessHandler
    extends AbstractAuthenticationTargetUrlRequestHandler implements AuthenticationSuccessHandler {

  private final URI allowedDomain;

  public CustomAuthenticationSuccessHandler(URI allowedDomain) {
    this.allowedDomain = allowedDomain;
  }

  @Override
  public void onAuthenticationSuccess(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Authentication authentication)
      throws IOException {
    getRedirectStrategy().sendRedirect(request, response, resolveRedirectUri(request).toString());
  }

  private URI resolveRedirectUri(HttpServletRequest request) {
    URI redirectUri = null;
    try {
      var sessionRedirect = (String) request.getSession().getAttribute("validated_redirect_uri");
      if (sessionRedirect != null) {
        var validatedRedirectUri = new URI(sessionRedirect);
        if (!validatedRedirectUri.toString().isEmpty()
            && allowedDomain.getScheme() != null
            && allowedDomain.getScheme().equals(validatedRedirectUri.getScheme())
            && allowedDomain.getAuthority().equals(validatedRedirectUri.getAuthority())) {
          redirectUri = validatedRedirectUri;
        }
      }
    } catch (URISyntaxException _) {
      // Invalid session redirect values fall back to the fixed login success page.
    }
    request.getSession().removeAttribute("validated_redirect_uri");
    return redirectUri == null ? URI.create(allowedDomain + "/login_success") : redirectUri;
  }
}
