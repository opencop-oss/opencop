// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.opencop.core.api.common.CoreSecurityExceptionAdvice;
import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.ports.audit.AuthzAccessDeniedException;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class CoreSecurityErrorContractIT {

  private static final String SECRET_MARKER = "do-not-disclose-this-marker";
  private static final String UNAUTHORIZED_BODY =
      "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\"/oauth2/authorization/oidc\"}";
  private static final UUID ACTOR_USER_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

  @Test
  void apiAllowsAuthenticatedRequestAndReturnsExactCoreUnauthorizedForAnonymousRequest() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(get("/v1/security-error-probe/permitted").with(user("permitted-user")))
                  .andExpect(status().isNoContent());
              chainMvc
                  .perform(get("/v1/security-error-probe/permitted"))
                  .andExpect(status().isUnauthorized())
                  .andExpect(content().contentType("application/json"))
                  .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT))
                  .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                  .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
            });
  }

  /** Every API version is authenticated the same way, rather than denied for being unlisted. */
  @Test
  void everyApiVersionAllowsAuthenticatedRequestsAndAnswersAnonymousOnesWithCoreUnauthorized() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              for (var path :
                  new String[] {
                    "/v2/security-error-probe/permitted", "/v9/security-error-probe/permitted"
                  }) {
                chainMvc
                    .perform(get(path).with(user("permitted-user")))
                    .andExpect(status().isNoContent());
                chainMvc
                    .perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT));
              }
            });
  }

  /** A later version's admin paths are as restricted as v1's, without a rule of their own. */
  @Test
  void everyApiVersionRestrictsItsAdminPathsToAdministrators() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(get("/v2/admin/security-error-probe").with(user("regular-user")))
                  .andExpect(status().isForbidden());
              chainMvc
                  .perform(
                      get("/v2/admin/security-error-probe").with(user("admin-user").roles("admin")))
                  .andExpect(status().isNoContent());
            });
  }

  @Test
  void realSecurityChainAuthenticatedCsrfDenialReturnsCodedForbiddenAndRetainsAuditReason() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              var result =
                  chainMvc
                      .perform(post("/v1/security-error-probe/permitted").with(user("csrf-user")))
                      .andExpect(status().isForbidden())
                      .andExpect(content().contentType("application/json"))
                      .andExpect(
                          content().string("{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}"))
                      .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                      .andReturn();

              assertThat(
                      result
                          .getRequest()
                          .getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON))
                  .isEqualTo(AuthzReasonCode.CSRF_REJECTED);
              assertThat(
                      result.getRequest().getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE))
                  .isEqualTo(AuthzPrincipalType.AUTHENTICATED);
            });
  }

  @Test
  void streamAuthorizationAllowsAuthenticatedAndDeniesAnonymousWithOneTelemetryEvent() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(get("/streams/authorize").with(user("stream-user")))
                  .andExpect(status().isNoContent());
              chainMvc
                  .perform(get("/streams/authorize"))
                  .andExpect(status().isUnauthorized())
                  .andExpect(content().contentType("application/json"))
                  .andExpect(content().json(UNAUTHORIZED_BODY, JsonCompareMode.STRICT))
                  .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                  .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

              var denialCounter =
                  context
                      .getBean(MeterRegistry.class)
                      .find("opencop_stream_authz_total")
                      .tags("outcome", "denied", "reason", "unauthenticated")
                      .counter();
              assertThat(denialCounter).isNotNull();
              assertThat(denialCounter.count()).isEqualTo(1.0);
            });
  }

  @Test
  void authenticatedMvcAccessDeniedReturnsExactGenericForbiddenResponse() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(
                      get("/v1/security-error-probe/access-denied")
                          .with(user("authenticated-user")))
                  .andExpect(status().isForbidden())
                  .andExpect(content().contentType("application/json"))
                  .andExpect(content().string("{\"error\":\"forbidden\"}"))
                  .andExpect(
                      content()
                          .string(
                              org.hamcrest.Matchers.not(
                                  org.hamcrest.Matchers.containsString(SECRET_MARKER))))
                  .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
            });
  }

  @Test
  void mvcAuthenticationExceptionReturnsExactCoreUnauthorizedResponse() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              chainMvc
                  .perform(
                      get("/v1/security-error-probe/authentication")
                          .with(user("authenticated-user")))
                  .andExpect(status().isUnauthorized())
                  .andExpect(content().contentType("application/json"))
                  .andExpect(content().string(UNAUTHORIZED_BODY))
                  .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                  .andExpect(
                      content()
                          .string(
                              org.hamcrest.Matchers.not(
                                  org.hamcrest.Matchers.containsString(SECRET_MARKER))))
                  .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
            });
  }

  @Test
  void debugSecurityEventsDoNotRetainExceptionDetails() {
    var logger = (Logger) LoggerFactory.getLogger(CoreSecurityExceptionAdvice.class);
    var originalLevel = logger.getLevel();
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.setLevel(Level.DEBUG);
    logger.addAppender(appender);

    try {
      securityContext()
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                var chainMvc =
                    MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

                chainMvc
                    .perform(
                        get("/v1/security-error-probe/access-denied")
                            .with(user("authenticated-user")))
                    .andExpect(status().isForbidden());
                chainMvc
                    .perform(
                        get("/v1/security-error-probe/authentication")
                            .with(user("authenticated-user")))
                    .andExpect(status().isUnauthorized());
                chainMvc
                    .perform(
                        get("/v1/security-error-probe/authz-access-denied")
                            .with(user("authenticated-user")))
                    .andExpect(status().isForbidden());
              });

      assertThat(appender.list)
          .hasSize(3)
          .allSatisfy(
              event -> {
                assertThat(renderedLog(event)).doesNotContain(SECRET_MARKER);
                assertThat(event.getThrowableProxy()).isNull();
              });
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .containsExactly(
              "CORE_SECURITY_ACCESS_DENIED exception_type=AccessDeniedException",
              "CORE_SECURITY_AUTHENTICATION_FAILED exception_type=BadCredentialsException",
              "CORE_SECURITY_ACCESS_DENIED exception_type=AuthzAccessDeniedException"
                  + " reason=MISSING_LAYER_GROUP_ROLE");
    } finally {
      logger.detachAppender(appender);
      logger.setLevel(originalLevel);
      appender.stop();
    }
  }

  @Test
  void authzAccessDeniedReturnsGenericForbiddenAndRetainsAuditAttributes() {
    securityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var chainMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              var result =
                  chainMvc
                      .perform(
                          get("/v1/security-error-probe/authz-access-denied")
                              .with(user("authenticated-user"))
                              .with(
                                  request -> {
                                    request.setAttribute(
                                        AuthzAuditRequestAttributes.PRINCIPAL_TYPE,
                                        AuthzPrincipalType.AUTHENTICATED);
                                    request.setAttribute(
                                        AuthzAuditRequestAttributes.ACTOR_USER_ID, ACTOR_USER_ID);
                                    request.setAttribute(
                                        AuthzAuditRequestAttributes.ACTOR_EMAIL,
                                        "audit.user@example.test");
                                    return request;
                                  }))
                      .andExpect(status().isForbidden())
                      .andExpect(content().string("{\"error\":\"forbidden\"}"))
                      .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                      .andReturn();

              assertThat(
                      result
                          .getRequest()
                          .getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON))
                  .isEqualTo(AuthzReasonCode.MISSING_LAYER_GROUP_ROLE);
              assertThat(
                      result.getRequest().getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE))
                  .isEqualTo(AuthzPrincipalType.AUTHENTICATED);
              assertThat(
                      result.getRequest().getAttribute(AuthzAuditRequestAttributes.ACTOR_USER_ID))
                  .isEqualTo(ACTOR_USER_ID);
              assertThat(result.getRequest().getAttribute(AuthzAuditRequestAttributes.ACTOR_EMAIL))
                  .isEqualTo("audit.user@example.test");
            });
  }

  @RestController
  static final class SecurityErrorProbeController {

    @GetMapping("/v1/security-error-probe/permitted")
    ResponseEntity<Void> permitted() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping({"/v2/security-error-probe/permitted", "/v9/security-error-probe/permitted"})
    ResponseEntity<Void> permittedV2() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping("/v2/admin/security-error-probe")
    ResponseEntity<Void> adminProbe() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping("/streams/authorize")
    ResponseEntity<Void> streamAuthorize() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/security-error-probe/access-denied")
    void accessDenied() {
      throw new AccessDeniedException(SECRET_MARKER);
    }

    @GetMapping("/v1/security-error-probe/authentication")
    void authentication() {
      throw new BadCredentialsException(SECRET_MARKER);
    }

    @GetMapping("/v1/security-error-probe/authz-access-denied")
    void authzAccessDenied() {
      throw new AuthzAccessDeniedException(AuthzReasonCode.MISSING_LAYER_GROUP_ROLE, SECRET_MARKER);
    }
  }

  private static WebApplicationContextRunner securityContext() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(SecurityChainTestConfig.class, SecurityConfig.class)
        .withPropertyValues(
            "opencop.domain=https://opencop.test",
            "idp.audience=test-audience",
            "idp.user-id-claim=sub",
            "opencop.idp.issuer=",
            "idp.admin.client-id=test-client",
            "idp.admin.client-secret=test-value",
            "idp.authorization-uri=https://idp.test/authorize",
            "idp.token-uri=https://idp.test/oauth2/v2.0/access",
            "idp.jwk-set-uri=https://idp.test/discovery/keys",
            "idp.user-info-uri=https://idp.test/oidc/userinfo");
  }

  private static String renderedLog(ILoggingEvent event) {
    if (event.getThrowableProxy() == null) {
      return event.getFormattedMessage();
    }
    return event.getFormattedMessage() + ThrowableProxyUtil.asString(event.getThrowableProxy());
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSecurity
  @Import({
    SecurityErrorProbeController.class,
    CoreSecurityExceptionAdvice.class,
    CurrentAccountAccessFilter.class,
    CurrentAccountAuthenticationFactory.class,
    CurrentAccountAccessFactory.class,
    CurrentAccountAccessPolicy.class,
    ResolveCurrentAccountAccessUsecase.class,
    CurrentAccountSessionTelemetry.class
  })
  static class SecurityChainTestConfig {

    @Bean
    LockedCurrentAccountAccessGateway currentAccountAccessGateway() {
      return mock(LockedCurrentAccountAccessGateway.class);
    }

    @Bean
    CurrentAccountAccessCacheGateway currentAccountAccessCacheGateway() {
      return mock(CurrentAccountAccessCacheGateway.class);
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      var transactionManager = mock(PlatformTransactionManager.class);
      when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
      return transactionManager;
    }

    @Bean
    UserService userService() {
      return mock(UserService.class);
    }

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    StreamAuthorizationTelemetry streamAuthorizationTelemetry(MeterRegistry meterRegistry) {
      return new StreamAuthorizationTelemetry(meterRegistry);
    }

    @Bean
    CookieSerializer cookieSerializer() {
      return new DefaultCookieSerializer();
    }
  }
}
