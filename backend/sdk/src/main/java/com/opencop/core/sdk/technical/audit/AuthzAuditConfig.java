// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.audit;

import com.opencop.core.sdk.ports.audit.AuthzAuditReporting;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AuthzAuditConfig {

  // Boot's SecurityFilterProperties.DEFAULT_FILTER_ORDER, duplicated because
  // the sdk does not depend on spring-boot-autoconfigure. The backstop must
  // register ahead of (outside) springSecurityFilterChain for its
  // final-status read to be global, and the attribution filter behind
  // (inside) it to see a populated SecurityContext — so consuming apps must
  // keep spring.security.filter.order strictly between the two offsets
  // (-10/+10) or the corresponding filter's contract silently breaks. Core
  // pins the relationships in AuthzAuditWiringTest.
  private static final int SPRING_SECURITY_FILTER_ORDER = -100;

  @Bean
  MicrometerAuthzAuditReporter micrometerAuthzAuditReporter(MeterRegistry meterRegistry) {
    return new MicrometerAuthzAuditReporter(meterRegistry);
  }

  @Bean
  AuthzDeniedBackstopFilter authzDeniedBackstopFilter(AuthzAuditReporting reporter) {
    return new AuthzDeniedBackstopFilter(reporter);
  }

  @Bean
  FilterRegistrationBean<AuthzDeniedBackstopFilter> authzDeniedBackstopFilterRegistration(
      AuthzDeniedBackstopFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(SPRING_SECURITY_FILTER_ORDER - 10);
    // Explicit so the ASYNC re-dispatch contract does not rest on Boot's
    // OncePerRequestFilter all-dispatcher-types implicit. ERROR stays listed
    // to match that default; the filter itself skips ERROR dispatches.
    registration.setDispatcherTypes(
        DispatcherType.REQUEST,
        DispatcherType.ASYNC,
        DispatcherType.FORWARD,
        DispatcherType.INCLUDE,
        DispatcherType.ERROR);
    return registration;
  }

  @Bean
  FilterRegistrationBean<AuthzActorAttributionFilter> authzActorAttributionFilterRegistration() {
    var registration = new FilterRegistrationBean<>(new AuthzActorAttributionFilter());
    // Just INSIDE the chain boundary: runs while the SecurityContext is
    // populated on the request's way in, so the backstop (outside, -110) can
    // read the stashed attribution on the unwind. Scoped to the API surface:
    // reading the authentication forces Spring Security's deferred
    // SecurityContext to resolve (a session-store read), which permitAll
    // paths like /csrf and /oauth2/** would otherwise skip — and app-level
    // 403s only occur under /v1 anyway (in-chain denials never reach this
    // filter regardless of scope).
    registration.addUrlPatterns("/v1/*");
    registration.setOrder(SPRING_SECURITY_FILTER_ORDER + 10);
    // ERROR and nested FORWARD/INCLUDE are listed for parity with the
    // backstop registration; OncePerRequestFilter skips them by default.
    registration.setDispatcherTypes(
        DispatcherType.REQUEST,
        DispatcherType.ASYNC,
        DispatcherType.FORWARD,
        DispatcherType.INCLUDE,
        DispatcherType.ERROR);
    return registration;
  }
}
