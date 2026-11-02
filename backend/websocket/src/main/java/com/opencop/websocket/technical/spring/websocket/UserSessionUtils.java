// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static com.opencop.websocket.technical.spring.security.AuthConstants.ATTR_AUTH;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.socket.WebSocketSession;

public final class UserSessionUtils {

  private UserSessionUtils() {}

  public static UUID getUserIdFromSession(WebSocketSession session) {
    var auth = session.getAttributes().get(ATTR_AUTH);
    if (auth instanceof OAuth2AuthenticationToken oauth
        && oauth.getPrincipal() instanceof OpenCopPrincipal openCopPrincipal) {
      return openCopPrincipal.internalUserId();
    }
    throw new IllegalArgumentException("invalid authentication token");
  }
}
