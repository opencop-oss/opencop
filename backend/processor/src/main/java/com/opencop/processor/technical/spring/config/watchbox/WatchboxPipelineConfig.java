// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config.watchbox;

import com.opencop.processor.business.usecase.watchbox.WatchboxEventHandler;
import com.opencop.processor.business.usecase.watchbox.WatchboxTransitionEngine;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.gateway.db.watchbox.PostgresWatchboxRuntimeConfigRepository;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxAlertOutboxDrainer;
import com.opencop.processor.technical.gateway.db.watchbox.WatchboxRuntimeConfigCache;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(WatchboxPipelineProperties.class)
public class WatchboxPipelineConfig {

  // The uncached Postgres adapter is deliberately not a bean: the only injectable
  // WatchboxRuntimeConfigGateway is the caching decorator, so nothing can bypass it.
  @Bean
  WatchboxRuntimeConfigGateway watchboxRuntimeConfig(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      WatchboxPipelineProperties properties,
      ProcessorMetrics metrics) {
    return new WatchboxRuntimeConfigCache(
        new PostgresWatchboxRuntimeConfigRepository(jdbc, objectMapper, metrics),
        properties.getCacheTtl(),
        metrics);
  }

  @Bean
  WatchboxEventHandler watchboxEventHandler(
      WatchboxRuntimeConfigGateway runtimeConfig,
      WatchboxTransitionEngine engine,
      WatchboxAlertOutboxDrainer alertOutboxDrainer,
      WatchboxStateProperties stateProperties,
      WatchboxPipelineProperties pipelineProperties,
      ObjectMapper objectMapper,
      Clock clock,
      ProcessorMetrics metrics) {
    return new WatchboxEventHandler(
        runtimeConfig,
        engine,
        alertOutboxDrainer,
        stateProperties.getLiveWindow(),
        pipelineProperties.getFutureTolerance(),
        objectMapper,
        clock,
        metrics);
  }
}
