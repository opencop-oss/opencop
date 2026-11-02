// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.opencop.core.api.common.SecurityConfig;
import com.opencop.core.api.common.filter.CurrentAccountAccessFilter;
import com.opencop.core.api.common.filter.CurrentAccountAuthenticationFactory;
import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.session.RedisSessionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = CsrfSecurityComponentIT.TestApplication.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CsrfSecurityComponentIT extends AbstractWebIT {

  private static final String SESSION_COOKIE_NAME = "OPENCOP_BFF";
  private static final String CUSTOM_SESSION_COOKIE_NAME = "CUSTOM_BFF";
  private static final String CUSTOM_SESSION_COOKIE_PATH = "/v1";
  private static final String CUSTOM_SESSION_COOKIE_DOMAIN = "opencop.test";
  private static final String LEGACY_CSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final String UNAUTHORIZED_BODY =
      "{\"error\":\"unauthorized\",\"message\":\"Login required\",\"loginUrl\":\"/oauth2/authorization/oidc\"}";
  private static final String CSRF_REJECTED_BODY =
      "{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}";
  private static final String INVALID_SESSION_RECOVERIES_METRIC =
      "opencop_bff_invalid_session_recoveries_total";
  private static final String LEGACY_CSRF_COOKIE_OBSERVATIONS_METRIC =
      "opencop_bff_legacy_csrf_cookie_observations_total";

  private final StringRedisTemplate redisTemplate;
  private final MeterRegistry meterRegistry;
  private final DataSource dataSource;
  private final SessionRepository sessionRepository;
  private final CookieSerializer cookieSerializer;
  private final UnsafeProbeEndpoint unsafeProbeEndpoint;
  private final CsrfEndpointInvocationProbe csrfEndpointInvocationProbe;
  private final RequestMappingHandlerMapping requestMappingHandlerMapping;
  private final Filter sessionRepositoryFilter;

  private MockMvc firstRequestContext;
  private MockMvc secondRequestContext;

  CsrfSecurityComponentIT(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      DataSource dataSource,
      SessionRepository sessionRepository,
      CookieSerializer cookieSerializer,
      UnsafeProbeEndpoint unsafeProbeEndpoint,
      CsrfEndpointInvocationProbe csrfEndpointInvocationProbe,
      @Qualifier("requestMappingHandlerMapping")
          RequestMappingHandlerMapping requestMappingHandlerMapping,
      @Qualifier("springSessionRepositoryFilter") Filter sessionRepositoryFilter) {
    this.redisTemplate = redisTemplate;
    this.meterRegistry = meterRegistry;
    this.dataSource = dataSource;
    this.sessionRepository = sessionRepository;
    this.cookieSerializer = cookieSerializer;
    this.unsafeProbeEndpoint = unsafeProbeEndpoint;
    this.csrfEndpointInvocationProbe = csrfEndpointInvocationProbe;
    this.requestMappingHandlerMapping = requestMappingHandlerMapping;
    this.sessionRepositoryFilter = sessionRepositoryFilter;
  }

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
    registry.add("spring.session.servlet.cookie.name", () -> SESSION_COOKIE_NAME);
    registry.add("spring.session.servlet.cookie.path", () -> "/");
    registry.add("opencop.domain", () -> "https://app.opencop.test");
    registry.add("idp.audience", () -> "CORE-API");
  }

  @BeforeEach
  void setUpRequestContexts() {
    firstRequestContext = requestContext();
    secondRequestContext = requestContext();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    meterRegistry.clear();
    unsafeProbeEndpoint.reset();
    csrfEndpointInvocationProbe.reset();
  }

  @Test
  void anonymousTokenRequestIsRejectedBeforeEndpointInvocationAndSessionAllocation()
      throws Exception {
    var rejected =
        firstRequestContext
            .perform(get("/v1/csrf"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(csrfEndpointInvocationProbe.invocations()).isZero();
    assertRedisIsEmpty();
    assertNoCookieNamed(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), SESSION_COOKIE_NAME);
  }

  @Test
  void legacyPublicTokenPathIsDenied() throws Exception {
    var authenticatedSession = persistAuthenticatedSession();

    firstRequestContext
        .perform(get("/csrf").cookie(authenticatedSession.cookie()))
        .andExpect(status().isForbidden());

    assertThat(requestMappingHandlerMapping.getHandlerMethods().keySet())
        .noneMatch(mapping -> mapping.getPatternValues().contains("/csrf"));
  }

  @Test
  void unknownSessionOnSafeApiRequestReturnsUnauthorizedClearsCookieAndAllocatesNoSession()
      throws Exception {
    var unknownSessionCookie = sessionCookie("unknown-session-id");

    var rejected =
        firstRequestContext
            .perform(get("/v1/csrf-probe").cookie(unknownSessionCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(rejected.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertSingleClearingCookie(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), SESSION_COOKIE_NAME);
    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertRedisIsEmpty();
    assertThat(meterRegistry.find(INVALID_SESSION_RECOVERIES_METRIC).counter())
        .isNotNull()
        .extracting(counter -> counter.count())
        .isEqualTo(1.0d);
  }

  @Test
  void unknownSessionCleanupUsesConfiguredCookieSerializer() throws Exception {
    try (var customCookieContext =
        startReplicaContext(CUSTOM_SESSION_COOKIE_NAME, CUSTOM_SESSION_COOKIE_PATH)) {
      var customCookieSerializer = customCookieContext.getBean(CookieSerializer.class);
      var customRequestContext = requestContext(customCookieContext);
      var unknownSessionCookie =
          sessionCookie(
              customCookieSerializer, CUSTOM_SESSION_COOKIE_NAME, "unknown-custom-session-id");

      var rejected =
          customRequestContext
              .perform(get("/v1/csrf-probe").cookie(unknownSessionCookie))
              .andExpect(status().isUnauthorized())
              .andExpect(content().string(UNAUTHORIZED_BODY))
              .andReturn();

      var setCookieHeaders = rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
      assertSingleClearingCookie(
          setCookieHeaders,
          CUSTOM_SESSION_COOKIE_NAME,
          "Domain=" + CUSTOM_SESSION_COOKIE_DOMAIN,
          "Path=" + CUSTOM_SESSION_COOKIE_PATH,
          "Max-Age=0",
          "Secure",
          "HttpOnly",
          "SameSite=Strict");
      assertNoCookieNamed(setCookieHeaders, SESSION_COOKIE_NAME);
      assertRedisIsEmpty();
    }
  }

  @Test
  void invalidatedSessionOnUnsafeRequestReturnsUnauthorizedClearsCookiesAndLeavesRedisEmpty()
      throws Exception {
    var invalidatedSession = openAuthenticatedCsrfSession(firstRequestContext);
    sessionRepository.deleteById(invalidatedSession.sessionId());
    assertRedisIsEmpty();

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(
                        invalidatedSession.cookie(),
                        new Cookie(LEGACY_CSRF_COOKIE_NAME, invalidatedSession.token()))
                    .header("X-XSRF-TOKEN", invalidatedSession.token()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(rejected.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    var setCookieHeaders = rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    assertSingleClearingCookie(setCookieHeaders, SESSION_COOKIE_NAME);
    assertSingleClearingCookie(setCookieHeaders, LEGACY_CSRF_COOKIE_NAME);
    assertHeadersDoNotContain(setCookieHeaders, invalidatedSession.token());
    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertRedisIsEmpty();
    assertThat(meterRegistry.find(INVALID_SESSION_RECOVERIES_METRIC).counter())
        .isNotNull()
        .extracting(counter -> counter.count())
        .isEqualTo(1.0d);
  }

  @Test
  void invalidatedSessionOnUnsafeRequestWithoutCsrfProofReturnsUnauthorizedAndLeavesRedisEmpty()
      throws Exception {
    var invalidatedSession = openAuthenticatedCsrfSession(firstRequestContext);
    sessionRepository.deleteById(invalidatedSession.sessionId());

    var rejected =
        secondRequestContext
            .perform(post("/v1/csrf-probe").cookie(invalidatedSession.cookie()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(rejected.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertSingleClearingCookie(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), SESSION_COOKIE_NAME);
    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertRedisIsEmpty();
  }

  @Test
  void invalidatedSessionOnUnsafeRequestWithWrongCsrfProofReturnsUnauthorizedAndLeavesRedisEmpty()
      throws Exception {
    var invalidatedSession = openAuthenticatedCsrfSession(firstRequestContext);
    sessionRepository.deleteById(invalidatedSession.sessionId());
    var wrongToken = "wrong-token";

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(invalidatedSession.cookie())
                    .header("X-XSRF-TOKEN", wrongToken))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(rejected.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    assertSingleClearingCookie(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), SESSION_COOKIE_NAME);
    assertHeadersDoNotContain(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), wrongToken);
    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertRedisIsEmpty();
  }

  @Test
  void missingSessionOnUnsafeRequestWithOldCsrfMaterialReturnsUnauthorizedAndLeavesRedisEmpty()
      throws Exception {
    var obsoleteToken = "obsolete-token";

    var rejected =
        firstRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(new Cookie(LEGACY_CSRF_COOKIE_NAME, obsoleteToken))
                    .header("X-XSRF-TOKEN", obsoleteToken))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(UNAUTHORIZED_BODY))
            .andReturn();

    assertThat(rejected.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    var setCookieHeaders = rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    assertSingleClearingCookie(setCookieHeaders, LEGACY_CSRF_COOKIE_NAME);
    assertHeadersDoNotContain(setCookieHeaders, obsoleteToken);
    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertRedisIsEmpty();
  }

  @Test
  void tokenObtainedInOneRequestContextSucceedsInAnotherThroughRedisSession() throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);
    assertSingleCsrfTokenAttribute(csrfSession.sessionId());

    try (var secondReplica = startReplicaContext()) {
      var secondReplicaRequestContext = requestContext(secondReplica);
      var secondReplicaProbe = secondReplica.getBean(UnsafeProbeEndpoint.class);
      var accepted =
          secondReplicaRequestContext
              .perform(
                  post("/v1/csrf-probe")
                      .cookie(csrfSession.cookie())
                      .header("X-XSRF-TOKEN", csrfSession.token()))
              .andExpect(status().isNoContent())
              .andReturn();

      assertThat(secondReplicaProbe.invocations()).isOne();
      assertNoXsrfCookie(accepted.getResponse().getHeaders("Set-Cookie"));
    }
  }

  @Test
  void developmentSwaggerBootstrapTokenSucceedsForUnsafeRequest() throws Exception {
    var authenticatedSession = persistAuthenticatedSession();
    var swaggerCsrfSession = openCsrfSession(firstRequestContext, authenticatedSession);

    var accepted =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(swaggerCsrfSession.cookie())
                    .header("X-XSRF-TOKEN", swaggerCsrfSession.token()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isOne();
    assertNoXsrfCookie(accepted.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void developmentSwaggerReloadReplacesStaleToken() throws Exception {
    var staleSwaggerSession = openAuthenticatedCsrfSession(firstRequestContext);
    sessionRepository.deleteById(staleSwaggerSession.sessionId());
    var replacementSession = persistAuthenticatedSession();

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(replacementSession.cookie())
                    .header("X-XSRF-TOKEN", staleSwaggerSession.token()))
            .andExpect(status().isForbidden())
            .andExpect(content().string(CSRF_REJECTED_BODY))
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertNoXsrfCookie(rejected.getResponse().getHeaders("Set-Cookie"));

    var reloadedSwaggerSession = openCsrfSession(firstRequestContext, replacementSession);
    assertThat(!reloadedSwaggerSession.token().equals(staleSwaggerSession.token())).isTrue();

    var accepted =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(reloadedSwaggerSession.cookie())
                    .header("X-XSRF-TOKEN", reloadedSwaggerSession.token()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isOne();
    assertNoXsrfCookie(accepted.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void matchingRequestParameterWithoutHeaderIsRejected() throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(csrfSession.cookie())
                    .param("_csrf", csrfSession.token()))
            .andExpect(status().isForbidden())
            .andExpect(content().string(CSRF_REJECTED_BODY))
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertNoXsrfCookie(rejected.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void plantedXsrfCookieWithoutHeaderIsRejected() throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(
                        csrfSession.cookie(),
                        new Cookie(LEGACY_CSRF_COOKIE_NAME, csrfSession.token())))
            .andExpect(status().isForbidden())
            .andExpect(content().string(CSRF_REJECTED_BODY))
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertSingleClearingCookie(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), LEGACY_CSRF_COOKIE_NAME);
    assertHeadersDoNotContain(
        rejected.getResponse().getHeaders(HttpHeaders.SET_COOKIE), csrfSession.token());
  }

  @Test
  void wrongHeaderTokenIsRejected() throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(csrfSession.cookie())
                    .header("X-XSRF-TOKEN", "wrong-token"))
            .andExpect(status().isForbidden())
            .andExpect(content().string(CSRF_REJECTED_BODY))
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertNoXsrfCookie(rejected.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void staleSessionTokenIsRejectedAndReplacementTokenSucceeds() throws Exception {
    var staleCsrfSession = openAuthenticatedCsrfSession(firstRequestContext);
    sessionRepository.deleteById(staleCsrfSession.sessionId());
    var replacementSession = persistAuthenticatedSession();

    var rejected =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(replacementSession.cookie())
                    .header("X-XSRF-TOKEN", staleCsrfSession.token()))
            .andExpect(status().isForbidden())
            .andExpect(content().string(CSRF_REJECTED_BODY))
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isZero();
    assertNoXsrfCookie(rejected.getResponse().getHeaders("Set-Cookie"));

    var replacementCsrfSession = openCsrfSession(firstRequestContext, replacementSession);
    assertThat(!replacementCsrfSession.token().equals(staleCsrfSession.token())).isTrue();

    var accepted =
        secondRequestContext
            .perform(
                post("/v1/csrf-probe")
                    .cookie(replacementCsrfSession.cookie())
                    .header("X-XSRF-TOKEN", replacementCsrfSession.token()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(unsafeProbeEndpoint.invocations()).isOne();
    assertNoXsrfCookie(accepted.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void ordinaryApiResponseDoesNotEmitXsrfCookie() throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);

    var response =
        secondRequestContext
            .perform(get("/v1/csrf-probe").cookie(csrfSession.cookie()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertNoXsrfCookie(response.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void successfulSafeApiRequestRetiresObservedLegacyXsrfCookieWithoutReadingItsValue()
      throws Exception {
    var csrfSession = openAuthenticatedCsrfSession(firstRequestContext);
    var legacyValue = "obsolete-client-value";

    var response =
        secondRequestContext
            .perform(
                get("/v1/csrf-probe")
                    .cookie(csrfSession.cookie(), new Cookie(LEGACY_CSRF_COOKIE_NAME, legacyValue)))
            .andExpect(status().isNoContent())
            .andReturn();

    var setCookieHeaders = response.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
    assertSingleClearingCookie(setCookieHeaders, LEGACY_CSRF_COOKIE_NAME);
    assertHeadersDoNotContain(setCookieHeaders, legacyValue);
    assertHeadersDoNotContain(setCookieHeaders, "Domain=");
    assertNoClearingCookie(setCookieHeaders, SESSION_COOKIE_NAME);
    assertThat(unsafeProbeEndpoint.invocations()).isOne();
    assertThat(meterRegistry.find(LEGACY_CSRF_COOKIE_OBSERVATIONS_METRIC).counter())
        .isNotNull()
        .extracting(counter -> counter.count())
        .isEqualTo(1.0d);
  }

  private CsrfSession openAuthenticatedCsrfSession(MockMvc requestContext) throws Exception {
    return openCsrfSession(requestContext, persistAuthenticatedSession());
  }

  private AuthenticatedSession persistAuthenticatedSession() {
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated("csrf-test", "not-used", List.of());
    Session session = sessionRepository.createSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    sessionRepository.save(session);
    Cookie sessionCookie = sessionCookie(session.getId());
    return new AuthenticatedSession(session.getId(), sessionCookie);
  }

  private CsrfSession openCsrfSession(
      MockMvc requestContext, AuthenticatedSession authenticatedSession) throws Exception {
    var endpointInvocationsBeforeRequest = csrfEndpointInvocationProbe.invocations();
    var csrfResponse =
        requestContext
            .perform(get("/v1/csrf").cookie(authenticatedSession.cookie()))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(csrfEndpointInvocationProbe.invocations())
        .isEqualTo(endpointInvocationsBeforeRequest + 1);
    Map<String, Object> body =
        objectMapper.readValue(
            csrfResponse.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    var token = (String) body.get("token");

    assertThat(body.keySet()).containsExactly("token");
    assertThat(token).isNotBlank();
    assertThat(authenticatedSession.cookie()).isNotNull();
    assertNoXsrfCookie(csrfResponse.getResponse().getHeaders("Set-Cookie"));
    return new CsrfSession(authenticatedSession.id(), authenticatedSession.cookie(), token);
  }

  private Cookie sessionCookie(String sessionId) {
    return sessionCookie(cookieSerializer, SESSION_COOKIE_NAME, sessionId);
  }

  private static Cookie sessionCookie(
      CookieSerializer serializer, String cookieName, String sessionId) {
    var response = new MockHttpServletResponse();
    serializer.writeCookieValue(
        new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, sessionId));
    return response.getCookie(cookieName);
  }

  private void assertSingleCsrfTokenAttribute(String sessionId) {
    Session session = sessionRepository.findById(sessionId);
    assertThat(session).isNotNull();
    assertThat(
            session.getAttributeNames().stream()
                .filter(name -> session.getAttribute(name) instanceof CsrfToken)
                .count())
        .isOne();
  }

  private MockMvc requestContext() {
    return requestContext(context, sessionRepositoryFilter);
  }

  private static MockMvc requestContext(ConfigurableApplicationContext replicaContext) {
    var webContext = (WebApplicationContext) replicaContext;
    var sessionFilter = replicaContext.getBean("springSessionRepositoryFilter", Filter.class);
    return requestContext(webContext, sessionFilter);
  }

  private static MockMvc requestContext(WebApplicationContext webContext, Filter sessionFilter) {
    return webAppContextSetup(webContext).addFilters(sessionFilter).apply(springSecurity()).build();
  }

  private ConfigurableApplicationContext startReplicaContext() {
    return startReplicaContext(SESSION_COOKIE_NAME, "/");
  }

  private ConfigurableApplicationContext startReplicaContext(
      String sessionCookieName, String sessionCookiePath) {
    return new SpringApplicationBuilder(TestApplication.class)
        .profiles("it")
        .web(WebApplicationType.SERVLET)
        .run(
            "--server.port=0",
            "--spring.datasource.url=" + jdbcUrl(),
            "--spring.datasource.username=postgres",
            "--spring.datasource.password=postgres",
            "--spring.datasource.driver-class-name=org.postgresql.Driver",
            "--opencop.db.driver=org.postgresql.Driver",
            "--spring.session.store-type=redis",
            "--spring.data.redis.host=" + LocalRedisSupport.host(),
            "--spring.data.redis.port=" + LocalRedisSupport.port(),
            "--spring.session.servlet.cookie.name=" + sessionCookieName,
            "--spring.session.servlet.cookie.path=" + sessionCookiePath,
            "--spring.session.servlet.cookie.domain=" + CUSTOM_SESSION_COOKIE_DOMAIN,
            "--spring.session.servlet.cookie.secure=true",
            "--spring.session.servlet.cookie.http-only=true",
            "--spring.session.servlet.cookie.same-site=Strict",
            "--opencop.domain=https://app.opencop.test",
            "--idp.audience=CORE-API");
  }

  private String jdbcUrl() {
    try (var connection = dataSource.getConnection()) {
      return connection.getMetaData().getURL();
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to resolve component test database URL", exception);
    }
  }

  private static void assertNoXsrfCookie(Iterable<String> setCookieHeaders) {
    assertNoCookieNamed(setCookieHeaders, LEGACY_CSRF_COOKIE_NAME);
  }

  private static void assertSingleClearingCookie(
      Iterable<String> setCookieHeaders, String cookieName) {
    assertSingleClearingCookie(setCookieHeaders, cookieName, "Path=/", "Max-Age=0");
  }

  private static void assertSingleClearingCookie(
      Iterable<String> setCookieHeaders, String cookieName, String... requiredAttributes) {
    var matchingHeaders =
        StreamSupport.stream(setCookieHeaders.spliterator(), false)
            .filter(header -> header.startsWith(cookieName + "="))
            .toList();
    assertThat(matchingHeaders.size()).isOne();

    var clearingHeader = matchingHeaders.getFirst();
    assertThat(clearingHeader.startsWith(cookieName + "=;")).isTrue();
    for (var requiredAttribute : requiredAttributes) {
      assertThat(clearingHeader.contains(requiredAttribute)).isTrue();
    }
  }

  private void assertRedisIsEmpty() {
    assertThat(redisTemplate.keys("*").isEmpty()).isTrue();
  }

  private static void assertNoCookieNamed(Iterable<String> setCookieHeaders, String cookieName) {
    var cookieWasEmitted =
        StreamSupport.stream(setCookieHeaders.spliterator(), false)
            .anyMatch(header -> header.startsWith(cookieName + "="));
    assertThat(cookieWasEmitted).isFalse();
  }

  private static void assertHeadersDoNotContain(
      Iterable<String> setCookieHeaders, String sensitiveValue) {
    var valueWasExposed =
        StreamSupport.stream(setCookieHeaders.spliterator(), false)
            .anyMatch(header -> header.contains(sensitiveValue));
    assertThat(valueWasExposed).isFalse();
  }

  private static void assertNoClearingCookie(Iterable<String> setCookieHeaders, String cookieName) {
    var clearingCookieWasEmitted =
        StreamSupport.stream(setCookieHeaders.spliterator(), false)
            .anyMatch(
                header -> header.startsWith(cookieName + "=") && header.contains("Max-Age=0"));
    assertThat(clearingCookieWasEmitted).isFalse();
  }

  private record AuthenticatedSession(String id, Cookie cookie) {}

  private record CsrfSession(String sessionId, Cookie cookie, String token) {}

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @ComponentScan(
      basePackages = "com.opencop.core.api.general.technical.endpoint.user",
      useDefaultFilters = false,
      includeFilters =
          @ComponentScan.Filter(
              type = FilterType.REGEX,
              pattern =
                  "com\\.opencop\\.core\\.api\\.general\\.technical\\.endpoint\\.user\\.CsrfEndpoint"))
  @EntityScan(
      basePackageClasses = {
        InternalUserEntity.class,
        InternalGroupEntity.class,
        InternalUserGroupRelationshipEntity.class,
        CurrentAccountAccessEntity.class
      })
  @EnableJpaRepositories(
      basePackageClasses = {InternalUserRepository.class, CurrentAccountAccessRepository.class})
  @Import({
    ComponentTestDatabaseConfig.class,
    RedisSessionConfig.class,
    SecurityConfig.class,
    CurrentAccountAccessFilter.class,
    CurrentAccountAuthenticationFactory.class,
    CurrentAccountAccessFactory.class,
    CurrentAccountAccessPolicy.class,
    ResolveCurrentAccountAccessUsecase.class,
    PostgresCurrentAccountAccessGateway.class,
    CurrentAccountSessionTelemetry.class,
    CurrentAccountCacheTelemetry.class,
    RedisCurrentAccountAccessGateway.class,
    InternalUserService.class,
    StreamAuthorizationTelemetry.class,
    UnsafeProbeEndpoint.class
  })
  static class TestApplication {

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    CsrfEndpointInvocationProbe csrfEndpointInvocationProbe() {
      return new CsrfEndpointInvocationProbe();
    }

    @Bean
    WebMvcConfigurer csrfEndpointInvocationProbeConfigurer(
        CsrfEndpointInvocationProbe csrfEndpointInvocationProbe) {
      return new WebMvcConfigurer() {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
          registry.addInterceptor(csrfEndpointInvocationProbe).addPathPatterns("/v1/csrf");
        }
      };
    }
  }

  static final class CsrfEndpointInvocationProbe implements HandlerInterceptor {

    private final AtomicInteger invocations = new AtomicInteger();

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) {
      invocations.incrementAndGet();
      return true;
    }

    int invocations() {
      return invocations.get();
    }

    void reset() {
      invocations.set(0);
    }
  }

  @RestController
  static final class UnsafeProbeEndpoint {

    private final AtomicInteger invocations = new AtomicInteger();

    @GetMapping("/v1/csrf-probe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read() {
      invocations.incrementAndGet();
    }

    @PostMapping("/v1/csrf-probe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void invoke() {
      invocations.incrementAndGet();
    }

    int invocations() {
      return invocations.get();
    }

    void reset() {
      invocations.set(0);
    }
  }
}
