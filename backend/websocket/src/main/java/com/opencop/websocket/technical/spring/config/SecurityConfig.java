// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain wsSecurity(HttpSecurity http) {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health")
                    .permitAll()
                    // allow websocket handshake only if authenticated via session
                    // The SessionSecurityContextInterceptor will handle the auth at handshake
                    .requestMatchers("/ws")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        // no login redirects, WS service is not responsible for login
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint((req, res, e) -> res.sendError(401))
                    .accessDeniedHandler((req, res, e) -> res.sendError(403)))
        // CSRF is not relevant for WS handshake
        .csrf(AbstractHttpConfigurer::disable);

    return http.build();
  }

  @Bean
  public HttpSessionSecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }
}
