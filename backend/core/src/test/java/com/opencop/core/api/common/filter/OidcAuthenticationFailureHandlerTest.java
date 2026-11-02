// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

class OidcAuthenticationFailureHandlerTest {

  private Logger logger;
  private Level originalLevel;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(OidcAuthenticationFailureHandler.class);
    originalLevel = logger.getLevel();
    logger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void unexpectedFailureRetainsARedactedStackAtProductionLogLevel() throws Exception {
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    request.setRequestedSessionId(session.getId());
    var response = new MockHttpServletResponse();
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("principal", "credentials"));
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error("opencop_login_failed"),
            "safe outer message",
            new IllegalStateException("sensitive-claim-value"));

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(request, response, exception);

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/login_failed");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(session.isInvalid()).isTrue();
    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage())
        .contains(
            "unexpected_login_failure",
            "causeType=java.lang.IllegalStateException",
            "sessionValid=true",
            "cookiePresent=false");
    assertThat(event.getFormattedMessage()).doesNotContain("sensitive-claim-value");
    assertThat(event.getThrowableProxy()).isNotNull();
    assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
        .contains(
            "OIDC authentication failure details redacted",
            "unexpectedFailureRetainsARedactedStackAtProductionLogLevel")
        .doesNotContain("sensitive-claim-value", "safe outer message");
  }

  @Test
  void failedReloginPreservesTheEstablishedAuthenticatedSession() throws Exception {
    var request = new MockHttpServletRequest();
    var session = new MockHttpSession();
    request.setSession(session);
    session.setAttribute("validated_redirect_uri", "https://opencop.test/map");
    var persistedContext = SecurityContextHolder.createEmptyContext();
    persistedContext.setAuthentication(
        new TestingAuthenticationToken("established-principal", "credentials", "ROLE_user"));
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, persistedContext);
    SecurityContextHolder.setContext(persistedContext);
    var response = new MockHttpServletResponse();
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error("opencop_access_denied"), "generic denial");

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(request, response, exception);

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/access_denied");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(session.isInvalid()).isFalse();
    assertThat(
            session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
        .isSameAs(persistedContext);
    assertThat(session.getAttribute("validated_redirect_uri")).isNull();
  }

  @Test
  void unrecognizedExternalOauthErrorCodeIsCollapsedToABoundedDiagnosticCategory()
      throws Exception {
    var response = new MockHttpServletResponse();
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error("sensitive-provider-error-code"), "generic failure");

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage()).contains("oauth_authentication_failure");
    assertThat(event.getFormattedMessage()).doesNotContain("sensitive-provider-error-code");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "invalid_oidc_claim, invalid_claim",
    "invalid_oidc_audience, invalid_audience",
    "opencop_admission_required, unsupported_principal"
  })
  void invalidAuthenticationClassesLogOnlyTheirBoundedCategoryAtWarn(
      String errorCode, String expectedCategory) throws Exception {
    var response = new MockHttpServletResponse();
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error(errorCode), "sensitive-provider-error-message");

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains(expectedCategory)
        .doesNotContain(errorCode, "sensitive-provider-error-message");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void admissionDenialUsesTheExistingAccessDeniedBrowserContract() throws Exception {
    var response = new MockHttpServletResponse();
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error("opencop_access_denied"), "generic denial");

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/access_denied");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
    assertThat(appender.list.getFirst().getFormattedMessage()).contains("admission_denied");
  }

  @Test
  void missingAuthorizationRequestRetainsTheExactMultiTabBrowserContract() throws Exception {
    var response = new MockHttpServletResponse();
    var exception =
        new OAuth2AuthenticationException(
            new OAuth2Error("authorization_request_not_found"), "generic failure");

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

    assertThat(response.getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_failed?reason=auth_request_not_found");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .contains("authorization_request_not_found");
  }

  @Test
  void unexpectedAuthenticationExceptionUsesGenericLoginFailure() throws Exception {
    var response = new MockHttpServletResponse();

    var exception =
        new AuthenticationServiceException(
            "sensitive-authentication-message",
            new IllegalArgumentException("sensitive-provider-payload"));

    new OidcAuthenticationFailureHandler("https://opencop.test")
        .onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

    assertThat(response.getRedirectedUrl()).isEqualTo("https://opencop.test/login_failed");
    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage())
        .contains(
            "unexpected_authentication_failure", "causeType=java.lang.IllegalArgumentException")
        .doesNotContain("sensitive-authentication-message", "sensitive-provider-payload");
    assertThat(ThrowableProxyUtil.asString(event.getThrowableProxy()))
        .doesNotContain("sensitive-authentication-message", "sensitive-provider-payload");
  }
}
