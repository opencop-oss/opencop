// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.idp.OidcAuthenticationResultConverter;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.ports.audit.AuthzPrincipalType;
import com.opencop.core.sdk.ports.audit.AuthzReasonCode;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.audit.AuthzActorAttributionFilter;
import com.opencop.core.sdk.technical.audit.AuthzAuditConfig;
import com.opencop.core.sdk.technical.audit.AuthzAuditRequestAttributes;
import com.opencop.core.sdk.technical.audit.AuthzDeniedBackstopFilter;
import com.opencop.core.sdk.technical.audit.MicrometerAuthzAuditReporter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthzAuditWiringTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withUserConfiguration(AuthzAuditConfig.class, TestMeterRegistryConfig.class);

  @Test
  void authzAuditConfig_registersBackstopOutsideAndAttributionInsideSecurityChain() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(MicrometerAuthzAuditReporter.class);
          assertThat(context).hasSingleBean(AuthzDeniedBackstopFilter.class);

          @SuppressWarnings("rawtypes")
          var registrations = context.getBeansOfType(FilterRegistrationBean.class).values();
          assertThat(registrations).hasSize(2);
          var backstop = registrationFor(registrations, AuthzDeniedBackstopFilter.class);
          var attribution = registrationFor(registrations, AuthzActorAttributionFilter.class);
          // The contract is the relationship, not the literals: the backstop
          // registers ahead of (outside) springSecurityFilterChain so its
          // final-status read happens after the chain fully unwinds, while
          // the attribution filter registers behind (inside) it so it sees a
          // populated SecurityContext on the way in. Not covered: an app
          // overriding spring.security.filter.order at runtime.
          assertThat(backstop.getOrder()).isLessThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
          assertThat(attribution.getOrder())
              .isGreaterThan(SecurityFilterProperties.DEFAULT_FILTER_ORDER);
          // Backstop is global; attribution is scoped to the API surface so
          // permitAll paths keep Spring Security's deferred-context skip.
          assertThat(backstop.getUrlPatterns()).isEmpty();
          assertThat(attribution.getUrlPatterns()).containsExactly("/v1/*");
        });
  }

  @SuppressWarnings("rawtypes")
  private static FilterRegistrationBean registrationFor(
      java.util.Collection<FilterRegistrationBean> registrations, Class<?> filterType) {
    return registrations.stream()
        .filter(registration -> filterType.isInstance(registration.getFilter()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no registration for " + filterType.getSimpleName()));
  }

  @Test
  void appConfig_importsAuthzAuditConfig() {
    var imported = AppConfig.class.getAnnotation(Import.class).value();

    assertThat(imported).contains(AuthzAuditConfig.class);
  }

  @Test
  void realSecurityChain_anonymousCsrfDenialUsesUnauthorizedBodyAndBackstopCounter() {
    // Boots the production SecurityConfig (not a hand-wired test app) and
    // drives an anonymous CSRF-rejected POST through the real chain with the
    // backstop registered in production order (outside the chain).
    new WebApplicationContextRunner()
        .withUserConfiguration(ChainTestConfig.class, SecurityConfig.class)
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
            "idp.user-info-uri=https://idp.test/oidc/userinfo")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var registry = new SimpleMeterRegistry();
              try {
                var backstop =
                    new AuthzDeniedBackstopFilter(new MicrometerAuthzAuditReporter(registry));
                var mockMvc =
                    MockMvcBuilders.webAppContextSetup(context)
                        .addFilters(backstop)
                        .apply(springSecurity())
                        .build();

                var result =
                    mockMvc
                        .perform(post("/v1/events/source-1/object-1/attachments"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentType("application/json"))
                        .andExpect(
                            content()
                                .string(
                                    "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\"/oauth2/authorization/oidc\"}"))
                        .andReturn();

                assertThat(
                        result
                            .getRequest()
                            .getAttribute(AuthzAuditRequestAttributes.CANDIDATE_REASON))
                    .isEqualTo(AuthzReasonCode.CSRF_REJECTED);
                assertThat(
                        result
                            .getRequest()
                            .getAttribute(AuthzAuditRequestAttributes.PRINCIPAL_TYPE))
                    .isEqualTo(AuthzPrincipalType.ANONYMOUS);

                var counter =
                    registry
                        .find("opencop_authz_denied_total")
                        .tags(
                            "reason_code",
                            "CSRF_REJECTED",
                            "principal_type",
                            "ANONYMOUS",
                            "action",
                            "UNKNOWN",
                            "resource_type",
                            "UNKNOWN")
                        .counter();
                assertThat(counter).isNull();
              } finally {
                registry.close();
              }
            });
  }

  @Test
  void realSecurityChain_streamAuthorizeUnauthenticatedReturns401WithoutRedirect() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mockMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              mockMvc
                  .perform(get("/streams/authorize"))
                  .andExpect(status().isUnauthorized())
                  .andExpect(header().doesNotExist("Location"));

              var counter =
                  context
                      .getBean(MeterRegistry.class)
                      .find("opencop_stream_authz_total")
                      .tags("outcome", "denied", "reason", "unauthenticated")
                      .counter();
              assertThat(counter).isNotNull();
              assertThat(counter.count()).isEqualTo(1.0);
            });
  }

  @Test
  void realSecurityChain_streamAuthorizeAuthenticatedGetDoesNotRequireCsrf() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mockMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              mockMvc
                  .perform(
                      get("/streams/authorize")
                          .header(
                              "X-Forwarded-Uri",
                              "/streams/att_6d063523-0461-439e-b025-ed3baf3b1d18/index.m3u8")
                          .header("X-Forwarded-Method", "GET")
                          .with(user("stream-user")))
                  .andExpect(status().isNoContent());
            });
  }

  @Test
  void realSecurityChain_streamAuthorizePostIsNotPublicProductRoute() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mockMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              mockMvc
                  .perform(
                      post("/streams/authorize").with(csrf().asHeader()).with(user("stream-user")))
                  .andExpect(status().isForbidden());
            });
  }

  @Test
  void realSecurityChain_adminPathRequiresAdminRole() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mockMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              mockMvc.perform(get("/v1/admin/test")).andExpect(status().isUnauthorized());
              mockMvc
                  .perform(get("/v1/admin/test").with(user("ordinary-user")))
                  .andExpect(status().isForbidden());
              mockMvc
                  .perform(get("/v1/admin/test").with(user("admin-user").roles("admin")))
                  .andExpect(status().isNoContent());
            });
  }

  @Test
  void realSecurityChainReservesLogoutForAuthenticatedCsrfProtectedMvcHandling() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var mockMvc =
                  MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

              mockMvc.perform(post("/v1/bff/logout")).andExpect(status().isUnauthorized());
              mockMvc
                  .perform(post("/v1/bff/logout").with(csrf().asHeader()))
                  .andExpect(status().isUnauthorized());
              mockMvc
                  .perform(
                      post("/v1/bff/logout")
                          .with(csrf().asHeader())
                          .with(user("authenticated-user")))
                  .andExpect(status().isNotFound());
            });
  }

  @Test
  void realSecurityChain_installsMinimalResultConversionOnTheStandardOidcFilter() {
    streamAuthorizeSecurityContext()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var filters = context.getBean(SecurityFilterChain.class).getFilters();
              assertThat(filters)
                  .filteredOn(OAuth2LoginAuthenticationFilter.class::isInstance)
                  .hasSize(1);
              var filter =
                  filters.stream()
                      .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                      .map(OAuth2LoginAuthenticationFilter.class::cast)
                      .findFirst()
                      .orElseThrow();
              assertThat(ReflectionTestUtils.getField(filter, "authenticationResultConverter"))
                  .isSameAs(context.getBean(OidcAuthenticationResultConverter.class));
            });
  }

  private static WebApplicationContextRunner streamAuthorizeSecurityContext() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ChainTestConfig.class, SecurityConfig.class)
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

  @TestConfiguration(proxyBeanMethods = false)
  static class TestMeterRegistryConfig {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableWebSecurity
  @Import({
    StreamAuthorizeTestController.class,
    CurrentAccountAccessFilter.class,
    CurrentAccountAuthenticationFactory.class,
    CurrentAccountAccessFactory.class,
    CurrentAccountAccessPolicy.class,
    ResolveCurrentAccountAccessUsecase.class,
    CurrentAccountSessionTelemetry.class
  })
  static class ChainTestConfig {

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

  @RestController
  static class StreamAuthorizeTestController {

    @GetMapping("/streams/authorize")
    ResponseEntity<Void> authorize() {
      return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/admin/test")
    ResponseEntity<Void> admin() {
      return ResponseEntity.noContent().build();
    }
  }
}
