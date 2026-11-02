// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V52 moves a socket connection's security under its transport and refuses, before rewriting any
 * row, a stored spec whose security or auth no connector runs.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConnectionSpecTransportSecurityMigrationIT {

  private static final String BEFORE_TRANSPORT_SECURITY = "51";
  private static final String GUARD_MESSAGE = "store a security or auth no connector runs";

  private EmbeddedPostgres postgres;
  private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeAll
  void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
  }

  @AfterAll
  void stopDatabase() throws Exception {
    postgres.close();
  }

  // Each case gets its own database: the baseline creates schemas a Flyway clean leaves behind.
  @BeforeEach
  void migrateAFreshDatabaseToTheSchemaBeforeTransportSecurity() {
    var name = "case_" + UUID.randomUUID().toString().replace("-", "");
    new JdbcTemplate(postgres.getPostgresDatabase()).execute("CREATE DATABASE " + name);
    dataSource = postgres.getDatabase("postgres", name);
    jdbc = new JdbcTemplate(dataSource);
    flyway().target(BEFORE_TRANSPORT_SECURITY).load().migrate();
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "UDP  | {\"transport\":{\"type\":\"udp\",\"host\":\"h\",\"port\":1},\"security\":{\"type\":\"tls\"},\"auth\":{\"type\":\"none\"}}",
        "RTSP | {\"transport\":{\"type\":\"rtsp\",\"url\":\"rtsp://h/s\"},\"security\":{\"type\":\"mtls\"},\"auth\":{\"type\":\"none\"}}",
        "TCP  | {\"transport\":{\"type\":\"tcp\",\"host\":\"h\",\"port\":1},\"security\":{\"type\":\"dtls\"},\"auth\":{\"type\":\"none\"}}",
        "TCP  | {\"transport\":{\"type\":\"tcp\",\"host\":\"h\",\"port\":1},\"security\":{\"type\":\"none\"},\"auth\":{\"type\":\"userpass\",\"credentialsId\":\"c\"}}",
        "TCP  | {\"transport\":{\"type\":\"tcp\",\"host\":\"h\",\"port\":1}}"
      })
  void refusesASpecNoConnectorRunsAndLeavesItUntouched(String provider, String spec) {
    var id = seedConnection(provider, spec);

    assertThatThrownBy(() -> flyway().load().migrate())
        .rootCause()
        .hasMessageContaining(GUARD_MESSAGE);

    assertThat(
            jdbc.queryForObject(
                "SELECT connection_spec::text FROM profiles.data_source WHERE id = ?",
                String.class,
                id))
        .isEqualTo(
            jdbc.queryForObject("SELECT CAST(? AS jsonb)::text", String.class, spec.strip()));
  }

  private UUID seedConnection(String provider, String spec) {
    var id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, tenant, provider, mapper_id, connection_spec, enabled, access)
        VALUES (?, ?, 'default', CAST(? AS profiles.transport_kind), ?, CAST(? AS jsonb), true,
                'R'::profiles.access_mode)
        """,
        id,
        "unsupported-" + id,
        provider.strip(),
        UUID.randomUUID(),
        spec.strip());
    return id;
  }

  private FluentConfiguration flyway() {
    return Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders());
  }
}
