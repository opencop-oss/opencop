// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.usecase.user.ResolveCurrentAccountAccessUsecase;
import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.api.general.technical.observability.user.CurrentAccountSessionTelemetry;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.business.policy.SessionLifetimePolicy;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class CurrentAccountAccessFilterTest {

  private static final UUID USER_ID = UUID.fromString("b70d53f2-87e1-45da-a354-6072849f4602");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatedRequest_resolvesCurrentAccountAccessExactlyOnce() throws Exception {
    var resolutionCount = new AtomicInteger();
    LockedCurrentAccountAccessGateway gateway =
        internalUserId -> {
          resolutionCount.incrementAndGet();
          return Optional.of(new CurrentAccountAccess(internalUserId, true, false, 0, 0));
        };
    var registry = new SimpleMeterRegistry();
    try {
      var filter = filter(gateway, registry);
      SecurityContextHolder.getContext().setAuthentication(authentication(0));

      filter.doFilter(sessionRequest(), new MockHttpServletResponse(), new MockFilterChain());

      assertEquals(1, resolutionCount.get());
      assertTrue(
          registry
              .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
              .counters()
              .isEmpty());
    } finally {
      registry.close();
    }
  }

  @Test
  void admittedRequestFinishesAcrossAConcurrentGenerationChangeAndTheNextRequestIsRejected()
      throws Exception {
    var currentGeneration = new AtomicLong();
    LockedCurrentAccountAccessGateway gateway =
        internalUserId ->
            Optional.of(
                new CurrentAccountAccess(internalUserId, true, false, currentGeneration.get(), 0));
    var registry = new SimpleMeterRegistry();
    try {
      var filter = filter(gateway, registry);
      var firstRequestEnteredUseCase = new AtomicBoolean();
      SecurityContextHolder.getContext().setAuthentication(authentication(0));

      filter.doFilter(
          sessionRequest(),
          new MockHttpServletResponse(),
          (request, response) -> {
            currentGeneration.incrementAndGet();
            firstRequestEnteredUseCase.set(true);
          });

      var secondRequestEnteredUseCase = new AtomicBoolean();
      var secondResponse = new MockHttpServletResponse();
      SecurityContextHolder.getContext().setAuthentication(authentication(0));
      filter.doFilter(
          sessionRequest(),
          secondResponse,
          (request, response) -> secondRequestEnteredUseCase.set(true));

      assertTrue(firstRequestEnteredUseCase.get());
      assertFalse(secondRequestEnteredUseCase.get());
      assertEquals(401, secondResponse.getStatus());
    } finally {
      registry.close();
    }
  }

  @Test
  void inactiveAccountEmitsOneBoundedSignalAndKeepsResponseGeneric() throws Exception {
    assertDenied(
        Optional.of(new CurrentAccountAccess(USER_ID, false, false, 0, 0)),
        0,
        CurrentAccountAccessDenialReason.INACTIVE);
  }

  @Test
  void staleSessionEmitsOneBoundedSignalAndKeepsResponseGeneric() throws Exception {
    assertDenied(
        Optional.of(new CurrentAccountAccess(USER_ID, true, false, 1, 0)),
        0,
        CurrentAccountAccessDenialReason.STALE_GENERATION);
  }

  @Test
  void missingAccountEmitsOneBoundedSignalAndKeepsResponseGeneric() throws Exception {
    assertDenied(Optional.empty(), 0, CurrentAccountAccessDenialReason.MISSING_ACCOUNT);
  }

  @Test
  void requestWithoutOpenCopPrincipalBypassesResolutionAndTelemetry() throws Exception {
    var resolutionCount = new AtomicInteger();
    LockedCurrentAccountAccessGateway gateway =
        internalUserId -> {
          resolutionCount.incrementAndGet();
          return Optional.empty();
        };
    var registry = new SimpleMeterRegistry();
    try {
      var filter = filter(gateway, registry);

      filter.doFilter(
          new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

      assertEquals(0, resolutionCount.get());
      assertTrue(
          registry
              .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
              .counters()
              .isEmpty());
    } finally {
      registry.close();
    }
  }

  @Test
  void requestAtTheAbsoluteDeadlineIsInvalidatedBeforeCurrentAccountResolution() throws Exception {
    var resolutionCount = new AtomicInteger();
    LockedCurrentAccountAccessGateway gateway =
        internalUserId -> {
          resolutionCount.incrementAndGet();
          return Optional.of(new CurrentAccountAccess(internalUserId, true, false, 0, 0));
        };
    var registry = new SimpleMeterRegistry();
    try {
      var filter =
          filter(
              gateway,
              registry,
              new SessionLifetimePolicy(
                  Duration.ofHours(8),
                  Clock.fixed(Instant.EPOCH.plus(Duration.ofHours(8)), ZoneOffset.UTC)));
      var request = sessionRequest();
      var session = (MockHttpSession) request.getSession(false);
      var response = new MockHttpServletResponse();
      SecurityContextHolder.getContext().setAuthentication(authentication(0));

      filter.doFilter(request, response, new MockFilterChain());

      assertEquals(0, resolutionCount.get());
      assertEquals(401, response.getStatus());
      assertTrue(session.isInvalid());
      assertNull(SecurityContextHolder.getContext().getAuthentication());
    } finally {
      registry.close();
    }
  }

  private static CurrentAccountAccessCacheGateway emptyCache() {
    var cache = mock(CurrentAccountAccessCacheGateway.class);
    when(cache.findByInternalUserId(USER_ID)).thenReturn(Optional.empty());
    return cache;
  }

  private static CurrentAccountAccessFilter filter(
      LockedCurrentAccountAccessGateway gateway, SimpleMeterRegistry registry) {
    return filter(gateway, registry, validLifetimePolicy());
  }

  private static CurrentAccountAccessFilter filter(
      LockedCurrentAccountAccessGateway gateway,
      SimpleMeterRegistry registry,
      SessionLifetimePolicy lifetimePolicy) {
    var cache = emptyCache();
    var transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    return new CurrentAccountAccessFilter(
        new ResolveCurrentAccountAccessUsecase(
            gateway, cache, new CurrentAccountAccessPolicy(), transactionManager),
        new CurrentAccountAuthenticationFactory(),
        new SpaAuthenticationEntryPoint(),
        new CurrentAccountSessionTelemetry(registry),
        lifetimePolicy);
  }

  private static void assertDenied(
      Optional<CurrentAccountAccess> access,
      long loginSessionGeneration,
      CurrentAccountAccessDenialReason expectedReason)
      throws Exception {
    LockedCurrentAccountAccessGateway gateway = internalUserId -> access;
    var registry = new SimpleMeterRegistry();
    try {
      var filter = filter(gateway, registry);
      var request = sessionRequest();
      var session = (MockHttpSession) request.getSession(false);
      var response = new MockHttpServletResponse();
      SecurityContextHolder.getContext().setAuthentication(authentication(loginSessionGeneration));

      filter.doFilter(request, response, new MockFilterChain());

      assertEquals(401, response.getStatus());
      var responseBody = new ObjectMapper().readTree(response.getContentAsByteArray());
      assertEquals("unauthorized", responseBody.get("error").textValue());
      assertEquals("Login required", responseBody.get("message").textValue());
      assertFalse(responseBody.has("reason"));
      assertFalse(response.getContentAsString().contains(expectedReason.value()));
      assertTrue(session.isInvalid());
      assertNull(SecurityContextHolder.getContext().getAuthentication());
      var counter =
          registry
              .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
              .tag("reason", expectedReason.value())
              .counter();
      assertEquals(1.0d, counter.count());
      assertEquals(
          1,
          registry
              .find(CurrentAccountSessionTelemetry.SESSION_INVALIDATIONS_TOTAL)
              .counters()
              .size());
    } finally {
      registry.close();
    }
  }

  private static OAuth2AuthenticationToken authentication(long sessionGeneration) {
    var principal =
        com.opencop.core.sdk.component.security.TestPrincipals.oidcPrincipal(
            USER_ID, sessionGeneration);
    return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "oidc");
  }

  private static MockHttpServletRequest sessionRequest() {
    var request = new MockHttpServletRequest();
    request.getSession();
    return request;
  }

  private static SessionLifetimePolicy validLifetimePolicy() {
    return new SessionLifetimePolicy(
        Duration.ofHours(8), Clock.fixed(Instant.EPOCH.plusSeconds(1), ZoneOffset.UTC));
  }
}
