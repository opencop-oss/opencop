// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.nominatim.technical.config;

import com.opencop.core.sdk.integration.nominatim.business.model.NominatimIntegration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Registers the Nominatim integration.
 *
 * <p>Unconditional, unlike its geocoder, so the integration's catalog row exists whether or not a
 * deployment enables the geocoder.
 */
@Lazy
@Configuration(proxyBeanMethods = false)
public class NominatimIntegrationIdentityConfiguration {

  @Bean
  NominatimIntegration nominatimIntegration() {
    return new NominatimIntegration();
  }
}
