// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.business.usecase.user.OidcAdmissionUsecase;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.idp.AdmittedOidcUser;
import com.opencop.core.api.general.technical.gateway.idp.OpenCopOidcUserService;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.Roles;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

@ComponentWebIT(classes = OidcAdmissionTestApplication.class)
class OidcAdmissionComponentIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("5d264bdf-447c-4d4a-a47a-ee20d4faf433");
  private static final UUID LEGACY_USER_ID =
      UUID.fromString("a57eb176-3930-459a-a0d3-4bfdef113740");
  private static final String LEGACY_PRINCIPAL_FIXTURE =
      "/com/opencop/core/sdk/technical/security/legacy-open-cop-principal-v1.ser";
  private static final OidcTestProvider IDP = new OidcTestProvider();

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @Autowired private SessionRepository<?> sessionRepository;

  @Autowired
  @Qualifier("springSessionRepositoryFilter")
  private Filter sessionRepositoryFilter;

  @MockitoSpyBean private InternalUserRepository internalUserRepository;
  @MockitoSpyBean private OidcAdmissionUsecase oidcAdmissionUsecase;
  @MockitoSpyBean private OpenCopOidcUserService oidcUserService;
  @MockitoBean private UserPreferenceRepository userPreferenceRepository;
  @MockitoBean private SecretStore secretStore;
  @MockitoBean private RetrieveMedia retrieveMedia;
  @MockitoBean private StreamAuthorizationTelemetry streamAuthorizationTelemetry;

  @DynamicPropertySource
  static void oidcAndRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.session.store-type", () -> "redis");
    registry.add("spring.data.redis.host", LocalRedisSupport::host);
    registry.add("spring.data.redis.port", LocalRedisSupport::port);
    registry.add("redis.host", LocalRedisSupport::host);
    registry.add("redis.port", LocalRedisSupport::port);
    registry.add("opencop.domain", () -> "https://opencop.test");
    registry.add("opencop.idp.issuer", () -> "");
    registry.add("idp.user-id-claim", () -> "sub");
    registry.add("idp.audience", () -> "CORE-API");
    registry.add("idp.allowed.0.group", () -> "group-a");
    registry.add("idp.allowed.0.roles", () -> "operator");
    registry.add("idp.admin.client-id", () -> OidcTestProvider.CLIENT_ID);
    registry.add("idp.admin.client-secret", () -> "test-secret");
    registry.add("idp.authorization-uri", IDP::authorizationUri);
    registry.add("idp.token-uri", IDP::tokenUri);
    registry.add("idp.jwk-set-uri", IDP::jwkSetUri);
    registry.add("idp.user-info-uri", IDP::userInfoUri);
  }

  @BeforeEach
  void prepareProvisionedAccount() {
    mockMvc =
        webAppContextSetup(context)
            .addFilters(sessionRepositoryFilter)
            .apply(springSecurity())
            .build();
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");
    jdbcTemplate.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email,
          first_name, last_name, display_name, active, administrator,
          session_generation, account_access_revision
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        USER_ID,
        OidcTestProvider.SUBJECT,
        "keycloak-user",
        IDP.issuer(),
        "keycloak-user",
        "keycloak.user@example.test",
        "Keycloak",
        "User",
        "Keycloak User",
        true,
        false,
        17L,
        4L);
    when(userPreferenceRepository.findByUserId(USER_ID))
        .thenReturn(
            new UserPreference(
                "keycloak-user",
                "en",
                MeasurementSystem.METRIC,
                TemperatureUnit.CELSIUS,
                "UTC",
                ZoomBehavior.CENTER,
                null,
                null,
                null,
                null));
    when(secretStore.read(org.mockito.ArgumentMatchers.anyString())).thenReturn(Map.of());
  }

  @Test
  void keycloakCallbackPersistsAndConsumesOnlyTheMinimalAdmittedSession() throws Exception {
    var authorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc")
                    .queryParam("redirect_uri", "/map?id=7"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    var authorizationParameters =
        queryParameters(authorization.getResponse().getHeader("Location"));
    assertThat(authorizationParameters)
        .containsKeys("state", "nonce", "code_challenge", "code_challenge_method");
    assertThat(authorizationParameters.get("code_challenge_method")).isEqualTo("S256");
    IDP.prepareNonce(authorizationParameters.get("nonce"));
    var providerAdminAuthorityInjected = injectProviderAdminAuthority();
    var initialSessionCookie = requiredSessionCookie(authorization.getResponse());
    assertThat((String) persistedSession().getAttribute("validated_redirect_uri"))
        .isEqualTo("https://opencop.test/map?id=7");

    var callbackStartedAt = Instant.now();
    var callback =
        mockMvc
            .perform(
                get("/login/oauth2/code/oidc")
                    .secure(true)
                    .cookie(initialSessionCookie)
                    .queryParam("code", "valid-code")
                    .queryParam("state", authorizationParameters.get("state")))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var callbackFinishedAt = Instant.now();

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/map?id=7");
    assertThat(IDP.tokenRequestBody()).contains("code_verifier=");
    assertThat(providerAdminAuthorityInjected.get()).isTrue();
    verify(internalUserRepository)
        .findByIdpIssuerAndIdpUserId(IDP.issuer(), OidcTestProvider.SUBJECT);

    var persistedSession = persistedSession();
    var securityContext =
        (SecurityContext)
            persistedSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertThat(securityContext).isNotNull();
    assertThat(securityContext.getAuthentication()).isInstanceOf(OAuth2AuthenticationToken.class);
    var authentication = (OAuth2AuthenticationToken) securityContext.getAuthentication();
    assertThat(authentication.getAuthorizedClientRegistrationId()).isEqualTo("oidc");
    assertThat(authentication.getAuthorities())
        .extracting(authority -> authority.getAuthority())
        .contains("OIDC_USER", "SCOPE_openid", "SCOPE_profile", "SCOPE_email")
        .doesNotContain("ROLE_admin");

    assertThat(authentication.getPrincipal()).isInstanceOf(OpenCopPrincipal.class);
    assertThat(authentication.getPrincipal()).isNotInstanceOf(OidcUser.class);
    var principal = (OpenCopPrincipal) authentication.getPrincipal();
    assertThat(principal.internalUserId()).isEqualTo(USER_ID);
    assertThat(principal.issuer()).isEqualTo(IDP.issuer());
    assertThat(principal.subject()).isEqualTo(OidcTestProvider.SUBJECT);
    assertThat(principal.idpUserId()).isEqualTo(OidcTestProvider.SUBJECT);
    assertThat(principal.preferredUsername()).isEqualTo("keycloak-user");
    assertThat(principal.email()).isEqualTo("keycloak.user@example.test");
    assertThat(principal.sessionGeneration()).isEqualTo(17);
    assertThat(principal.authenticatedAt())
        .isBetween(callbackStartedAt.minusSeconds(1), callbackFinishedAt.plusSeconds(1));
    assertThat(principal.getAuthorities()).isEmpty();
    assertThat(principal.getAttributes()).doesNotContainKey("arbitrary_claim");
    assertThat(ReflectionTestUtils.getField(principal, "delegate")).isNull();
    assertMinimalRedisPayload(persistedSession.getId());
    var persistedSecurityContext = persistedSecurityContextBytes(persistedSession.getId());

    var callbackCookie = sessionCookie(callback.getResponse());
    var authenticatedCookie = callbackCookie == null ? initialSessionCookie : callbackCookie;
    var me =
        mockMvc
            .perform(get("/v1/me").secure(true).cookie(copy(authenticatedCookie)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(me.getResponse().getContentAsString())
        .contains(
            USER_ID.toString(),
            "\"subject\":\"verified-subject\"",
            "\"username\":\"keycloak-user\"",
            "\"issuer\":\"" + IDP.issuer() + "\"",
            "\"SCOPE_profile\"")
        .doesNotContain(Roles.ADMIN);
    assertThat(persistedSecurityContextBytes(persistedSession.getId()))
        .isEqualTo(persistedSecurityContext);
  }

  @Test
  void anonymousCsrfDenialDuringOidcAuthorizationPreservesPendingSessionAndCallback()
      throws Exception {
    var authorization = authorize();
    var pendingSessionId = persistedSession().getId();
    IDP.prepareNonce(authorization.nonce());

    mockMvc
        .perform(post("/v1/me").secure(true).cookie(copy(authorization.sessionCookie())))
        .andExpect(status().isUnauthorized());

    assertThat(persistedSession().getId()).isEqualTo(pendingSessionId);

    var callback = callback(authorization);

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_success");
    assertThat(persistedPrincipal().internalUserId()).isEqualTo(USER_ID);
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  @Test
  void retainedLegacyRedisSession_isSanitizedPerRequestWithoutRewritingItsSecurityContext()
      throws Exception {
    var authorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var sessionCookie = requiredSessionCookie(authorization.getResponse());
    var redisSession = persistedSession();
    var legacyPrincipal = legacyPrincipal();
    var authentication =
        new OAuth2AuthenticationToken(legacyPrincipal, legacyPrincipal.getAuthorities(), "oidc");
    var securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    persistSecurityContext(redisSession.getId(), securityContext);
    jdbcTemplate.update(
        """
        UPDATE opencop_user
        SET id = ?, administrator = false, session_generation = 7
        WHERE id = ?
        """,
        LEGACY_USER_ID,
        USER_ID);
    when(userPreferenceRepository.findByUserId(LEGACY_USER_ID))
        .thenReturn(
            new UserPreference(
                "legacy-user",
                "en",
                MeasurementSystem.METRIC,
                TemperatureUnit.CELSIUS,
                "UTC",
                ZoomBehavior.CENTER,
                null,
                null,
                null,
                null));
    var persistedSecurityContext = persistedSecurityContextBytes(redisSession.getId());

    var me =
        mockMvc
            .perform(get("/v1/me").secure(true).cookie(copy(sessionCookie)))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(me.getResponse().getContentAsString())
        .contains(LEGACY_USER_ID.toString(), "\"SCOPE_openid\"", "\"SCOPE_profile\"")
        .doesNotContain(Roles.ADMIN);
    assertThat(persistedSecurityContextBytes(redisSession.getId()))
        .isEqualTo(persistedSecurityContext);
  }

  @Test
  void relationshipDenialRedirectsToAccessDeniedAndDeletesTheTemporarySession() throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce())
        .withUserInfo(userInfo("[\"other-group\"]", "[\"operator\"]"));

    assertRejected(authorization, "https://opencop.test/access_denied");
  }

  @Test
  void missingProvisionedAccountRedirectsToAccessDeniedAndDeletesTheTemporarySession()
      throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce());
    UserFixtures.releaseUserReferences(jdbcTemplate);
    jdbcTemplate.update("DELETE FROM opencop_user");

    assertRejected(authorization, "https://opencop.test/access_denied");
  }

  @Test
  void inactiveCurrentAccountRedirectsToAccessDeniedAndDeletesTheTemporarySession()
      throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce());
    jdbcTemplate.update("UPDATE opencop_user SET active = false");

    assertRejected(authorization, "https://opencop.test/access_denied");
  }

  @Test
  void deniedReloginPreservesTheEstablishedAuthenticatedSession() throws Exception {
    var initialAuthorization = authorize();
    IDP.prepareNonce(initialAuthorization.nonce());
    var successfulCallback = callback(initialAuthorization);
    var callbackCookie = sessionCookie(successfulCallback.getResponse());
    var authenticatedCookie =
        callbackCookie == null ? initialAuthorization.sessionCookie() : callbackCookie;
    var authenticatedSession = persistedSession();
    var authenticatedSessionId = authenticatedSession.getId();
    var persistedSecurityContext = persistedSecurityContextBytes(authenticatedSessionId);

    var reloginAuthorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc")
                    .cookie(copy(authenticatedCookie))
                    .queryParam("redirect_uri", "/map?id=failed-relogin"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var reloginParameters =
        queryParameters(reloginAuthorization.getResponse().getHeader("Location"));
    IDP.prepareNonce(reloginParameters.get("nonce"))
        .withUserInfo(userInfo("[\"other-group\"]", "[\"operator\"]"));

    var failedCallback =
        mockMvc
            .perform(
                get("/login/oauth2/code/oidc")
                    .secure(true)
                    .cookie(copy(authenticatedCookie))
                    .queryParam("code", "denied-relogin-code")
                    .queryParam("state", reloginParameters.get("state")))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(failedCallback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/access_denied");
    assertThat(failedCallback.getResponse().getHeaders("Set-Cookie"))
        .noneMatch(header -> header.startsWith("OPENCOP_BFF=") && header.contains("Max-Age=0"));
    assertThat(persistedSession().getId()).isEqualTo(authenticatedSessionId);
    assertThat((Object) persistedSession().getAttribute("validated_redirect_uri")).isNull();
    assertThat(persistedSecurityContextBytes(authenticatedSessionId))
        .isEqualTo(persistedSecurityContext);

    var me =
        mockMvc
            .perform(get("/v1/me").secure(true).cookie(copy(authenticatedCookie)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(me.getResponse().getContentAsString()).contains(USER_ID.toString());
    assertThat(persistedSecurityContextBytes(authenticatedSessionId))
        .isEqualTo(persistedSecurityContext);
  }

  @Test
  void missingConfiguredAudienceRedirectsToLoginFailedAndDeletesTheTemporarySession()
      throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce()).withAudiences(List.of(OidcTestProvider.CLIENT_ID));

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @Test
  void mismatchedConfiguredAudienceRedirectsToLoginFailedAndDeletesTheTemporarySession()
      throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce())
        .withAudiences(List.of(OidcTestProvider.CLIENT_ID, "OTHER-API"));

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @Test
  void invalidTokenNonceRedirectsToLoginFailedAndDeletesTheTemporarySession() throws Exception {
    var authorization = authorize();
    IDP.prepareNonce("wrong-nonce");

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @ParameterizedTest(name = "{0}={1}")
  @MethodSource("malformedAccessClaims")
  void malformedAccessClaimRedirectsToLoginFailedAndDeletesTheTemporarySession(
      String claimName, String claimValue) throws Exception {
    var authorization = authorize();
    var groups = "groups".equals(claimName) ? claimValue : "[\"group-a\"]";
    var roles = "roles".equals(claimName) ? claimValue : "[\"operator\"]";
    IDP.prepareNonce(authorization.nonce()).withUserInfo(userInfo(groups, roles));

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @ParameterizedTest(name = "admissible access claims: {0}")
  @MethodSource("admissibleEmptyAccessClaims")
  void missingOrEmptyAccessClaimsReachAdmissionThenRedirectToAccessDenied(
      String description, String userInfo) throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce()).withUserInfo(userInfo);

    assertRejected(authorization, "https://opencop.test/access_denied");
  }

  @Test
  void malformedOptionalProfileClaimsAreOmittedWithoutRejectingTheCallback() throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce())
        .withUserInfo(
            """
            {
              "sub":"verified-subject",
              "groups":["group-a"],
              "roles":["operator"],
              "preferred_username":42,
              "name":false,
              "given_name":["not-a-string"],
              "family_name":{"value":"not-a-string"},
              "email":true
            }
            """);

    var callback = callback(authorization);

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_success");
    var principal = persistedPrincipal();
    assertThat(principal.preferredUsername()).isNull();
    assertThat(principal.fullName()).isNull();
    assertThat(principal.givenName()).isNull();
    assertThat(principal.familyName()).isNull();
    assertThat(principal.email()).isNull();
    assertThat(principal.emailVerified()).isNull();
  }

  @Test
  void unsupportedCallbackPrincipalRedirectsToLoginFailedWithoutPersistingIt() throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce());
    var unsupportedPrincipal = mock(OidcUser.class);
    doReturn(List.of(new SimpleGrantedAuthority("OIDC_USER")))
        .when(unsupportedPrincipal)
        .getAuthorities();
    when(unsupportedPrincipal.getName()).thenReturn("unsupported-principal");
    doReturn(unsupportedPrincipal).when(oidcUserService).loadUser(any());

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @Test
  void unexpectedAdmissionFailureRedirectsToLoginFailedWithoutLeakingItsCause() throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce());
    doThrow(new IllegalStateException("sensitive-claim-value"))
        .when(oidcAdmissionUsecase)
        .admit(any());

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @Test
  void conflictingUserInfoSubjectIsRejectedWithoutCreatingAnAuthenticatedSession()
      throws Exception {
    var authorization = authorize();
    IDP.prepareNonce(authorization.nonce())
        .withUserInfo(
            """
            {
              "sub":"conflicting-user-info-subject",
              "groups":["group-a"],
              "roles":["operator"]
            }
            """);

    assertRejected(authorization, "https://opencop.test/login_failed");
  }

  @Test
  void missingAuthorizationRequestRetainsTheExactMultiTabRedirectAndCreatesNoSession()
      throws Exception {
    var callback =
        mockMvc
            .perform(
                get("/login/oauth2/code/oidc")
                    .secure(true)
                    .queryParam("code", "orphaned-code")
                    .queryParam("state", "orphaned-state"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_failed?reason=auth_request_not_found");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(persistedSessionKeys()).isEmpty();
  }

  private static Stream<Arguments> malformedAccessClaims() {
    return Stream.of(
        Arguments.of("groups", "\"group-a\""),
        Arguments.of("groups", "[\"group-a\",42]"),
        Arguments.of("groups", "[[\"group-a\"]]"),
        Arguments.of("groups", "[42]"),
        Arguments.of("groups", "[true]"),
        Arguments.of("groups", "[\" \"]"),
        Arguments.of("groups", "[{\"name\":\"group-a\"}]"),
        Arguments.of("roles", "\"operator\""),
        Arguments.of("roles", "[\"operator\",42]"),
        Arguments.of("roles", "[[\"operator\"]]"),
        Arguments.of("roles", "[42]"),
        Arguments.of("roles", "[true]"),
        Arguments.of("roles", "[\" \"]"),
        Arguments.of("roles", "[{\"name\":\"operator\"}]"));
  }

  private static Stream<Arguments> admissibleEmptyAccessClaims() {
    return Stream.of(
        Arguments.of(
            "missing",
            """
            {
              "sub":"verified-subject"
            }
            """),
        Arguments.of(
            "empty",
            """
            {
              "sub":"verified-subject",
              "groups":[],
              "roles":[]
            }
            """));
  }

  private AuthorizationAttempt authorize() throws Exception {
    var authorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var parameters = queryParameters(authorization.getResponse().getHeader("Location"));
    return new AuthorizationAttempt(
        requiredSessionCookie(authorization.getResponse()),
        parameters.get("state"),
        parameters.get("nonce"));
  }

  private MvcResult callback(AuthorizationAttempt authorization) throws Exception {
    return mockMvc
        .perform(
            get("/login/oauth2/code/oidc")
                .secure(true)
                .cookie(authorization.sessionCookie())
                .queryParam("code", "callback-code")
                .queryParam("state", authorization.state()))
        .andExpect(status().is3xxRedirection())
        .andReturn();
  }

  private void assertRejected(AuthorizationAttempt authorization, String expectedRedirect)
      throws Exception {
    var callback = callback(authorization);

    assertThat(callback.getResponse().getRedirectedUrl()).isEqualTo(expectedRedirect);
    assertThat(callback.getResponse().getRedirectedUrl())
        .doesNotContain("sensitive-claim-value", "verified-subject", "group-a", "operator");
    assertThat(callback.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(
            header ->
                header.startsWith("OPENCOP_BFF=")
                    && (header.contains("Max-Age=0") || header.contains("Max-Age=0;")));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(persistedSessionKeys()).isEmpty();
  }

  private OpenCopPrincipal persistedPrincipal() {
    var securityContext =
        (SecurityContext)
            persistedSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    return (OpenCopPrincipal) securityContext.getAuthentication().getPrincipal();
  }

  private List<String> persistedSessionKeys() {
    return redisTemplate.keys("opencop:bff:sessions:*").stream()
        .filter(key -> redisTemplate.type(key) == DataType.HASH)
        .filter(
            key ->
                sessionRepository.findById(key.substring("opencop:bff:sessions:".length())) != null)
        .toList();
  }

  private static String userInfo(String groups, String roles) {
    return """
        {
          "sub":"verified-subject",
          "groups":%s,
          "roles":%s
        }
        """
        .formatted(groups, roles);
  }

  private void assertMinimalRedisPayload(String sessionId) {
    var key = ("opencop:bff:sessions:" + sessionId).getBytes(StandardCharsets.UTF_8);
    try (var connection = redisConnectionFactory.getConnection()) {
      assertThat(connection.keyCommands().exists(key)).isTrue();
      var serializedValues = connection.hashCommands().hGetAll(key).values();
      var payload =
          new String(
              serializedValues.stream()
                  .reduce(
                      new byte[0],
                      (left, right) -> {
                        var joined = Arrays.copyOf(left, left.length + right.length);
                        System.arraycopy(right, 0, joined, left.length, right.length);
                        return joined;
                      }),
              StandardCharsets.ISO_8859_1);
      assertThat(payload)
          .doesNotContain(
              "callback-access-token",
              "arbitrary_claim",
              "must-not-enter-session",
              "not-a-string",
              "DefaultOidcUser",
              "OidcIdToken",
              "ROLE_admin");
    }
  }

  private byte[] persistedSecurityContextBytes(String sessionId) {
    var key = ("opencop:bff:sessions:" + sessionId).getBytes(StandardCharsets.UTF_8);
    try (var connection = redisConnectionFactory.getConnection()) {
      return connection.hashCommands().hGetAll(key).entrySet().stream()
          .filter(
              entry ->
                  new String(entry.getKey(), StandardCharsets.UTF_8)
                      .contains(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
          .map(Map.Entry::getValue)
          .findFirst()
          .orElseThrow()
          .clone();
    }
  }

  private void persistSecurityContext(String sessionId, SecurityContext securityContext) {
    var key = ("opencop:bff:sessions:" + sessionId).getBytes(StandardCharsets.UTF_8);
    var attribute =
        ("sessionAttr:" + HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
            .getBytes(StandardCharsets.UTF_8);
    var value = new JdkSerializationRedisSerializer().serialize(securityContext);
    try (var connection = redisConnectionFactory.getConnection()) {
      connection.hashCommands().hSet(key, attribute, value);
    }
  }

  private AtomicBoolean injectProviderAdminAuthority() {
    var injected = new AtomicBoolean();
    doAnswer(
            invocation -> {
              var admittedUser = (AdmittedOidcUser) invocation.callRealMethod();
              var providerUser = mock(OidcUser.class);
              doReturn(admittedUser.getClaims()).when(providerUser).getClaims();
              doReturn(admittedUser.getUserInfo()).when(providerUser).getUserInfo();
              doReturn(admittedUser.getIdToken()).when(providerUser).getIdToken();
              doReturn(admittedUser.getAttributes()).when(providerUser).getAttributes();
              doReturn(admittedUser.getName()).when(providerUser).getName();
              var authorities =
                  Stream.concat(
                          admittedUser.getAuthorities().stream(),
                          Stream.of(new SimpleGrantedAuthority(Roles.ADMIN)))
                      .toList();
              doReturn(authorities).when(providerUser).getAuthorities();
              injected.set(true);
              return new AdmittedOidcUser(providerUser, admittedUser.admittedIdentity());
            })
        .when(oidcUserService)
        .loadUser(any());
    return injected;
  }

  /**
   * AUTH_SESSION_LEGACY_PRINCIPAL_CLEANUP: remove after no deployed release can write the legacy
   * representation and eight hours have elapsed since the last legacy writer left service. A
   * rollback to a legacy writer resets that gate.
   */
  private static OpenCopPrincipal legacyPrincipal() throws Exception {
    try (var fixture =
            OidcAdmissionComponentIT.class.getResourceAsStream(LEGACY_PRINCIPAL_FIXTURE);
        var input = new ObjectInputStream(fixture)) {
      return (OpenCopPrincipal) input.readObject();
    }
  }

  private Session persistedSession() {
    var sessionKeys =
        redisTemplate.keys("opencop:bff:sessions:*").stream()
            .filter(key -> redisTemplate.type(key) == DataType.HASH)
            .toList();
    assertThat(sessionKeys).hasSize(1);
    var key = sessionKeys.getFirst();
    var sessionId = key.substring("opencop:bff:sessions:".length());
    var session = sessionRepository.findById(sessionId);
    assertThat(session).isNotNull();
    return session;
  }

  private static Cookie copy(Cookie source) {
    var copy = new Cookie(source.getName(), source.getValue());
    copy.setPath(source.getPath());
    copy.setSecure(source.getSecure());
    copy.setHttpOnly(source.isHttpOnly());
    return copy;
  }

  private static Cookie requiredSessionCookie(MockHttpServletResponse response) {
    var cookie = sessionCookie(response);
    assertThat(cookie).isNotNull();
    return cookie;
  }

  private static Cookie sessionCookie(MockHttpServletResponse response) {
    var mockCookie = response.getCookie("OPENCOP_BFF");
    if (mockCookie != null) {
      return mockCookie;
    }
    return response.getHeaders("Set-Cookie").stream()
        .filter(header -> header.startsWith("OPENCOP_BFF="))
        .findFirst()
        .map(
            header -> {
              var nameAndValue = header.substring(0, header.indexOf(';')).split("=", 2);
              return new Cookie(nameAndValue[0], nameAndValue[1]);
            })
        .orElse(null);
  }

  private static Map<String, String> queryParameters(String location) {
    assertThat(location).isNotNull();
    var query = URI.create(location).getRawQuery();
    var parameters = new LinkedHashMap<String, String>();
    for (var pair : query.split("&")) {
      var parts = pair.split("=", 2);
      parameters.put(decode(parts[0]), parts.length == 1 ? "" : decode(parts[1]));
    }
    return parameters;
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private record AuthorizationAttempt(Cookie sessionCookie, String state, String nonce) {}
}
