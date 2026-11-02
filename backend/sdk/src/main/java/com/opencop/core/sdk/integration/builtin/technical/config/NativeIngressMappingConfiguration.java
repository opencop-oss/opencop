// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.technical.config;

import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.GeoJsonIngressMapperV1;
import com.opencop.core.sdk.integration.builtin.technical.mapping.ingress.KlvIngressMapperV1;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the Native integration's ingress mappers: GeoJSON and KLV. */
@Configuration(proxyBeanMethods = false)
public class NativeIngressMappingConfiguration {

  @Bean
  IntegrationIngressMappers nativeIngressMappers() {
    return new IntegrationIngressMappers(
        new NativeIntegration(), List.of(new GeoJsonIngressMapperV1(), new KlvIngressMapperV1()));
  }
}
