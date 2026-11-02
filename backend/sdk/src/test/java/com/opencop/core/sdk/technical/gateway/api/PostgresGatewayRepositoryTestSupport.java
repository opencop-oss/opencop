// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class PostgresGatewayRepositoryTestSupport implements AutoCloseable {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private final EmbeddedPostgres postgres;
  private final NamedParameterJdbcTemplate jdbc;

  private PostgresGatewayRepositoryTestSupport(EmbeddedPostgres postgres) {
    this.postgres = postgres;
    jdbc = new NamedParameterJdbcTemplate(postgres.getPostgresDatabase());
  }

  static PostgresGatewayRepositoryTestSupport start() throws Exception {
    var postgres = EmbeddedPostgres.builder().start();
    migrateAll(postgres.getPostgresDatabase());
    return new PostgresGatewayRepositoryTestSupport(postgres);
  }

  NamedParameterJdbcTemplate jdbc() {
    return jdbc;
  }

  void seedMapper(UUID mapperId) {
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (:id, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", mapperId));
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (:id, 1, :name, '{}'::jsonb)
        """,
        Map.of("id", mapperId, "name", "mapper-" + mapperId));
  }

  void seedSocketSource(UUID sourceId, UUID mapperId) {
    NativeIntegrationFixture.seed(jdbc.getJdbcOperations());
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES(
          :id, :name, 'default', 'TCP'::profiles.transport_kind, 'R'::profiles.access_mode,
          :mapperId, '{}'::jsonb, :integrationId, :schemaVersion
        )
        """,
        Map.of(
            "id",
            sourceId,
            "name",
            "source-" + sourceId,
            "mapperId",
            mapperId,
            "integrationId",
            NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
            "schemaVersion",
            NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
  }

  private static void migrateAll(DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }

  @Override
  public void close() throws Exception {
    postgres.close();
  }
}
