// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.integrationcatalog;

import com.opencop.core.api.component.support.CoreComponentWebTestConfig;
import com.opencop.core.api.general.technical.config.IntegrationConnectionSettingsConfiguration;
import com.opencop.core.api.general.technical.config.IntegrationConnectionSetupConfiguration;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSettingsEndpoint;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupEndpoint;
import com.opencop.core.api.general.technical.endpoint.integrationcatalog.IntegrationConnectionSetupExceptionAdvice;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaEntity;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.IntegrationConnectionSchemaJpaRepository;
import com.opencop.core.api.general.technical.gateway.db.integrationcatalog.JpaIntegrationCatalogGateway;
import com.opencop.core.api.support.integration.StubConnectionMappingContribution;
import com.opencop.core.api.support.integration.StubTcpConnectionExtractor;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchema;
import com.opencop.core.sdk.business.model.integration.TransportProtocol;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.ExtractorDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.FileListField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.SecretField;
import com.opencop.core.sdk.business.model.integration.connection.input.descriptor.StandardExtractorField;
import com.opencop.core.sdk.business.model.integration.connection.mapping.EgressMapperRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.FramingRule;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingDeclaration;
import com.opencop.core.sdk.business.model.integration.connection.mapping.MapperFramingPair;
import com.opencop.core.sdk.business.model.integration.connection.settings.ConnectionSettingsDescriptor;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsField.DurationField;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsSection;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.integration.connection.input.TcpConnectionExtractorCapability;
import com.opencop.core.sdk.ports.integration.connection.mapping.ConnectionMappingContribution;
import com.opencop.core.sdk.ports.integration.connection.settings.ConnectionSettingsCapability;
import com.opencop.core.sdk.technical.config.integration.connection.input.ConnectionExtractorConfiguration;
import com.opencop.core.sdk.technical.config.integration.connection.mapping.ConnectionMappingConfiguration;
import com.opencop.core.sdk.technical.config.integration.connection.settings.ConnectionSettingsConfiguration;
import com.opencop.core.sdk.testing.integration.connection.behavior.TestConnectionSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Registers two provider-neutral connection schema versions so the endpoint can be exercised
 * against a TCP one, a non-TCP one, and catalog rows this runtime backs with nothing at all.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableMethodSecurity
@EntityScan(basePackageClasses = IntegrationConnectionSchemaEntity.class)
@EnableJpaRepositories(basePackageClasses = IntegrationConnectionSchemaJpaRepository.class)
@Import({
  ComponentTestDatabaseConfig.class,
  CoreComponentWebTestConfig.class,
  ConnectionMappingConfiguration.class,
  IntegrationConnectionSetupConfiguration.class,
  IntegrationConnectionSetupEndpoint.class,
  ConnectionSettingsConfiguration.class,
  IntegrationConnectionSettingsConfiguration.class,
  IntegrationConnectionSettingsEndpoint.class,
  IntegrationConnectionSetupExceptionAdvice.class,
  JpaIntegrationCatalogGateway.class,
  ConnectionExtractorConfiguration.class
})
class IntegrationConnectionSetupWebTestApplication {

  static final UUID TCP_ID = UUID.fromString("8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20");
  static final int TCP_SCHEMA_VERSION = 2;
  static final UUID NON_TCP_ID = UUID.fromString("9b2e1d5f-3e4c-4b6d-8f70-6c7d8e9f0a31");
  static final int NON_TCP_SCHEMA_VERSION = 1;

  static final UUID MAPPER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  static final UUID EGRESS_MAPPER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  static final UUID FRAMING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  static final UUID OPEN_FRAMING_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

  static final UUID FIRST_EXTRACTOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
  static final UUID SECOND_EXTRACTOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

  private static final IntegrationConnectionSchema TCP_SCHEMA =
      new TestConnectionSchema(
          TCP_ID,
          TCP_SCHEMA_VERSION,
          "Streaming feed",
          "Streaming feed",
          Set.of(TransportProtocol.TCP));
  private static final IntegrationConnectionSchema NON_TCP_SCHEMA =
      new TestConnectionSchema(
          NON_TCP_ID,
          NON_TCP_SCHEMA_VERSION,
          "Datagram feed",
          "Datagram feed",
          Set.of(TransportProtocol.UDP));

  static final ExtractorDescriptor FIRST_EXTRACTOR =
      new ExtractorDescriptor(
          FIRST_EXTRACTOR_ID,
          "Signed bundle",
          List.of(
              StandardExtractorField.HOST.declare(true),
              new SecretField("passphrase", "Passphrase", true),
              new FileField(
                  "bundle",
                  "Signed bundle",
                  true,
                  Set.of(".p12"),
                  Set.of("application/x-pkcs12"))));
  static final ExtractorDescriptor SECOND_EXTRACTOR =
      new ExtractorDescriptor(
          SECOND_EXTRACTOR_ID,
          "Trust chain",
          List.of(new FileListField("anchors", "Trust anchors", false, Set.of(".pem"), Set.of())));

  @Bean
  IntegrationConnectionSchema tcpConnectionSchema() {
    return TCP_SCHEMA;
  }

  @Bean
  IntegrationConnectionSchema nonTcpConnectionSchema() {
    return NON_TCP_SCHEMA;
  }

  @Bean
  TcpConnectionExtractorCapability firstExtractor() {
    return new StubTcpConnectionExtractor(TCP_SCHEMA, FIRST_EXTRACTOR);
  }

  @Bean
  TcpConnectionExtractorCapability secondExtractor() {
    return new StubTcpConnectionExtractor(TCP_SCHEMA, SECOND_EXTRACTOR);
  }

  @Bean
  ConnectionMappingContribution tcpMappingContribution() {
    return new StubConnectionMappingContribution(
        TCP_SCHEMA,
        Map.of(
            TransportProtocol.TCP,
            MapperFramingDeclaration.of(
                    new MapperFramingPair(MAPPER_ID, FRAMING_ID),
                    new FramingRule.AnyMapperWith(OPEN_FRAMING_ID))
                .withEgress(EgressMapperRule.closedTo(Set.of(EGRESS_MAPPER_ID)))));
  }

  /** Only the TCP schema version declares connection settings; the other declares none. */
  @Bean
  ConnectionSettingsCapability<Duration> tcpConnectionSettings() {
    return new IntervalSettings(TCP_SCHEMA);
  }

  @Bean
  ConnectionMappingContribution nonTcpMappingContribution() {
    return new StubConnectionMappingContribution(
        NON_TCP_SCHEMA,
        Map.of(TransportProtocol.UDP, MapperDeclaration.closedTo(Set.of(MAPPER_ID))));
  }

  /** Settings of one duration field, so the descriptor's wire form can be asserted end to end. */
  private record IntervalSettings(IntegrationConnectionSchema schema)
      implements ConnectionSettingsCapability<Duration> {

    @Override
    public Class<Duration> settingsType() {
      return Duration.class;
    }

    @Override
    public ConnectionSettingsDescriptor descriptor() {
      return new ConnectionSettingsDescriptor(
          List.of(
              new SettingsSection(
                  "feed",
                  "Feed",
                  List.of(
                      new DurationField(
                          "interval",
                          "Report every",
                          Duration.ofSeconds(5),
                          Duration.ofMinutes(10),
                          Duration.ofSeconds(30))))));
    }

    @Override
    public Duration effective(SettingsDocument complete) {
      return complete.duration("feed", "interval");
    }
  }
}
