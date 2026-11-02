// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.config.integration.geocoding;

import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.registry.integration.geocoding.GeocoderRegistry;
import com.opencop.core.sdk.ports.integration.geocoding.GeocoderCapability;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Collects integration-owned geocoder beans into their immutable registry. */
@Configuration(proxyBeanMethods = false)
public class GeocoderRegistryConfiguration {

  @Bean
  GeocoderRegistry geocoderRegistry(
      List<GeocoderCapability> geocoders, List<Integration> integrations) {
    return new GeocoderRegistry(geocoders, integrations);
  }
}
