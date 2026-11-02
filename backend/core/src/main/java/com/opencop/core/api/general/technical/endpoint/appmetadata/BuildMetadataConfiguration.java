// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.appmetadata;

import jakarta.annotation.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BuildMetadataProperties.class)
public class BuildMetadataConfiguration {

  @Bean
  BuildMetadata buildMetadata(BuildMetadataProperties properties) {
    return new BuildMetadata(
        normalize(properties.appVersion()),
        normalize(properties.revision()),
        normalize(properties.branch()),
        normalize(properties.date()));
  }

  private static @Nullable String normalize(@Nullable String value) {
    if (value == null || value.isBlank() || "unknown".equals(value)) {
      return null;
    }
    return value;
  }
}
