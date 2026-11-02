// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.opencop.core.api.component.support.fixture.CurrentAccountAccessFixtures;
import com.opencop.core.api.general.business.factory.user.CurrentAccountAccessFactory;
import com.opencop.core.api.general.business.policy.AccountMutationPolicy;
import com.opencop.core.api.general.business.usecase.user.GlobalSignOutUsecase;
import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.technical.endpoint.auth.GlobalSignOutEndpoint;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.CurrentAccountCacheTelemetry;
import com.opencop.core.api.general.technical.gateway.cache.RedisCurrentAccountAccessGateway;
import com.opencop.core.api.general.technical.gateway.db.user.InternalUserService;
import com.opencop.core.api.general.technical.gateway.db.user.PostgresAccountMutationGateway;
import com.opencop.core.api.general.technical.gateway.scim.group.InternalGroupEntity;
import com.opencop.core.api.general.technical.gateway.scim.relationship.InternalUserGroupRelationshipEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserEntity;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.component.security.TestPrincipals;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessEntity;
import com.opencop.core.sdk.technical.gateway.db.account.CurrentAccountAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.account.PostgresCurrentAccountAccessGateway;
import com.opencop.core.sdk.technical.security.session.RedisSessionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.core.type.TypeReference;

@ComponentWebIT(classes = GlobalSignOutComponentIT.TestApplication.class)
class GlobalSignOutComponentIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("d26c01d8-6239-48e0-9777-2713d6951863");
  private static final String SESSION_COOKIE_NAME = "CUSTOM_BFF";
  private static final String SESSION_COOKIE_PATH = "/v1";
  private static final String SESSION_COOKIE_DOMAIN = "opencop.test";

  @Autowired private SessionRepository sessionRepository;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CookieSerializer cookieSerializer;

  @Autowired
  @Qualifier("springSessionRepositoryFilter")
  private Filter sessionRepositoryFilter;

  private CurrentAccountAccessFixtures fixtures;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
    registry.add("spring.session.servlet.cookie.name", () -> SESSION_COOKIE_NAME);
    registry.add("spring.session.servlet.cookie.path", () -> SESSION_COOKIE_PATH);
    registry.add("spring.session.servlet.cookie.domain", () -> SESSION_COOKIE_DOMAIN);
    registry.add("opencop.domain", () -> "https://app.opencop.test");
    registry.add("idp.audience", () -> "CORE-API");
  }

  @BeforeEach
  void setUp() {
    mockMvc =
        webAppContextSetup(context)
            .addFilters(sessionRepositoryFilter)
            .apply(springSecurity())
            .build();
    fixtures = new CurrentAccountAccessFixtures(jdbcTemplate);
    fixtures.reset();
    fixtures.seedUser(USER_ID, true, false);
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  void globalSignOutInvalidatesOnlyTheRequesterAndLeavesOtherSessionsForLogicalRejection()
      throws Exception {
    var requestingSession = openAuthenticatedCsrfSession();
    String otherSessionId = persistSession(0);

    var result =
        mockMvc
            .perform(
                post("/api/v1/bff/logout")
                    .contextPath("/api")
                    .cookie(requestingSession.cookie())
                    .header("X-XSRF-TOKEN", requestingSession.token()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertNull(sessionRepository.findById(requestingSession.id()));
    assertNotNull(sessionRepository.findById(otherSessionId));
    assertTrue(fixtures.active(USER_ID));
    assertEquals(1, fixtures.sessionGeneration(USER_ID));
    assertNull(result.getResponse().getHeader("Location"));
    var sessionCookieHeader =
        clearingCookie(result.getResponse().getHeaders("Set-Cookie"), SESSION_COOKIE_NAME);
    assertNotNull(sessionCookieHeader);
    assertTrue(sessionCookieHeader.contains("Domain=" + SESSION_COOKIE_DOMAIN));
    assertTrue(sessionCookieHeader.contains("Path=" + SESSION_COOKIE_PATH));
    assertFalse(hasClearingCookie(result.getResponse().getHeaders("Set-Cookie"), "OPENCOP_BFF"));
    assertNoXsrfCookie(result.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void missingCsrfHeaderReturnsExactForbiddenAndRetainsTheSession() throws Exception {
    var requestingSession = openAuthenticatedCsrfSession();

    var result =
        mockMvc
            .perform(post("/v1/bff/logout").cookie(requestingSession.cookie()))
            .andExpect(status().isForbidden())
            .andExpect(content().string("{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}"))
            .andReturn();

    assertNotNull(sessionRepository.findById(requestingSession.id()));
    assertTrue(fixtures.active(USER_ID));
    assertEquals(0, fixtures.sessionGeneration(USER_ID));
    assertNoXsrfCookie(result.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void wrongCsrfHeaderReturnsExactForbiddenAndRetainsTheSession() throws Exception {
    var requestingSession = openAuthenticatedCsrfSession();

    var result =
        mockMvc
            .perform(
                post("/v1/bff/logout")
                    .cookie(requestingSession.cookie())
                    .header("X-XSRF-TOKEN", "wrong-token"))
            .andExpect(status().isForbidden())
            .andExpect(content().string("{\"error\":\"forbidden\",\"code\":\"CSRF_REJECTED\"}"))
            .andReturn();

    assertNotNull(sessionRepository.findById(requestingSession.id()));
    assertTrue(fixtures.active(USER_ID));
    assertEquals(0, fixtures.sessionGeneration(USER_ID));
    assertNoXsrfCookie(result.getResponse().getHeaders("Set-Cookie"));
  }

  @Test
  void aRetainedStaleSessionIsRejectedAndInvalidatedOnItsNextCoreRequest() throws Exception {
    var requestingSession = openAuthenticatedCsrfSession();
    var staleSession = openCsrfSession(persistSession(0));

    var signOut =
        mockMvc
            .perform(
                post("/v1/bff/logout")
                    .cookie(requestingSession.cookie())
                    .header("X-XSRF-TOKEN", requestingSession.token()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertNoXsrfCookie(signOut.getResponse().getHeaders("Set-Cookie"));
    assertNotNull(sessionRepository.findById(staleSession.id()));

    var rejected =
        mockMvc
            .perform(
                post("/v1/bff/logout")
                    .cookie(staleSession.cookie())
                    .header("X-XSRF-TOKEN", staleSession.token()))
            .andExpect(status().isUnauthorized())
            .andReturn();

    assertNoXsrfCookie(rejected.getResponse().getHeaders("Set-Cookie"));
    assertNull(sessionRepository.findById(staleSession.id()));
  }

  private String persistSession(long generation) {
    var principal = TestPrincipals.oidcPrincipal(USER_ID, generation);
    var authentication =
        new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "oidc");
    Session session = sessionRepository.createSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
        new SecurityContextImpl(authentication));
    sessionRepository.save(session);
    return session.getId();
  }

  private CsrfSession openAuthenticatedCsrfSession() throws Exception {
    return openCsrfSession(persistSession(0));
  }

  private CsrfSession openCsrfSession(String sessionId) throws Exception {
    var sessionCookie = sessionCookie(sessionId);
    var csrfResponse =
        mockMvc
            .perform(get("/v1/csrf").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andReturn();
    Map<String, Object> body =
        objectMapper.readValue(
            csrfResponse.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    var token = (String) body.get("token");

    assertEquals(1, body.size());
    assertNotNull(token);
    assertFalse(token.isBlank());
    assertNoXsrfCookie(csrfResponse.getResponse().getHeaders("Set-Cookie"));
    return new CsrfSession(sessionId, sessionCookie, token);
  }

  private static boolean hasClearingCookie(List<String> headers, String cookieName) {
    return clearingCookie(headers, cookieName) != null;
  }

  private static String clearingCookie(List<String> headers, String cookieName) {
    return headers.stream()
        .filter(header -> header.startsWith(cookieName + "=") && header.contains("Max-Age=0"))
        .findFirst()
        .orElse(null);
  }

  private static void assertNoXsrfCookie(List<String> headers) {
    assertFalse(headers.stream().anyMatch(header -> header.startsWith("XSRF-TOKEN=")));
  }

  private Cookie sessionCookie(String sessionId) {
    var response = new MockHttpServletResponse();
    cookieSerializer.writeCookieValue(
        new CookieSerializer.CookieValue(new MockHttpServletRequest(), response, sessionId));
    Cookie cookie = response.getCookie(SESSION_COOKIE_NAME);
    assertNotNull(cookie);
    return cookie;
  }

  private record CsrfSession(String id, Cookie cookie, String token) {}

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
    AccountMutationPolicy.class,
    GlobalSignOutUsecase.class,
    GlobalSignOutEndpoint.class,
    PostgresAccountMutationGateway.class,
    CurrentAccountCacheTelemetry.class,
    RedisCurrentAccountAccessGateway.class,
    InternalUserService.class,
    StreamAuthorizationTelemetry.class
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
  }
}
