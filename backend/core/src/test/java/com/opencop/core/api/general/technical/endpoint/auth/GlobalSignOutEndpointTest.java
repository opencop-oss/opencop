// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.general.business.model.user.AccountMutationState;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.usecase.user.GlobalSignOutUsecase;
import com.opencop.core.api.general.ports.gateway.AccountMutationGateway;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.web.http.DefaultCookieSerializer;

class GlobalSignOutEndpointTest {

  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final UUID USER_ID = UUID.fromString("a06c1a4d-b8f5-401d-a6f1-59a12147a76e");
  private static final String SENSITIVE_FAILURE_DETAILS =
      "database unavailable for session=private-session cookie=OPENCOP_BFF=private-value";

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void captureEndpointLogs() {
    logger = (Logger) LoggerFactory.getLogger(GlobalSignOutEndpoint.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void successfulSignOutInvalidatesCurrentSessionClearsConfiguredCookieAndReturnsNoContent() {
    var endpoint = endpoint(new NoOpCacheGateway());
    var principal = TestPrincipals.oidcPrincipal(USER_ID, 7);
    var authentication = authentication(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    var response = new MockHttpServletResponse();

    var result = endpoint.signOut(principal, request, response, authentication);

    assertEquals(204, result.getStatusCode().value());
    assertFalse(result.getHeaders().containsHeader("Location"));
    assertTrue(session.isInvalid());
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertLogoutCookiesCleared(response);
    assertTrue(appender.list.isEmpty());
  }

  @Test
  void requiredCacheFailureReturnsRetryableResponseWithoutLogoutCleanup() {
    var endpoint =
        endpoint(new FailingCacheGateway(new IllegalStateException(SENSITIVE_FAILURE_DETAILS)));
    var principal = TestPrincipals.oidcPrincipal(USER_ID, 7);
    var authentication = authentication(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    var response = new MockHttpServletResponse();

    ResponseEntity<?> failedResponse;
    try (var ignored = Span.wrap(traceContext()).makeCurrent()) {
      failedResponse = endpoint.signOut(principal, request, response, authentication);
    }

    assertUnavailableWithoutLogoutCleanup(failedResponse, response, session, authentication);
    assertSingleSafeFailureLog(
        "GLOBAL_SIGN_OUT_FAILED operation=global_sign_out"
            + " stage=required exception_class=IllegalStateException trace_id="
            + TRACE_ID);
  }

  @Test
  void currentSessionInvalidationFailureStillClearsContextCookiesAndReturnsNoContent() {
    var endpoint = endpoint(new NoOpCacheGateway());
    var principal = TestPrincipals.oidcPrincipal(USER_ID, 7);
    var authentication = authentication(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    var session = mock(HttpSession.class);
    doThrow(new IllegalStateException(SENSITIVE_FAILURE_DETAILS)).when(session).invalidate();
    var request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(session);
    when(request.getContextPath()).thenReturn("/api");
    var response = new MockHttpServletResponse();

    ResponseEntity<?> result;
    try (var ignored = Span.wrap(traceContext()).makeCurrent()) {
      result = endpoint.signOut(principal, request, response, authentication);
    }

    assertEquals(204, result.getStatusCode().value());
    assertFalse(result.getHeaders().containsHeader("Location"));
    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertLogoutCookiesCleared(response);
    assertSingleSafeFailureLog(
        "GLOBAL_SIGN_OUT_FAILED operation=global_sign_out"
            + " stage=current_session exception_class=IllegalStateException trace_id="
            + TRACE_ID);
  }

  private static Authentication authentication(
      com.opencop.core.sdk.technical.security.OpenCopPrincipal principal) {
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, "not-used", principal.getAuthorities());
  }

  private static GlobalSignOutEndpoint endpoint(CurrentAccountAccessCacheGateway cacheGateway) {
    var usecase =
        new GlobalSignOutUsecase(
            new InMemoryAccountMutationGateway(), new AccountMutationPolicy(""), cacheGateway);
    return new GlobalSignOutEndpoint(usecase, sessionCookieSerializer());
  }

  private static DefaultCookieSerializer sessionCookieSerializer() {
    var serializer = new DefaultCookieSerializer();
    serializer.setCookieName("OPENCOP_BFF");
    serializer.setCookiePath("/");
    return serializer;
  }

  private static void assertLogoutCookiesCleared(MockHttpServletResponse response) {
    assertTrue(
        response.getHeaders("Set-Cookie").stream()
            .anyMatch(header -> header.startsWith("OPENCOP_BFF=") && header.contains("Max-Age=0")));
    assertTrue(
        response.getHeaders("Set-Cookie").stream()
            .noneMatch(header -> header.startsWith("XSRF-TOKEN=")));
    assertNull(response.getCookie("XSRF-TOKEN"));
  }

  private static void assertUnavailableWithoutLogoutCleanup(
      ResponseEntity<?> failedResponse,
      MockHttpServletResponse servletResponse,
      MockHttpSession session,
      Authentication authentication) {
    assertEquals(503, failedResponse.getStatusCode().value());
    assertEquals(
        new GlobalErrorResponse(
            "GLOBAL_SIGN_OUT_UNAVAILABLE",
            "Global sign-out is temporarily unavailable. Please retry."),
        failedResponse.getBody());
    assertFalse(failedResponse.getHeaders().containsHeader("Location"));
    assertTrue(servletResponse.getHeaders("Set-Cookie").isEmpty());
    assertFalse(session.isInvalid());
    assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
  }

  private void assertSingleSafeFailureLog(String expectedMessage) {
    assertEquals(1, appender.list.size());
    var failureLog = appender.list.getFirst();
    assertEquals(expectedMessage, failureLog.getFormattedMessage());
    assertNull(failureLog.getThrowableProxy());
    assertFalse(failureLog.getFormattedMessage().contains(SENSITIVE_FAILURE_DETAILS));
    assertFalse(failureLog.getFormattedMessage().contains("private-session"));
    assertFalse(failureLog.getFormattedMessage().contains("OPENCOP_BFF"));
    assertFalse(failureLog.getFormattedMessage().contains("private-value"));
  }

  private static SpanContext traceContext() {
    return SpanContext.create(
        TRACE_ID, "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());
  }

  private static final class InMemoryAccountMutationGateway implements AccountMutationGateway {

    private AccountMutationState account =
        new AccountMutationState(USER_ID, "user@example.com", true, false, 7, 0, null);

    @Override
    public Optional<AccountMutationState> findByInternalUserIdForUpdate(UUID internalUserId) {
      return Optional.of(account);
    }

    @Override
    public void saveAndFlush(AccountMutationState account) {
      this.account = account;
    }
  }

  private static final class NoOpCacheGateway implements CurrentAccountAccessCacheGateway {

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      return Optional.empty();
    }

    @Override
    public void populate(CurrentAccountAccess access) {}

    @Override
    public void evict(UUID internalUserId) {}

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {}
  }

  private record FailingCacheGateway(RuntimeException failure)
      implements CurrentAccountAccessCacheGateway {

    @Override
    public Optional<CurrentAccountAccess> findByInternalUserId(UUID internalUserId) {
      return Optional.empty();
    }

    @Override
    public void populate(CurrentAccountAccess access) {}

    @Override
    public void evict(UUID internalUserId) {
      throw failure;
    }

    @Override
    public void scheduleEvictionAfterCommit(UUID internalUserId) {}
  }
}
