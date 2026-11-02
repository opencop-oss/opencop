// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.profile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.technical.config.PluginMapperRegistrationConfiguration;
import com.opencop.core.api.general.technical.config.StartupLeader;
import com.opencop.core.api.general.technical.gateway.db.profile.JpaMapperCatalogGateway;
import com.opencop.core.api.general.technical.gateway.db.profile.MapperCatalogEntryEntity;
import com.opencop.core.sdk.business.model.integration.Integration;
import com.opencop.core.sdk.business.model.integration.mapping.egress.EgressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IngressMapperContributions;
import com.opencop.core.sdk.business.model.integration.mapping.ingress.IntegrationIngressMappers;
import com.opencop.core.sdk.business.usecase.integration.mapping.ingress.IngressMapperAdmissionUsecase;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.gateway.mapping.ingress.PayloadIngressMapper;
import com.opencop.core.sdk.technical.gateway.profile.MapperProfileRepository;
import com.opencop.core.sdk.technical.mapper.MappingProperties;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema.TestIntegration;
import java.util.List;
import java.util.UUID;
import org.geojson.Feature;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = MapperCatalogEntryEntity.class)
@Import({
  ComponentTestDatabaseConfig.class,
  JpaMapperCatalogGateway.class,
  MapperProfileRepository.class,
  PluginMapperRegistrationConfiguration.class
})
class PluginMapperRegistrationTestApplication {

  static final Integration TEST_INTEGRATION =
      new TestIntegration(
          UUID.fromString("00000000-0000-4000-8000-0000000000aa"),
          "Test integration",
          "Test integration");
  static final UUID MAPPER_ID = UUID.fromString("6b0a707f-32ac-4206-bef6-88ea8cbc6300");
  static final UUID BLANK_NAME_ID = UUID.fromString("7663b070-994b-41cf-b82a-4e36c0d424e6");
  static final UUID ZERO_VERSION_ID = UUID.fromString("859d989d-9464-44c0-94e0-23c506ce8b07");
  static final UUID NEGATIVE_VERSION_ID = UUID.fromString("68f9b0c1-5f35-4bcc-a1bd-907977a769a8");
  static final UUID DUPLICATE_ID = UUID.fromString("379183e9-723d-4784-a1f5-f60cbdce4bc8");
  static final UUID FIRST_DUPLICATE_NAME_ID =
      UUID.fromString("92783217-03bc-4f09-802e-ed8a5b341baa");
  static final UUID SECOND_DUPLICATE_NAME_ID =
      UUID.fromString("bc98d808-692d-4073-ad4f-d8d51d71eb1f");
  static final UUID TAK_MAPPER_ID = UUID.fromString("24e7279b-abff-4b68-a0c3-2225e6262ed6");
  static final UUID GEOJSON_MAPPER_ID = UUID.fromString("4017d49f-2a68-4900-8e50-2dbb3bd435df");
  static final UUID KLV_MAPPER_ID = UUID.fromString("d8ba1624-ccd2-4bca-8d4b-241b38f246c4");

  @Bean
  @DependsOn("componentTestFlywayInitializer")
  InitializingBean resetProfileFixtures(JdbcTemplate jdbc) {
    // Context rebuilds share the database with other suites. Reset before the startup runner,
    // rather than before each test method, so startup registration remains under test.
    return () -> {
      jdbc.update("DELETE FROM profiles.data_source");
      jdbc.update("DELETE FROM profiles.data_mapper");
      jdbc.update("DELETE FROM profiles.mapper");
      // Registration records each mapper's owning integration, which must be registered first.
      jdbc.update(
          """
          INSERT INTO config.integration (id, name, description)
          VALUES (?, 'Test integration', 'Mapper registration test integration')
          ON CONFLICT (id) DO NOTHING
          """,
          TEST_INTEGRATION.id());
    };
  }

  @Bean
  StartupLeader startupLeader() {
    var leader = mock(StartupLeader.class);
    when(leader.isLeader()).thenReturn(true);
    return leader;
  }

  @Bean
  PayloadIngressMapper<Feature> contributedMapper() {
    return new TestDecoder(MAPPER_ID, "Selectable plugin mapper", 7);
  }

  @Bean
  PayloadIngressMapper<Feature> takMapper() {
    return new TestDecoder(TAK_MAPPER_ID, "COT", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> geoJsonMapper() {
    return new TestDecoder(GEOJSON_MAPPER_ID, "GEOJSON-FEATURE", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> klvMapper() {
    return new TestDecoder(KLV_MAPPER_ID, "KLV Metadata", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> missingUuidMapper() {
    return new TestDecoder(null, "Missing UUID", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> blankNameMapper() {
    return new TestDecoder(BLANK_NAME_ID, " ", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> zeroVersionMapper() {
    return new TestDecoder(ZERO_VERSION_ID, "Zero version", 0);
  }

  @Bean
  PayloadIngressMapper<Feature> negativeVersionMapper() {
    return new TestDecoder(NEGATIVE_VERSION_ID, "Negative version", -1);
  }

  @Bean
  PayloadIngressMapper<Feature> firstDuplicateMapper() {
    return new TestDecoder(DUPLICATE_ID, "Duplicate version one", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> secondDuplicateMapper() {
    return new TestDecoder(DUPLICATE_ID, "Duplicate version two", 2);
  }

  @Bean
  PayloadIngressMapper<Feature> firstDuplicateNameMapper() {
    return new TestDecoder(FIRST_DUPLICATE_NAME_ID, "Unowned duplicate name", 1);
  }

  @Bean
  PayloadIngressMapper<Feature> secondDuplicateNameMapper() {
    return new TestDecoder(SECOND_DUPLICATE_NAME_ID, "Unowned duplicate name", 1);
  }

  @Bean
  IngressMapperContributions ingressMapperContributions(
      List<PayloadIngressMapper<? extends Feature>> contributions) {
    return new IngressMapperAdmissionUsecase()
        .admit(List.of(new IntegrationIngressMappers(TEST_INTEGRATION, contributions)))
        .contributions();
  }

  @Bean
  EgressMapperContributions egressMapperContributions() {
    return EgressMapperContributions.of(List.of());
  }

  private record TestDecoder(UUID id, String name, int version)
      implements PayloadIngressMapper<Feature> {

    @Override
    public String description() {
      return "Provider-declared description";
    }

    @Override
    public List<Feature> map(UUID id, MappingProperties properties, byte[] input) {
      return List.of();
    }
  }
}
