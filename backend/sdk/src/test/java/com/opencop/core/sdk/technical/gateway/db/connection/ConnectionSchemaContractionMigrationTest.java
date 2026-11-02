// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The latest schema makes every connection belong to an integration and hold exactly the mappers
 * its access needs, records which integration contributes a mapper only on the mapper's identity,
 * and keeps no data profiles or delimiters. Migrating to it rewrites no connection or mapper Core
 * has already started on.
 */
class ConnectionSchemaContractionMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();
  private static final String STARTED_VERSION = "61";
  private static final String ACCESS_RULE = "ck_data_source_access_mappers";
  // A database Core has already started on carries the access rule under this predicate.
  private static final String ACCESS_RULE_PREDICATE =
      """
      (access = 'R' AND ingress_mapper_id IS NOT NULL AND egress_mapper_id IS NULL)
      OR (access = 'W' AND ingress_mapper_id IS NULL AND egress_mapper_id IS NOT NULL)
      OR (access = 'RW' AND ingress_mapper_id IS NOT NULL AND egress_mapper_id IS NOT NULL)
      """;
  private static final String MEDIA_ICON_CACHE_REPAIR = "geo.media_icon_cache_repair";
  private static final int SCHEMA_VERSION = 1;

  @Test
  void aDatabaseCoreHasStartedOnKeepsEveryConnectionAndMapperAsItWas() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrate(dataSource, STARTED_VERSION);
      var jdbc = new JdbcTemplate(dataSource);
      var integration = seedIntegration(jdbc);
      NativeIntegrationFixture.seed(jdbc);
      var nativeSchema = NativeIntegrationFixture.CONNECTION_SCHEMA;
      var ingressMapper = seedMapper(jdbc, "INGRESS");
      var rtspMapper = seedMapper(jdbc, "INGRESS");
      var egressMapper = seedMapper(jdbc, "EGRESS");
      var contributedMapper = seedContributedMapper(jdbc, integration);
      var profile = seedProfile(jdbc, contributedMapper);
      // A connection created from a data profile frames by the framing its integration declares
      // under the profile's delimiter id.
      var integrationFramed =
          seedSource(
              jdbc,
              "TCP",
              "R",
              contributedMapper,
              null,
              integration,
              tcpSpec(
                  """
                  {"kind": "integration", "framingId": "%s"}
                  """
                      .formatted(profile.delimiterId())));
      var readWrite = seedSource(jdbc, "TCP", "RW", ingressMapper, egressMapper, integration, "{}");
      for (var legacy : List.of(integrationFramed, readWrite)) {
        jdbc.update(
            "UPDATE profiles.data_source SET data_profile_id = ? WHERE id = ?",
            profile.id(),
            legacy);
      }
      seedSource(
          jdbc,
          "TCP",
          "R",
          ingressMapper,
          null,
          nativeSchema,
          tcpSpec(
              """
              {"kind": "custom", "mode": "delimiterSplit", "delimiter": "\\n"}
              """));
      seedSource(jdbc, "RTSP", "R", rtspMapper, null, nativeSchema, "{}");
      seedSource(jdbc, "TCP", "W", null, egressMapper, integration, "{}");
      jdbc.execute(
          "ALTER TABLE profiles.data_source ADD CONSTRAINT "
              + ACCESS_RULE
              + " CHECK ("
              + ACCESS_RULE_PREDICATE
              + ")");
      jdbc.execute("DROP TABLE " + MEDIA_ICON_CACHE_REPAIR);
      var connectionsBefore = connections(jdbc);
      var mappersBefore = mappers(jdbc);

      migrate(dataSource, null);

      assertThat(connections(jdbc)).hasSize(5).isEqualTo(connectionsBefore);
      assertThat(mappers(jdbc)).hasSize(4).isEqualTo(mappersBefore);
      assertThat(mappersBefore.get(contributedMapper))
          .containsEntry("integration_id", integration.integrationId());
      assertContractedSchema(jdbc);
    }
  }

  @Test
  void aNewDatabaseGetsTheContractedSchema() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();

      migrate(dataSource, null);

      var jdbc = new JdbcTemplate(dataSource);
      assertContractedSchema(jdbc);
      NativeIntegrationFixture.seed(jdbc);
      var nativeSchema = NativeIntegrationFixture.CONNECTION_SCHEMA;
      var ingressMapper = seedMapper(jdbc, "INGRESS");
      var egressMapper = seedMapper(jdbc, "EGRESS");
      assertThatThrownBy(
              () -> seedSource(jdbc, "TCP", "RW", ingressMapper, null, nativeSchema, "{}"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(ACCESS_RULE);
      assertThatThrownBy(
              () -> seedSource(jdbc, "TCP", "R", ingressMapper, egressMapper, nativeSchema, "{}"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining(ACCESS_RULE);
      assertThatThrownBy(() -> seedSource(jdbc, "TCP", "R", ingressMapper, null, null, "{}"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("integration_id");
    }
  }

  private static void assertContractedSchema(JdbcTemplate jdbc) {
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM pg_constraint
                 WHERE conrelid = 'profiles.data_source'::regclass AND conname = ?
                """,
                Integer.class,
                ACCESS_RULE))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                """
                SELECT is_nullable FROM information_schema.columns
                 WHERE table_schema = 'profiles'
                   AND table_name = 'data_source'
                   AND column_name = 'integration_id'
                """,
                String.class))
        .isEqualTo("NO");
  }

  /** What a connection is, keyed by its id: everything the contraction must leave untouched. */
  private static Map<UUID, Map<String, Object>> connections(JdbcTemplate jdbc) {
    var connections = new TreeMap<UUID, Map<String, Object>>();
    jdbc.queryForList(
            """
            SELECT id, provider::text AS provider, access::text AS access, ingress_mapper_id,
                   egress_mapper_id, connection_spec::text AS connection_spec, integration_id,
                   connection_schema_version, updated_at
              FROM profiles.data_source
            """)
        .forEach(row -> connections.put((UUID) row.get("id"), row));
    return connections;
  }

  /** Who contributes each mapper, keyed by its id: the ownership the contraction must keep. */
  private static Map<UUID, Map<String, Object>> mappers(JdbcTemplate jdbc) {
    var mappers = new TreeMap<UUID, Map<String, Object>>();
    jdbc.queryForList(
            "SELECT id, qualifier::text AS qualifier, integration_id FROM profiles.mapper")
        .forEach(row -> mappers.put((UUID) row.get("id"), row));
    return mappers;
  }

  private static IntegrationConnectionSchemaReference seedIntegration(JdbcTemplate jdbc) {
    var integrationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO config.integration (id, name, description) VALUES (?, ?, ?)",
        integrationId,
        "integration-" + integrationId,
        "Contributes framing and mappers from code.");
    jdbc.update(
        """
        INSERT INTO config.integration_connection_schema (integration_id, schema_version)
        VALUES (?, ?)
        """,
        integrationId,
        SCHEMA_VERSION);
    return new IntegrationConnectionSchemaReference(integrationId, SCHEMA_VERSION);
  }

  private static UUID seedMapper(JdbcTemplate jdbc, String qualifier) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.mapper (id, qualifier) VALUES (?, ?::profiles.mapper_qualifier)",
        mapperId,
        qualifier);
    jdbc.update(
        "INSERT INTO profiles.data_mapper (id, version, name, mapper) VALUES (?, 1, ?, '{}'::jsonb)",
        mapperId,
        "mapper-" + mapperId);
    return mapperId;
  }

  /**
   * A mapper an integration contributes, as the catalog stored it while each revision also carried
   * a contribution flag.
   */
  private static UUID seedContributedMapper(
      JdbcTemplate jdbc, IntegrationConnectionSchemaReference integration) {
    var mapperId = seedMapper(jdbc, "INGRESS");
    jdbc.update(
        "UPDATE profiles.mapper SET integration_id = ? WHERE id = ?",
        integration.integrationId(),
        mapperId);
    jdbc.update("UPDATE profiles.data_mapper SET plugin_managed = true WHERE id = ?", mapperId);
    return mapperId;
  }

  private record SeededProfile(UUID id, UUID delimiterId) {}

  private static SeededProfile seedProfile(JdbcTemplate jdbc, UUID mapperId) {
    var delimiterId = UUID.randomUUID();
    var profileId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO profiles.data_delimiter (id, name) VALUES (?, ?)",
        delimiterId,
        "delimiter-" + delimiterId);
    jdbc.update(
        """
        INSERT INTO profiles.data_profile (id, name, delimiter_id, mapper_id, mapper_version)
        VALUES (?, ?, ?, ?, 1)
        """,
        profileId,
        "profile-" + profileId,
        delimiterId,
        mapperId);
    return new SeededProfile(profileId, delimiterId);
  }

  private static UUID seedSource(
      JdbcTemplate jdbc,
      String provider,
      String access,
      UUID ingressMapperId,
      UUID egressMapperId,
      IntegrationConnectionSchemaReference schema,
      String connectionSpec) {
    var sourceId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.data_source
          (id, name, provider, access, ingress_mapper_id, egress_mapper_id, integration_id,
           connection_schema_version, connection_spec)
        VALUES (?, ?, ?::profiles.transport_kind, ?::profiles.access_mode, ?, ?, ?, ?, ?::jsonb)
        """,
        sourceId,
        "source-" + sourceId,
        provider,
        access,
        ingressMapperId,
        egressMapperId,
        schema == null ? null : schema.integrationId(),
        schema == null ? null : schema.schemaVersion(),
        connectionSpec);
    return sourceId;
  }

  private static String tcpSpec(String framing) {
    return """
        {"transport": {"type": "tcp", "host": "feed.example.test", "port": 8089, "framing": %s}}
        """
        .formatted(framing.strip());
  }

  private static void migrate(DataSource dataSource, String target) {
    var configuration =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .placeholders(FLYWAY_PLACEHOLDERS);
    if (target != null) {
      configuration.target(target);
    }
    configuration.load().migrate();
  }
}
