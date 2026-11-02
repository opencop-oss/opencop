// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.opencop.websocket.technical.spring.security.interceptor.SessionSecurityContextInterceptor;
import java.security.Principal;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

public class PrincipalHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      @NonNull ServerHttpRequest request,
      @NonNull WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    Object p = attributes.get(SessionSecurityContextInterceptor.ATTR_PRINCIPAL);
    if (p instanceof Principal principal) {
      return principal;
    }
    // fallback to normal behavior (request principal)
    return super.determineUser(request, wsHandler, attributes);
  }
}
