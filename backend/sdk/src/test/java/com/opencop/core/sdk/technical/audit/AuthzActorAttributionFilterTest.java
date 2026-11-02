// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AuthzActorAttributionFilterTest {

  private static final UUID USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

  private final AuthzActorAttributionFilter filter = new AuthzActorAttributionFilter();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_authenticatedOpenCopPrincipal_stashesActorAndPrincipalType() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(openCopAuthentication("audit.user@example.test"));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertEquals(
        AuthzPrincipalType.AUTHENTICATED,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertEquals(USER_ID, request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
    assertEquals(
        "audit.user@example.test", request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
  }

  @Test
  void doFilter_stashesSanitizedEmail() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(openCopAuthentication(" audit.user\n<x>@example.test "));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    // Attributes never hold the raw claim.
    assertEquals(
        "audit.user_x_@example.test",
        request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
  }

  @Test
  void doFilter_principalWithoutEmailClaim_stashesActorWithoutEmail() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(openCopAuthentication(null));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertEquals(USER_ID, request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
  }

  @Test
  void doFilter_anonymousAuthentication_stashesAnonymousWithoutActor() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertEquals(
        AuthzPrincipalType.ANONYMOUS,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
  }

  @Test
  void doFilter_anonymousOnLaterDispatch_clearsPreviouslyStashedActor() throws Exception {
    // Session death between dispatches must never pair an anonymous principal
    // type with stale identity from an earlier pass.
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");
    request.setAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID, USER_ID);
    request.setAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL, "audit.user@example.test");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertEquals(
        AuthzPrincipalType.ANONYMOUS,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL));
  }

  @Test
  void doFilter_noAuthentication_stashesNothing() throws Exception {
    SecurityContextHolder.clearContext();
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertNull(request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
  }

  @Test
  void doFilter_nonOpenCopPrincipal_stashesPrincipalTypeOnly() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "password"));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");

    filter.doFilter(request, new MockHttpServletResponse(), chain());

    assertEquals(
        AuthzPrincipalType.AUTHENTICATED,
        request.getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE));
    assertNull(request.getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID));
  }

  @Test
  void doFilter_whenExtractionThrows_neverAffectsTheRequest() throws Exception {
    var failingDelegate = mock(OidcUser.class);
    when(failingDelegate.getEmail()).thenThrow(new IllegalStateException("claims unavailable"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                // AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove this compatibility-path test after
                // no deployed release can write the legacy representation and eight hours have
                // elapsed since the last legacy writer left service. A rollback resets that gate.
                new OpenCopPrincipal(USER_ID, failingDelegate), null, List.of()));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");
    var chainRan = new AtomicBoolean(false);
    FilterChain chain = (_, _) -> chainRan.set(true);

    assertDoesNotThrow(() -> filter.doFilter(request, new MockHttpServletResponse(), chain));

    assertTrue(chainRan.get());
  }

  private static UsernamePasswordAuthenticationToken openCopAuthentication(String email) {
    var principal =
        new OpenCopPrincipal(
            USER_ID,
            "http://localhost/test-issuer",
            "test-subject",
            "test-subject",
            new OpenCopPrincipal.Profile(null, null, null, null, email, null),
            0,
            Instant.EPOCH);
    return new UsernamePasswordAuthenticationToken(principal, null, List.of());
  }

  private static FilterChain chain() {
    return (_, _) -> {};
  }
}
