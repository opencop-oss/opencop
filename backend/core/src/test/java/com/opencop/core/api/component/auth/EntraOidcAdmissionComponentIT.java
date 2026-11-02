// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.opencop.core.api.component.support.AbstractWebIT;
import com.opencop.core.api.component.support.fixture.UserFixtures;
import com.opencop.core.api.general.business.model.user.UserPreference;
import com.opencop.core.api.general.ports.gateway.repository.UserPreferenceRepository;
import com.opencop.core.api.general.ports.usecase.media.RetrieveMedia;
import com.opencop.core.api.general.technical.endpoint.media.StreamAuthorizationTelemetry;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.sdk.business.model.MeasurementSystem;
import com.opencop.core.sdk.business.model.TemperatureUnit;
import com.opencop.core.sdk.business.model.ZoomBehavior;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.redis.LocalRedisSupport;
import com.opencop.core.sdk.ports.gateway.secrets.SecretStore;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
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
class EntraOidcAdmissionComponentIT extends AbstractWebIT {

  private static final UUID USER_ID = UUID.fromString("83cc0afa-6251-42e9-9d1f-8b6f54039c37");
  private static final String ENTRA_SUBJECT = "entra-verified-subject";
  private static final String ENTRA_OBJECT_ID = "f7e2bc76-a0e3-49e4-98d1-30d6cb58ebde";
  private static final OidcTestProvider IDP = new OidcTestProvider();

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private RedisConnectionFactory redisConnectionFactory;
  @Autowired private SessionRepository<?> sessionRepository;

  @Autowired
  @Qualifier("springSessionRepositoryFilter")
  private Filter sessionRepositoryFilter;

  @MockitoSpyBean private InternalUserRepository internalUserRepository;
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
    registry.add("idp.user-id-claim", () -> "oid");
    registry.add("idp.audience", () -> "CORE-API");
    registry.add("idp.allowed.0.group", () -> "entra-group");
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
        ENTRA_OBJECT_ID,
        "entra-user",
        IDP.issuer(),
        "entra-user",
        "entra.user@example.test",
        "Entra",
        "User",
        "Entra User",
        true,
        false,
        23L,
        6L);
    when(userPreferenceRepository.findByUserId(USER_ID))
        .thenReturn(
            new UserPreference(
                "entra-user",
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
  void entraCallbackUsesVerifiedOidAndPersistsTheProviderNeutralMinimalSession() throws Exception {
    var authorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var authorizationParameters =
        queryParameters(authorization.getResponse().getHeader("Location"));
    IDP.prepareEntraNonce(authorizationParameters.get("nonce"), ENTRA_SUBJECT, ENTRA_OBJECT_ID);
    var initialSessionCookie = requiredSessionCookie(authorization.getResponse());

    var callback =
        mockMvc
            .perform(
                get("/login/oauth2/code/oidc")
                    .secure(true)
                    .cookie(initialSessionCookie)
                    .queryParam("code", "valid-entra-code")
                    .queryParam("state", authorizationParameters.get("state")))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_success");
    verify(internalUserRepository).findByIdpIssuerAndIdpUserId(IDP.issuer(), ENTRA_OBJECT_ID);

    var persistedSession = persistedSession();
    var securityContext =
        (SecurityContext)
            persistedSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
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
    assertThat(principal.subject()).isEqualTo(ENTRA_SUBJECT);
    assertThat(principal.getName()).isEqualTo(ENTRA_SUBJECT);
    assertThat(principal.idpUserId()).isEqualTo(ENTRA_OBJECT_ID);
    assertThat(principal.preferredUsername()).isEqualTo("entra-user");
    assertThat(principal.email()).isEqualTo("entra.user@example.test");
    assertThat(principal.sessionGeneration()).isEqualTo(23);
    assertThat(principal.getAuthorities()).isEmpty();
    assertThat(principal.getAttributes())
        .doesNotContainKeys("arbitrary_claim", "oid")
        .doesNotContainValue("untrusted-user-info-object-id");
    assertThat(ReflectionTestUtils.getField(principal, "delegate")).isNull();
    assertMinimalRedisPayload(persistedSession.getId());

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
            "\"subject\":\"" + ENTRA_SUBJECT + "\"",
            "\"username\":\"entra-user\"",
            "\"issuer\":\"" + IDP.issuer() + "\"");
  }

  @Test
  void missingConfiguredOidRedirectsToLoginFailedWithoutPersistingAuthentication()
      throws Exception {
    assertInvalidObjectId(null);
  }

  @Test
  void blankConfiguredOidRedirectsToLoginFailedWithoutPersistingAuthentication() throws Exception {
    assertInvalidObjectId(" ");
  }

  @Test
  void nonStringConfiguredOidRedirectsToLoginFailedWithoutPersistingAuthentication()
      throws Exception {
    assertInvalidObjectId(42);
  }

  private void assertInvalidObjectId(Object objectId) throws Exception {
    var authorization =
        mockMvc
            .perform(
                get("/oauth2/authorization/oidc")
                    .secure(true)
                    .servletPath("/oauth2/authorization/oidc"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    var authorizationParameters =
        queryParameters(authorization.getResponse().getHeader("Location"));
    IDP.prepareEntraNonce(authorizationParameters.get("nonce"), ENTRA_SUBJECT, ENTRA_OBJECT_ID)
        .withObjectId(objectId);
    var callback =
        callback(
            requiredSessionCookie(authorization.getResponse()),
            authorizationParameters.get("state"));

    assertThat(callback.getResponse().getRedirectedUrl())
        .isEqualTo("https://opencop.test/login_failed");
    assertThat(callback.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(header -> header.startsWith("OPENCOP_BFF=") && header.contains("Max-Age=0"));
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(persistedSessionKeys()).isEmpty();
  }

  private MvcResult callback(Cookie sessionCookie, String state) throws Exception {
    return mockMvc
        .perform(
            get("/login/oauth2/code/oidc")
                .secure(true)
                .cookie(sessionCookie)
                .queryParam("code", "invalid-identity-code")
                .queryParam("state", state))
        .andExpect(status().is3xxRedirection())
        .andReturn();
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
              "untrusted-user-info-object-id",
              "DefaultOidcUser",
              "OidcIdToken",
              "ROLE_admin");
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

  private List<String> persistedSessionKeys() {
    return redisTemplate.keys("opencop:bff:sessions:*").stream()
        .filter(key -> redisTemplate.type(key) == DataType.HASH)
        .filter(
            key ->
                sessionRepository.findById(key.substring("opencop:bff:sessions:".length())) != null)
        .toList();
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
}
