// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class AuditingAccessDeniedHandlerTest {

  private final AuditingAccessDeniedHandler handler = new AuditingAccessDeniedHandler();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void handle_whenAnonymousCsrfException_setsCandidateReasonAndWritesUnauthorizedBody()
      throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    handler.handle(request, response, new MissingCsrfTokenException("X-XSRF-TOKEN"));

    assertEquals(401, response.getStatus());
    assertEquals("application/json", response.getContentType());
    assertEquals(
        "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\"/oauth2/authorization/oidc\"}",
        response.getContentAsString());
    assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
    assertEquals(
        AuthzReasonCode.CSRF_REJECTED,
        request.getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON));
    assertEquals(
        AuthzPrincipalType.ANONYMOUS,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
  }

  @Test
  void handle_whenPlainAccessDenied_setsNoCandidateReasonButWritesBody() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertNull(request.getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON));
    assertEquals(
        AuthzPrincipalType.ANONYMOUS,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertEquals(403, response.getStatus());
    assertEquals("application/json", response.getContentType());
    assertEquals("{\"error\":\"forbidden\"}", response.getContentAsString());
  }

  @Test
  void handle_whenAnonymousToken_setsAnonymousPrincipalType() throws Exception {
    // Defensive coverage: for CSRF denials (this handler's production source)
    // the context is null because CsrfFilter runs before
    // AnonymousAuthenticationFilter — that path is the null-authentication
    // test above. The anonymous token arises only for post-authn
    // authorization denials.
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertEquals(
        AuthzPrincipalType.ANONYMOUS,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
  }

  @Test
  void handle_whenAuthenticated_setsAuthenticatedPrincipalType() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "password"));
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    handler.handle(request, response, new AccessDeniedException("denied"));

    assertEquals(
        AuthzPrincipalType.AUTHENTICATED,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
  }

  @Test
  void handle_whenOpenCopPrincipal_stashesActorIdentity() throws Exception {
    // In-chain denials never reach the attribution filter, so the handler
    // stashes identity itself — authenticated CSRF desyncs are the noisiest
    // population and the identity is knowable here.
    var userId = UUID.fromString("55555555-5555-4555-8555-555555555555");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                openCopPrincipal(userId, "audit.user@example.test"), null, List.of()));
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    handler.handle(request, response, new MissingCsrfTokenException("X-XSRF-TOKEN"));

    assertEquals(
        AuthzPrincipalType.AUTHENTICATED,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertEquals(userId, request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
    assertEquals(
        "audit.user@example.test", request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
    assertEquals(
        "{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}", response.getContentAsString());
  }

  private static OpenCopPrincipal openCopPrincipal(UUID userId, String email) {
    return new OpenCopPrincipal(
        userId,
        "http://localhost/test-issuer",
        "test-subject",
        "test-subject",
        new OpenCopPrincipal.Profile(null, null, null, null, email, null),
        0,
        Instant.EPOCH);
  }
}
