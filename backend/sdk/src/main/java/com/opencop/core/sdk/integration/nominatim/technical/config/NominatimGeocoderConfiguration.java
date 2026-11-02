// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.config;

import com.opencop.core.sdk.integration.nominatim.business.model.NominatimIntegration;
import com.opencop.core.sdk.integration.nominatim.technical.geocoding.NominatimGeocoderCapability;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Nominatim geocoder when a deployment enables it. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = NominatimGeocoderProperties.PREFIX,
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(NominatimGeocoderProperties.class)
public class NominatimGeocoderConfiguration {

  @Bean
  GeocoderCapability nominatimGeocoder(
      NominatimIntegration integration, NominatimGeocoderProperties properties) {
    return new NominatimGeocoderCapability(integration, properties);
  }
}
