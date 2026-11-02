// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.security.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.data.redis.RedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
// SpringBoot will auto-configure RedisConnectionFactory from spring.data.redis.*
@EnableRedisHttpSession(redisNamespace = "${spring.session.redis.namespace:opencop:bff}")
@EnableConfigurationProperties({SessionCookieProperties.class, AuthSessionProperties.class})
public class RedisSessionConfig {

  @Bean
  public CookieSerializer cookieSerializer(
      SessionCookieProperties props, AuthSessionProperties sessionProperties) {
    DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();

    if (props.getName() != null) {
      cookieSerializer.setCookieName(props.getName());
    }
    if (props.getPath() != null) {
      cookieSerializer.setCookiePath(props.getPath());
    }

    if (props.getDomain() != null && !props.getDomain().isBlank()) {
      cookieSerializer.setDomainName(props.getDomain());
    }

    if (props.getSameSite() != null) {
      cookieSerializer.setSameSite(props.getSameSite());
    }
    if (props.getSecure() != null) {
      cookieSerializer.setUseSecureCookie(props.getSecure());
    }
    if (props.getHttpOnly() != null) {
      cookieSerializer.setUseHttpOnlyCookie(props.getHttpOnly());
    }
    cookieSerializer.setCookieMaxAge((int) sessionProperties.getAbsoluteLifetime().getSeconds());

    return cookieSerializer;
  }

  @Bean
  public SessionRepositoryCustomizer<RedisSessionRepository> sessionRepositoryCustomizer(
      AuthSessionProperties properties) {
    return repository -> repository.setDefaultMaxInactiveInterval(properties.getIdleTimeout());
  }
}
