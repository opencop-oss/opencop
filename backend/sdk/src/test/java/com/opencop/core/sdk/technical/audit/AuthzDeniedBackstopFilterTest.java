// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import com.opencop.core.sdk.ports.audit.AuthzDenialEvent;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.audit.AuthzResourceType;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthzDeniedBackstopFilterTest {

  private final RecordingReporter reporter = new RecordingReporter();
  private final AuthzDeniedBackstopFilter filter = new AuthzDeniedBackstopFilter(reporter);

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_when403WithoutMarker_reportsGenericDenial() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertEquals(1, reporter.events.size());
    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.UNKNOWN, event.principalType());
    assertEquals(AuthzReasonCode.UNATTRIBUTED, event.reasonCode());
    assertEquals(AuthzResourceType.UNKNOWN, event.resource().type());
    assertEquals("method=GET,path=/v1/events/source/object/attachments", event.resource().id());
    // No handler matched in this arrangement, so the request-line action is
    // honestly UNKNOWN.
    assertEquals("UNKNOWN", event.action());
  }

  @Test
  void doFilter_whenRouteMatched_reportsRequestLineAction() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");
    request.setAttribute(
        org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
        "/v1/events/{sourceId}/{objectId}/remarks");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertEquals(
        "GET /v1/events/{sourceId}/{objectId}/remarks", reporter.events.getFirst().action());
  }

  @Test
  void doFilter_when403AlreadyMarked_reportsNothing() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    request.setAttribute(AuthzAuditRequestAttributes.REPORTED, Boolean.TRUE);
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertTrue(reporter.events.isEmpty());
  }

  @Test
  void doFilter_whenNon403_reportsNothing() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(404));
    filter.doFilter(
        new MockHttpServletRequest("GET", "/error"),
        new MockHttpServletResponse(),
        statusChain(500));

    assertTrue(reporter.events.isEmpty());
  }

  @Test
  void doFilter_honorsCandidateReasonAndPrincipalTypeAttributes() throws Exception {
    var request = new MockHttpServletRequest("POST", "/v1/events/source/object/attachments");
    request.setAttribute(
        AuthzAuditRequestAttributes.CANDIDATE_REASON, AuthzReasonCode.CSRF_REJECTED);
    request.setAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE, AuthzPrincipalType.ANONYMOUS);
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertEquals(1, reporter.events.size());
    assertEquals(AuthzReasonCode.CSRF_REJECTED, reporter.events.getFirst().reasonCode());
    assertEquals(AuthzPrincipalType.ANONYMOUS, reporter.events.getFirst().principalType());
  }

  // Per-path attribution pins (alignment watch-item): what the backstop
  // actually captures per path, honestly. UNKNOWN is acceptable; silence is
  // not. Actor identity appears only when the in-chain attribution filter
  // stashed it — the backstop itself never extracts identity.

  @Test
  void doFilter_clearedContextNoPrincipal_reportsUnknownWithoutActor() throws Exception {
    SecurityContextHolder.clearContext();
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.UNKNOWN, event.principalType());
    assertNull(event.actorUserId());
  }

  @Test
  void doFilter_authenticatedContext_reportsAuthenticatedWithoutActor() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "password"));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.AUTHENTICATED, event.principalType());
    assertNull(event.actorUserId());
  }

  @Test
  void doFilter_anonymousAuthentication_reportsAnonymous() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertEquals(AuthzPrincipalType.ANONYMOUS, reporter.events.getFirst().principalType());
  }

  @Test
  void doFilter_containerUserPrincipalOnly_reportsAuthenticatedWithoutActor() throws Exception {
    SecurityContextHolder.clearContext();
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    request.setUserPrincipal(() -> "container-user");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.AUTHENTICATED, event.principalType());
    assertNull(event.actorUserId());
  }

  @Test
  void doFilter_withStashedActorAttribution_reportsActorOnGenericEvent() throws Exception {
    // The in-chain AuthzActorAttributionFilter stashes these on the way in;
    // the backstop must surface them on otherwise-generic denials.
    var actorId = java.util.UUID.fromString("44444444-4444-4444-8444-444444444444");
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/remarks");
    request.setAttribute(
        AuthzAuditRequestAttributes.PRINCIPAL_TYPE, AuthzPrincipalType.AUTHENTICATED);
    request.setAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID, actorId);
    request.setAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL, "audit.user@example.test");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.AUTHENTICATED, event.principalType());
    assertEquals(actorId, event.actorUserId());
    assertEquals("audit.user@example.test", event.actorEmail());
    assertEquals(AuthzReasonCode.UNATTRIBUTED, event.reasonCode());
  }

  @Test
  void doFilter_whenEventConstructionFails_reportsAllUnknownFallback() throws Exception {
    var request =
        new MockHttpServletRequest("GET", "/v1/events/source/object/attachments") {
          @Override
          public String getMethod() {
            throw new IllegalStateException("boom");
          }
        };
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    var event = reporter.events.getFirst();
    assertEquals(AuthzPrincipalType.UNKNOWN, event.principalType());
    assertEquals(AuthzReasonCode.UNATTRIBUTED, event.reasonCode());
    assertEquals("method=UNKNOWN,path=UNKNOWN", event.resource().id());
  }

  @Test
  void doFilter_whenSendError403_reportsExactlyOnce() throws Exception {
    var request = new MockHttpServletRequest("POST", "/v1/scim/Users");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (_, servletResponse) -> ((MockHttpServletResponse) servletResponse).sendError(403);

    filter.doFilter(request, response, chain);

    assertEquals(1, reporter.events.size());
  }

  @Test
  void doFilter_marksRequestReportedAfterGenericEmission() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    // A later ASYNC-dispatch re-run of this filter must see the marker and
    // not double-emit.
    assertEquals(Boolean.TRUE, request.getAttribute(AuthzAuditRequestAttributes.REPORTED));
  }

  @Test
  void doFilter_whenAsyncStarted_skipsEmissionUntilFinalDispatch() throws Exception {
    var request = new MockHttpServletRequest("GET", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (servletRequest, servletResponse) -> {
          ((MockHttpServletRequest) servletRequest).setAsyncStarted(true);
          ((MockHttpServletResponse) servletResponse).setStatus(403);
        };

    filter.doFilter(request, response, chain);
    assertTrue(reporter.events.isEmpty());

    // Final (ASYNC) dispatch unwind: async no longer started, status final.
    request.setAsyncStarted(false);
    request.setDispatcherType(DispatcherType.ASYNC);
    filter.doFilter(request, response, statusChain(403));

    assertEquals(1, reporter.events.size());
  }

  @Test
  void doFilter_errorDispatch_isSkippedSoNoDoubleEmission() throws Exception {
    // The REQUEST-dispatch unwind already observes sendError's 403, so the
    // ERROR dispatch must not run the filter again (OncePerRequestFilter
    // default, pinned here behaviorally).
    var request = new MockHttpServletRequest("GET", "/error");
    request.setDispatcherType(DispatcherType.ERROR);
    request.setAttribute("jakarta.servlet.error.request_uri", "/v1/events/source/object");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, statusChain(403));

    assertTrue(reporter.events.isEmpty());
  }

  @Test
  void doFilter_whenChainThrows_stillReportsCurrent403AndPropagatesException() {
    var request = new MockHttpServletRequest("DELETE", "/v1/events/source/object/attachments");
    var response = new MockHttpServletResponse();
    FilterChain chain =
        (_, servletResponse) -> {
          ((MockHttpServletResponse) servletResponse).setStatus(403);
          throw new ServletException("boom");
        };

    assertThrows(ServletException.class, () -> filter.doFilter(request, response, chain));

    assertEquals(1, reporter.events.size());
    assertEquals(AuthzReasonCode.UNATTRIBUTED, reporter.events.getFirst().reasonCode());
  }

  private static FilterChain statusChain(int status) {
    return (_, response) -> ((MockHttpServletResponse) response).setStatus(status);
  }

  private static final class RecordingReporter implements AuthzAuditReporting {
    private final List<AuthzDenialEvent> events = new ArrayList<>();

    @Override
    public void reportDenied(AuthzDenialEvent event) {
      events.add(event);
    }
  }
}
