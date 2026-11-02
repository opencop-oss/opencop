// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Derives the audit action — the request line "METHOD /route/{template}" — from servlet state.
 *
 * <p>The route template (Spring's best-matching pattern, the same value http.server.requests tags
 * by) is server-controlled and bounded by the deployed route table; it is never raw user input.
 * When no handler matched (e.g. CSRF rejections inside the security chain) there is no template and
 * the action stays null → the event clamps it to UNKNOWN.
 */
public final class AuthzRequestActions {

  private AuthzRequestActions() {}

  /** Action for the given request, or null when no route template is available. */
  public static String routeAction(HttpServletRequest request) {
    var pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern == null) {
      return null;
    }
    return sanitize(request.getMethod() + " " + pattern);
  }

  // Defensive only — both inputs are server-controlled. Charset mirrors
  // AuthzDenialEvent.sanitizeAction so the value is stable whether it flows
  // through event construction (backstop) or straight to the reporter (rich).
  private static String sanitize(String value) {
    var builder = new StringBuilder();
    value
        .strip()
        .codePoints()
        .limit(128)
        .forEach(
            codePoint -> {
              boolean allowed =
                  (codePoint >= 'A' && codePoint <= 'Z')
                      || (codePoint >= 'a' && codePoint <= 'z')
                      || (codePoint >= '0' && codePoint <= '9')
                      || codePoint == ' '
                      || codePoint == '/'
                      || codePoint == '{'
                      || codePoint == '}'
                      || codePoint == '.'
                      || codePoint == '_'
                      || codePoint == '-';
              builder.appendCodePoint(allowed ? codePoint : '_');
            });
    return builder.toString();
  }

  /** Action for the current request thread, or null outside a request or before route matching. */
  public static String currentRouteAction() {
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes servletAttributes) {
      return routeAction(servletAttributes.getRequest());
    }
    return null;
  }
}
