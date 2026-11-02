// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.spring.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opencop.core.sdk.ports.gateway.media.EventAttachmentRepository;
import com.opencop.core.sdk.ports.gateway.media.MediaUrlRepository;
import com.opencop.processor.business.usecase.event.EventAutoAttachmentService;
import com.opencop.processor.ports.gateway.db.DataSourceTransportGateway;
import com.opencop.processor.technical.gateway.db.DataSourceTransportReader;
import com.opencop.processor.technical.gateway.db.PostGisFeatureRecordMapper;
import com.opencop.processor.technical.gateway.db.PostGisService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableTransactionManagement
public class PostGisConfig {

  @Bean
  DataSourceTransportGateway dataSourceTransportGateway(
      NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new DataSourceTransportReader(jdbc, objectMapper);
  }

  @Bean
  EventAutoAttachmentService eventAutoAttachmentService(
      DataSourceTransportGateway dataSourceTransportGateway,
      MediaUrlRepository mediaUrlRepository,
      EventAttachmentRepository eventAttachmentRepository,
      @Value("${opencop.processor.attachment-cache.max-size:100000}") long maxSize) {
    Cache<String, Optional<String>> sourceUrlCache =
        Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(1, TimeUnit.MINUTES).build();
    Cache<String, Boolean> attachedCache =
        Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(1, TimeUnit.MINUTES).build();
    return new EventAutoAttachmentService(
        dataSourceTransportGateway,
        mediaUrlRepository,
        eventAttachmentRepository,
        sourceUrlCache,
        attachedCache);
  }

  @Bean
  PostGisFeatureRecordMapper postGisFeatureRecordMapper(ObjectMapper objectMapper) {
    return new PostGisFeatureRecordMapper(objectMapper);
  }

  @Bean
  PostGisService postGisService(
      final DataSource dataSource, final PostGisFeatureRecordMapper postGisFeatureRecordMapper) {
    return new PostGisService(dataSource, postGisFeatureRecordMapper);
  }

  @Bean
  DataSource create(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password) {

    HikariConfig cfg = getHikariConfig(url, username, password);

    // Postgres driver optimizations
    cfg.addDataSourceProperty("reWriteBatchedInserts", "true"); // faster batch INSERT
    cfg.addDataSourceProperty("tcpKeepAlive", "true");
    cfg.addDataSourceProperty("sslmode", "disable");

    return new HikariDataSource(cfg);
  }

  private static HikariConfig getHikariConfig(String url, String username, String password) {
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(url);
    cfg.setUsername(username);
    cfg.setPassword(password);

    int maxPool = 8;
    int minIdle = 1;
    cfg.setMaximumPoolSize(maxPool);
    cfg.setMinimumIdle(minIdle);

    cfg.setConnectionTimeout(10_000); // ms
    cfg.setValidationTimeout(3_000);
    cfg.setInitializationFailTimeout(-1);

    // Keep-alive/health
    cfg.setIdleTimeout(240_000); // 4m
    cfg.setMaxLifetime(1_800_000); // 30m

    return cfg;
  }
}
