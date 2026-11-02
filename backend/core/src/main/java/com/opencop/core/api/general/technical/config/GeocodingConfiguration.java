// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.geocoding.ListGeocodersUsecase;
import com.opencop.core.api.general.business.usecase.geocoding.SearchGeocoderUsecase;
import com.opencop.core.api.general.ports.gateway.geocoding.GeocodingRateLimiter;
import com.opencop.core.api.general.ports.gateway.integrationcatalog.IntegrationCatalogGateway;
import com.opencop.core.api.general.ports.usecase.geocoding.ListGeocoders;
import com.opencop.core.api.general.ports.usecase.geocoding.SearchGeocoder;
import com.opencop.core.api.general.technical.gateway.geocoding.InMemoryGeocodingRateLimiter;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GeocodingProperties.class)
public class GeocodingConfiguration {

  @Bean
  ListGeocoders listGeocoders(GeocoderRegistry registry, IntegrationCatalogGateway catalogGateway) {
    return new ListGeocodersUsecase(registry, catalogGateway);
  }

  @Bean
  GeocodingRateLimiter geocodingRateLimiter(GeocodingProperties properties, Clock clock) {
    var rateLimit = properties.getRateLimit();
    return new InMemoryGeocodingRateLimiter(
        rateLimit.getRequestsPerSecond(), rateLimit.getBurst(), clock);
  }

  @Bean
  SearchGeocoder searchGeocoder(
      GeocoderRegistry registry,
      IntegrationCatalogGateway catalogGateway,
      GeocodingRateLimiter rateLimiter,
      GeocodingProperties properties) {
    return new SearchGeocoderUsecase(
        registry, catalogGateway, rateLimiter, properties.toSettings());
  }
}
