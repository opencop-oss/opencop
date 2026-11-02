// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.config;

import com.opencop.core.api.general.business.usecase.media.StreamPlaybackReadiness;
import com.opencop.core.api.general.ports.gateway.media.MediaStreamGateway;
import com.opencop.core.api.general.technical.gateway.mediamtx.MediaMtxGateway;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MediaMtxProperties.class)
public class MediaMtxConfig {

  @Bean
  @ConditionalOnProperty(name = "opencop.mediamtx.enabled", havingValue = "true")
  MediaMtxGateway mediaMtxGateway(MediaMtxProperties props) {
    validateMediaMtxApiConfig(props);
    return new MediaMtxGateway(props);
  }

  @Bean
  StreamPlaybackReadiness streamPlaybackReadiness(MediaMtxProperties props) {
    return () -> props.isEnabled() && hasText(props.getHls().getPublicBaseUrl());
  }

  /** Fallback: when MediaMTX is disabled, all stream-prepare requests return empty (503). */
  @Bean
  @ConditionalOnMissingBean(MediaStreamGateway.class)
  MediaStreamGateway disabledMediaStreamGateway() {
    return (pathName, sourceUrl) -> Optional.empty();
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static void validateMediaMtxApiConfig(MediaMtxProperties props) {
    if (!hasText(props.getApi().getBaseUrl())) {
      throw new IllegalStateException(
          "opencop.mediamtx.api.base-url must be configured when MediaMTX is enabled");
    }
  }
}
