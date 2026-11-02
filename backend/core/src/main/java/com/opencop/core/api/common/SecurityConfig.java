// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common;

import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CustomAuthenticationSuccessHandler;
import com.opencop.core.api.common.filter.OAuth2StateFilter;
import com.opencop.core.api.common.filter.OidcAuthenticationFailureHandler;
import com.opencop.core.api.common.filter.SessionCutoverFilter;
import com.opencop.core.api.common.filter.SessionCutoverTelemetry;
import com.opencop.core.api.common.filter.SpaAuthenticationEntryPoint;
import com.opencop.core.api.general.business.factory.auth.AdmittedOidcIdentityFactory;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.model.auth.IdpRelationship;
import com.opencop.core.api.general.business.policy.OidcAdmissionPolicy;
import com.opencop.core.api.general.business.usecase.user.OidcAdmissionUsecase;
import com.opencop.core.api.general.ports.technical.gateway.UserService;
import com.opencop.core.api.general.technical.config.IdpRelationshipProperties;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationAuthenticationEntryPoint;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.idp.MinimalPrincipalFactory;
import com.opencop.core.api.general.technical.gateway.idp.OidcAdmissionCommandFactory;
import com.opencop.core.api.general.technical.gateway.idp.OidcAuthenticationResultConverter;
import com.opencop.core.api.general.technical.gateway.idp.OpenCopOidcUserService;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.technical.security.session.AuthSessionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;

@Configuration
@EnableConfigurationProperties({IdpRelationshipProperties.class, AuthSessionProperties.class})
public class SecurityConfig {

  // Every API version, so a new one is authenticated, and its admin and dev paths restricted,
  // without a rule of its own.
  private static final String API_PATH_PATTERN = "/v{version:\\d+}/**";
  private static final String ADMIN_API_PATH_PATTERN = "/v{version:\\d+}/admin/**";
  private static final String DEV_API_PATH_PATTERN = "/v{version:\\d+}/dev/**";
  private static final String STREAM_AUTHORIZE_PATH = "/streams/authorize";

  @Value("${opencop.domain}")
  private String allowedDomain;

  @Bean
  ClientRegistrationRepository clientRegistrationRepository(
      @Value("${opencop.idp.issuer}") String issuer,
      @Value("${idp.admin.client-id}") String clientId,
      @Value("${idp.admin.client-secret}") String clientSecret,
      @Value("${idp.authorization-uri:}") String authorizationUri,
      @Value("${idp.token-uri:}") String tokenUri,
      @Value("${idp.jwk-set-uri:}") String jwkSetUri,
      @Value("${idp.user-info-uri:}") String userInfoUri) {

    ClientRegistration registration;
    if (StringUtils.isNotEmpty(issuer) && StringUtils.isEmpty(authorizationUri)) {
      registration =
          ClientRegistrations.fromIssuerLocation(issuer) // uses discovery
              .registrationId("oidc")
              .clientId(clientId)
              .clientSecret(clientSecret)
              .scope("openid", "profile", "email")
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .build();
    } else {
      registration =
          ClientRegistration.withRegistrationId("oidc")
              .clientId(clientId)
              .clientSecret(clientSecret)
              .scope("openid", "profile", "email")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              // provider endpoints (no discovery)
              .authorizationUri(authorizationUri)
              .tokenUri(tokenUri)
              .jwkSetUri(jwkSetUri)
              .userInfoUri(userInfoUri)
              .userNameAttributeName("sub")
              .clientName("OIDC")
              .build();
    }

    return new InMemoryClientRegistrationRepository(registration);
  }

  @Bean
  CustomAuthenticationSuccessHandler successHandler() {
    return new CustomAuthenticationSuccessHandler(URI.create(allowedDomain));
  }

  @Bean
  OidcAuthenticationFailureHandler oidcAuthenticationFailureHandler() {
    return new OidcAuthenticationFailureHandler(allowedDomain);
  }

  @Bean
  OidcAdmissionPolicy oidcAdmissionPolicy(IdpRelationshipProperties properties) {
    var relationships =
        properties.getRelationshipConfigs().stream()
            .map(config -> new IdpRelationship(config.getGroup(), config.getRoles()))
            .toList();
    return new OidcAdmissionPolicy(relationships);
  }

  @Bean
  OidcAdmissionUsecase oidcAdmissionUsecase(
      OidcAdmissionPolicy admissionPolicy,
      UserService userService,
      CurrentAccountAccessFactory currentAccountAccessFactory,
      CurrentAccountAccessPolicy currentAccountAccessPolicy,
      AdmittedOidcIdentityFactory admittedIdentityFactory) {
    return new OidcAdmissionUsecase(
        admissionPolicy,
        userService,
        currentAccountAccessFactory,
        currentAccountAccessPolicy,
        admittedIdentityFactory);
  }

  @Bean
  AdmittedOidcIdentityFactory admittedOidcIdentityFactory(Clock clock) {
    return new AdmittedOidcIdentityFactory(clock);
  }

  @Bean
  SessionLifetimePolicy sessionLifetimePolicy(AuthSessionProperties properties, Clock clock) {
    return new SessionLifetimePolicy(properties.getAbsoluteLifetime(), clock);
  }

  @Bean
  OidcAdmissionCommandFactory oidcAdmissionCommandFactory(IdpRelationshipProperties properties) {
    return new OidcAdmissionCommandFactory(properties.getUserIdClaim(), properties.getAudience());
  }

  @Bean
  OpenCopOidcUserService openCopOidcUserService(
      OidcAdmissionCommandFactory commandFactory, OidcAdmissionUsecase admissionUsecase) {
    return new OpenCopOidcUserService(new OidcUserService(), commandFactory, admissionUsecase);
  }

  @Bean
  MinimalPrincipalFactory minimalPrincipalFactory() {
    return new MinimalPrincipalFactory();
  }

  @Bean
  OidcAuthenticationResultConverter oidcAuthenticationResultConverter(
      MinimalPrincipalFactory principalFactory) {
    return new OidcAuthenticationResultConverter(principalFactory);
  }

  @Bean
  StreamAuthorizationAuthenticationEntryPoint streamAuthorizationAuthenticationEntryPoint(
      StreamAuthorizationTelemetry telemetry) {
    return new StreamAuthorizationAuthenticationEntryPoint(telemetry);
  }

  @Bean
  SpaAuthenticationEntryPoint apiAuthenticationEntryPoint() {
    return new SpaAuthenticationEntryPoint();
  }

  @Bean
  @Order(1)
  SecurityFilterChain securityFilter(
      HttpSecurity http,
      CustomAuthenticationSuccessHandler successHandler,
      OidcAuthenticationFailureHandler failureHandler,
      OpenCopOidcUserService oidcUserService,
      OidcAuthenticationResultConverter authenticationResultConverter,
      CurrentAccountAccessFilter currentAccountAccessFilter,
      SpaAuthenticationEntryPoint apiAuthenticationEntryPoint,
      StreamAuthorizationAuthenticationEntryPoint streamAuthorizationAuthenticationEntryPoint,
      CookieSerializer sessionCookieSerializer,
      MeterRegistry meterRegistry) {
    var csrfRepo = new HttpSessionCsrfTokenRepository();
    csrfRepo.setHeaderName("X-XSRF-TOKEN");
    var apiRequestMatcher = PathPatternRequestMatcher.withDefaults().matcher(API_PATH_PATTERN);
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/error")
                    .permitAll()
                    .requestMatchers("/.well-known/**")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/**")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                    .authenticated()
                    .requestMatchers(DEV_API_PATH_PATTERN)
                    .hasRole("admin")
                    .requestMatchers(ADMIN_API_PATH_PATTERN)
                    .hasRole("admin")
                    .requestMatchers(HttpMethod.GET, STREAM_AUTHORIZE_PATH)
                    .authenticated()
                    .requestMatchers(apiRequestMatcher)
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            ex ->
                ex
                    // For API calls, return 401 instead of redirecting to OAuth
                    .defaultAuthenticationEntryPointFor(
                        apiAuthenticationEntryPoint, apiRequestMatcher)
                    .defaultAuthenticationEntryPointFor(
                        streamAuthorizationAuthenticationEntryPoint,
                        PathPatternRequestMatcher.withDefaults().matcher(STREAM_AUTHORIZE_PATH))
                    .defaultAccessDeniedHandlerFor(
                        new AuditingAccessDeniedHandler(), apiRequestMatcher)
                    .defaultAccessDeniedHandlerFor(
                        new AuditingAccessDeniedHandler(),
                        PathPatternRequestMatcher.withDefaults().matcher(STREAM_AUTHORIZE_PATH)))
        .oauth2Login(
            login ->
                login
                    .successHandler(successHandler)
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    .withObjectPostProcessor(
                        new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                          @Override
                          public <O extends OAuth2LoginAuthenticationFilter> O postProcess(
                              O filter) {
                            filter.setAuthenticationResultConverter(authenticationResultConverter);
                            return filter;
                          }
                        })
                    // Redirect auth failures to the SPA instead of Spring's default
                    // error page. Most common failure is authorization_request_not_found,
                    // caused by multi-tab login: Spring Security stores a single
                    // AuthorizationRequest per session (changed from a map to a single
                    // slot in Spring Security 5.5), so a second tab's auth flow
                    // overwrites the first tab's stored request.
                    .failureHandler(failureHandler))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepo)
                    .csrfTokenRequestHandler(new HeaderOnlyCsrfTokenRequestHandler()))
        .addFilterBefore(
            new OAuth2StateFilter(URI.create(allowedDomain), CoreSecurityErrorResponse.LOGIN_URL),
            SecurityContextHolderFilter.class)
        .addFilterBefore(
            new SessionCutoverFilter(
                sessionCookieSerializer,
                apiRequestMatcher,
                new SessionCutoverTelemetry(meterRegistry)),
            CsrfFilter.class)
        .addFilterAfter(currentAccountAccessFilter, SecurityContextHolderFilter.class)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(RequestCacheConfigurer::disable);

    return http.build();
  }

  private static final class HeaderOnlyCsrfTokenRequestHandler
      extends CsrfTokenRequestAttributeHandler {

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
      return request.getHeader(csrfToken.getHeaderName());
    }
  }
}
