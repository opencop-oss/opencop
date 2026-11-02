// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.common.scim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@Import(ScimJsonConfig.class)
public class ScimClientConfig {

  @Bean
  @Order(0)
  SecurityFilterChain scimChain(
      HttpSecurity http,
      @Value("${opencop.scim.bearer-token}") String scimToken,
      @Value("${opencop.scim.inbound-logging-enabled:false}") boolean inboundLoggingEnabled) {
    var scimSecurity =
        http.securityMatcher("/api/scim/**", "/scim/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(
                new ScimStaticTokenFilter(scimToken), UsernamePasswordAuthenticationFilter.class);
    if (inboundLoggingEnabled) {
      scimSecurity.addFilterAfter(new ScimInboundLoggingFilter(), ScimStaticTokenFilter.class);
    }
    return scimSecurity.build();
  }
}
