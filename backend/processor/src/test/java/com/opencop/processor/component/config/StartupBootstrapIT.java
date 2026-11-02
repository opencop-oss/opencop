// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.component.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileRepository;
import com.opencop.processor.component.support.ProcessorComponentTestDatabaseConfig;
import com.opencop.processor.technical.spring.config.BundledProfileInstaller;
import com.opencop.processor.technical.spring.config.StartupBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = StartupBootstrapIT.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "redis.host=localhost",
      "redis.port=6379",
      "management.health.redis.enabled=false"
    })
@ActiveProfiles("it")
class StartupBootstrapIT extends AbstractDatabaseIT {

  // Shipped by the SDK; Processor resolves it from the classpath like any bundled mapper.
  private static final String KML_MAPPER_RESOURCE = "classpath:/profiles/mappers/kml+kmz.v1.json";

  @Autowired private MapperProfileService mappers;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ResourcePatternResolver resolver;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void clearMapperCatalog() {
    jdbc.update("DELETE FROM profiles.data_source");
    jdbc.update("DELETE FROM profiles.data_mapper");
    jdbc.update("DELETE FROM profiles.mapper");
  }

  @Test
  void startupInstallsTheBundledMappersOnceAcrossRestarts() throws Exception {
    var bundled =
        objectMapper.readValue(
            resolver.getResource(KML_MAPPER_RESOURCE).getInputStream(), MapperProfile.class);

    startAndStop();
    startAndStop();

    assertThat(mappers.findLatest(bundled.id()))
        .get()
        .satisfies(
            mapper -> {
              assertThat(mapper.name()).isEqualTo(bundled.name());
              assertThat(mapper.version()).isEqualTo(bundled.version());
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM profiles.data_mapper WHERE id = ?",
                Integer.class,
                bundled.id()))
        .isOne();
  }

  private void startAndStop() {
    var bootstrap =
        new StartupBootstrap(new BundledProfileInstaller(mappers, resolver, objectMapper));
    bootstrap.start();
    bootstrap.stop();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({ProcessorComponentTestDatabaseConfig.class, MapperProfileRepository.class})
  static class TestApplication {}
}
