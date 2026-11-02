// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

final class OidcTestProvider implements AutoCloseable {

  static final String CLIENT_ID = "test-client";
  static final String SUBJECT = "verified-subject";

  private final HttpServer server;
  private final RSAKey signingKey;
  private final String baseUrl;

  private volatile String nonce;
  private volatile String subject = SUBJECT;
  private volatile Object objectId;
  private volatile List<String> audiences = List.of(CLIENT_ID, "CORE-API");
  private volatile String userInfo = keycloakUserInfo();
  private volatile String tokenRequestBody = "";

  OidcTestProvider() {
    try {
      signingKey = new RSAKeyGenerator(2048).keyID("oidc-test-key").generate();
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      server.createContext("/authorize", exchange -> respond(exchange, 404, ""));
      server.createContext("/token", this::token);
      server.createContext("/jwks", this::jwks);
      server.createContext("/userinfo", this::userInfo);
      server.start();
      // Failsafe reruns the test class in the same JVM after @AfterAll has run.
      Runtime.getRuntime().addShutdownHook(new Thread(this::close, "oidc-test-provider-shutdown"));
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to start the OIDC test provider", exception);
    }
  }

  String issuer() {
    return baseUrl;
  }

  String authorizationUri() {
    return baseUrl + "/authorize";
  }

  String tokenUri() {
    return baseUrl + "/token";
  }

  String jwkSetUri() {
    return baseUrl + "/jwks";
  }

  String userInfoUri() {
    return baseUrl + "/userinfo";
  }

  OidcTestProvider prepareNonce(String nonce) {
    this.nonce = nonce;
    subject = SUBJECT;
    objectId = null;
    audiences = List.of(CLIENT_ID, "CORE-API");
    userInfo = keycloakUserInfo();
    return this;
  }

  OidcTestProvider prepareEntraNonce(String nonce, String subject, String objectId) {
    prepareNonce(nonce);
    this.subject = subject;
    this.objectId = objectId;
    userInfo =
        """
        {
          "sub":"%s",
          "iss":"https://untrusted-user-info.test",
          "oid":"untrusted-user-info-object-id",
          "groups":["entra-group","entra-group"],
          "roles":["operator","operator"],
          "preferred_username":"entra-user",
          "name":"Entra User",
          "given_name":"Entra",
          "family_name":"User",
          "email":"entra.user@example.test",
          "email_verified":true,
          "arbitrary_claim":"must-not-enter-session"
        }
        """
            .formatted(subject);
    return this;
  }

  OidcTestProvider withAudiences(List<String> audiences) {
    this.audiences = List.copyOf(audiences);
    return this;
  }

  OidcTestProvider withObjectId(Object objectId) {
    this.objectId = objectId;
    return this;
  }

  OidcTestProvider withSubject(String subject) {
    this.subject = subject;
    return this;
  }

  OidcTestProvider withUserInfo(String userInfo) {
    this.userInfo = userInfo;
    return this;
  }

  String tokenRequestBody() {
    return tokenRequestBody;
  }

  private void token(HttpExchange exchange) throws IOException {
    tokenRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    try {
      var payload =
          """
          {
            "access_token":"callback-access-token",
            "token_type":"Bearer",
            "expires_in":300,
            "scope":"openid profile email",
            "id_token":"%s"
          }
          """
              .formatted(idToken());
      respondJson(exchange, payload);
    } catch (Exception exception) {
      respond(exchange, 500, "");
    }
  }

  private void jwks(HttpExchange exchange) throws IOException {
    respondJson(exchange, new JWKSet(signingKey.toPublicJWK()).toString());
  }

  private void userInfo(HttpExchange exchange) throws IOException {
    respondJson(exchange, userInfo);
  }

  private String idToken() throws Exception {
    if (nonce == null || nonce.isBlank()) {
      throw new IllegalStateException("Authorization nonce was not prepared");
    }
    var now = Instant.now();
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer())
            .subject(subject)
            .audience(audiences)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(300)))
            .claim("azp", CLIENT_ID)
            .claim("nonce", nonce)
            .jwtID(UUID.randomUUID().toString())
            .claim("oid", objectId)
            .build();
    var jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build(),
            claims);
    jwt.sign(new RSASSASigner(signingKey));
    return jwt.serialize();
  }

  private static String keycloakUserInfo() {
    return """
        {
          "sub":"verified-subject",
          "iss":"https://untrusted-user-info.test",
          "groups":["group-a","group-a"],
          "roles":["operator","operator"],
          "preferred_username":"keycloak-user",
          "name":"Keycloak User",
          "given_name":"Keycloak",
          "family_name":"User",
          "email":"keycloak.user@example.test",
          "email_verified":true,
          "arbitrary_claim":"must-not-enter-session"
        }
        """;
  }

  private static void respondJson(HttpExchange exchange, String body) throws IOException {
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    respond(exchange, 200, body);
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, bytes.length);
    try (var response = exchange.getResponseBody()) {
      response.write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
