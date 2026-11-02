// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.testing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.integration.builtin.business.model.NativeIntegration;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NativeIntegrationFixtureTest {

  private static EmbeddedPostgres postgres;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    Flyway.configure()
        .dataSource(postgres.getPostgresDatabase())
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
        .load()
        .migrate();
    jdbc = new JdbcTemplate(postgres.getPostgresDatabase());
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    postgres.close();
  }

  @Test
  void seed_registersNativeActiveAndItsConnectionSchemaOnce() {
    NativeIntegrationFixture.seed(jdbc);
    jdbc.update(
        "UPDATE config.integration SET active = false WHERE id = ?", NativeIntegration.NATIVE_ID);
    NativeIntegrationFixture.seed(jdbc);

    assertThat(
            jdbc.queryForList(
                "SELECT name FROM config.integration WHERE id = ? AND active",
                String.class,
                NativeIntegration.NATIVE_ID))
        .containsExactly(new NativeIntegration().name());
    assertThat(
            jdbc.queryForList(
                "SELECT schema_version FROM config.integration_connection_schema"
                    + " WHERE integration_id = ?",
                Integer.class,
                NativeIntegration.NATIVE_ID))
        .containsExactly(NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion());
  }
}
