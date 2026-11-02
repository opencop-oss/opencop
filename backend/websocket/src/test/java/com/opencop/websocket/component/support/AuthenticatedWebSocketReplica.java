// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.component.support;

import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.socket.WebSocketHttpHeaders;

public final class AuthenticatedWebSocketReplica {

  private AuthenticatedWebSocketReplica() {}

  public static ConfigurableApplicationContext start(Environment environment) {
    Map<String, Object> properties = new HashMap<>();
    copyProperty(properties, environment, "opencop.db.url");
    copyProperty(properties, environment, "opencop.db.username");
    copyProperty(properties, environment, "opencop.db.password");
    copyProperty(properties, environment, "opencop.db.driver");
    copyProperty(properties, environment, "redis.host");
    copyProperty(properties, environment, "redis.port");
    copyProperty(properties, environment, "idp.issuer");
    copyProperty(properties, environment, "spring.flyway.placeholders.postgisExtensionSql");
    copyProperty(properties, environment, "spring.flyway.placeholders.geoFeaturesGeomType");
    copyProperty(properties, environment, "spring.flyway.placeholders.geoFeaturesGeomIndexSql");
    copyProperty(
        properties, environment, "spring.flyway.placeholders.geoFeaturesHistoryChunkIndexSql");
    copyProperty(
        properties,
        environment,
        "spring.flyway.placeholders.geoFeaturesActiveHistoryLookupIndexSql");
    properties.put("redis.ssl", false);
    properties.put("opencop.dev.mode", false);
    properties.put("spring.session.store-type", "redis");
    properties.put("websocket.allowed-origins", "*");
    properties.put("spring.main.banner-mode", "off");
    return new SpringApplicationBuilder(WebSocketComponentTestApplication.class)
        .profiles("it")
        .properties(properties)
        .run("--server.port=0");
  }

  public static String persistSecurityContext(
      SessionRepository repository, OpenCopPrincipal principal, Duration maxInactiveInterval) {
    var authentication =
        new OAuth2AuthenticationToken(
            principal,
            AuthorityUtils.createAuthorityList(
                "OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email"),
            "oidc");
    var securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    Session session = repository.createSession();
    session.setMaxInactiveInterval(maxInactiveInterval);
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    repository.save(session);
    return session.getId();
  }

  public static OpenCopPrincipal principal(UUID userId, Instant authenticatedAt) {
    return new OpenCopPrincipal(
        userId,
        "https://idp.example.test",
        "stable-id",
        "verified-subject",
        new OpenCopPrincipal.Profile(null, null, null, null, null, null),
        17,
        authenticatedAt);
  }

  public static WebSocketHttpHeaders sessionCookieHeaders(
      CookieSerializer cookieSerializer, String sessionId) {
    var request = new MockHttpServletRequest();
    request.setSecure(true);
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(request, response, sessionId));
    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    if (setCookie == null) {
      throw new AssertionError("Session cookie was not written");
    }
    var headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, setCookie.substring(0, setCookie.indexOf(';')));
    return headers;
  }

  public static String url(ConfigurableApplicationContext context) {
    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
    return "ws://127.0.0.1:" + port + "/ws";
  }

  private static void copyProperty(
      Map<String, Object> properties, Environment environment, String name) {
    properties.put(name, environment.getRequiredProperty(name));
  }
}
