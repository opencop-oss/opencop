// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.profile.MapperProfile;
import com.opencop.core.sdk.business.model.profile.MapperQualifier;
import com.opencop.core.sdk.ports.gateway.mapper.MapperProfileService;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class MapperProfileRepositoryTest {

  private static final String ENABLED_MAPPER = "{\"enabled\": true, \"rules\": []}";

  private static EmbeddedPostgres postgres;
  private static NamedParameterJdbcTemplate jdbc;
  private static MapperProfileRepository repository;

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
    jdbc = new NamedParameterJdbcTemplate(postgres.getPostgresDatabase());
    repository = new MapperProfileRepository(jdbc, new ObjectMapper());
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    postgres.close();
  }

  @Test
  void sourceDecodesWithTheLatestRevisionOfItsOwnMapper() {
    var connectionMapper = seedMapper(2);
    var sourceId = seedSource(connectionMapper);

    var mapper = repository.findLatestForSource(sourceId).orElseThrow();

    assertThat(mapper.id()).isEqualTo(connectionMapper);
    assertThat(mapper.version()).isEqualTo(2);
  }

  @Test
  void findsTheLatestRevisionOfAMapperByItsIdentifier() {
    var mapperId = seedMapper(2);

    var mapper = repository.findLatest(mapperId).orElseThrow();

    assertThat(mapper.id()).isEqualTo(mapperId);
    assertThat(mapper.version()).isEqualTo(2);
  }

  @Test
  void findsNothingForAnUnknownMapperOrSource() {
    assertThat(repository.findLatest(UUID.randomUUID())).isEmpty();
    assertThat(repository.findLatestForSource(UUID.randomUUID())).isEmpty();
  }

  @Test
  void aWriteOnlySourceHasNoIngressMapperToDecodeWith() {
    var egressMapper = seedMapper(MapperQualifier.EGRESS, 1);
    var sourceId = seedSource("W", null, egressMapper);

    assertThat(repository.findLatestForSource(sourceId)).isEmpty();
  }

  @Test
  void onlyAKnownWriteOnlySourceOnlyWrites() {
    var ingressMapper = seedMapper(1);
    var egressMapper = seedMapper(MapperQualifier.EGRESS, 1);

    assertThat(repository.onlyWrites(seedSource("W", null, egressMapper))).isTrue();
    assertThat(repository.onlyWrites(seedSource("RW", ingressMapper, egressMapper))).isFalse();
    assertThat(repository.onlyWrites(seedSource(ingressMapper))).isFalse();
    assertThat(repository.onlyWrites(UUID.randomUUID())).isFalse();
  }

  @Test
  void aReadWriteSourceDecodesWithItsIngressMapperNotItsEgressMapper() {
    var ingressMapper = seedMapper(2);
    var egressMapper = seedMapper(MapperQualifier.EGRESS, 1);
    var sourceId = seedSource("RW", ingressMapper, egressMapper);

    var mapper = repository.findLatestForSource(sourceId).orElseThrow();

    assertThat(mapper.id()).isEqualTo(ingressMapper);
    assertThat(mapper.qualifier()).isEqualTo(MapperQualifier.INGRESS);
  }

  @Test
  void aMapperReadsBackWithItsQualifier() {
    var egressMapper = seedMapper(MapperQualifier.EGRESS, 1);

    assertThat(repository.findLatest(egressMapper).orElseThrow().qualifier())
        .isEqualTo(MapperQualifier.EGRESS);
  }

  @Test
  void createsAMapperAtTheSuppliedVersion() {
    var mapperId = UUID.randomUUID();

    repository.createMapperProfile(
        MapperProfile.builder()
            .withId(mapperId)
            .withName("mapper-" + mapperId)
            .withEditable(false)
            .withVersion(37)
            .withArchived(false)
            .build(),
        null,
        null,
        null);

    assertThat(repository.findLatest(mapperId).orElseThrow().version()).isEqualTo(37);
  }

  @Test
  void theLatestMapperProfileMetaIsTheLatestRevisionOrNullForAnUnknownMapper() {
    var mapperId = seedMapper(3);

    assertThat(repository.getLatestMapperProfileMeta(mapperId).version()).isEqualTo(3);
    assertThat(repository.getLatestMapperProfileMeta(UUID.randomUUID())).isNull();
  }

  @Test
  void bothLatestReadsOfAMapperShareOneCachedEntry() {
    new ApplicationContextRunner()
        .withUserConfiguration(MapperCaching.class)
        .withBean(
            MapperProfileRepository.class,
            () -> new MapperProfileRepository(jdbc, new ObjectMapper()))
        .run(
            context -> {
              var mappers = context.getBean(MapperProfileService.class);
              var readByFindLatest = seedMapper(1);
              var readByMeta = seedMapper(1);

              mappers.findLatest(readByFindLatest);
              mappers.getLatestMapperProfileMeta(readByMeta);
              addRevision(readByFindLatest, 2);
              addRevision(readByMeta, 2);

              assertThat(mappers.getLatestMapperProfileMeta(readByFindLatest).version())
                  .isEqualTo(1);
              assertThat(mappers.findLatest(readByMeta).orElseThrow().version()).isEqualTo(1);
            });
  }

  /**
   * Caching as the services that read mappers configure it. Deliberately not a
   * {@code @Configuration}: this package ships in the test-jar, and other modules' contexts
   * component-scan it, so a scanned configuration would add a second cache manager to theirs.
   */
  @EnableCaching
  static class MapperCaching {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(
          MapperProfileCaches.BY_MAPPER_ID,
          MapperProfileCaches.BY_SOURCE_ID,
          MapperProfileCaches.SOURCE_ONLY_WRITES);
    }
  }

  private static void addRevision(UUID mapperId, int version) {
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (:id, :version, :name, CAST(:mapper AS jsonb))
        """,
        Map.of(
            "id",
            mapperId,
            "version",
            version,
            "name",
            "mapper-" + mapperId,
            "mapper",
            ENABLED_MAPPER));
  }

  private static UUID seedMapper(int revisions) {
    return seedMapper(MapperQualifier.INGRESS, revisions);
  }

  private static UUID seedMapper(MapperQualifier qualifier, int revisions) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (:id, CAST(:qualifier AS profiles.mapper_qualifier))
        """,
        Map.of("id", mapperId, "qualifier", qualifier.name()));
    for (var version = 1; version <= revisions; version++) {
      addRevision(mapperId, version);
    }
    return mapperId;
  }

  private static UUID seedSource(UUID mapperId) {
    return seedSource("R", mapperId, null);
  }

  private static UUID seedSource(String access, UUID ingressMapperId, UUID egressMapperId) {
    var sourceId = UUID.randomUUID();
    NativeIntegrationFixture.seed(jdbc.getJdbcOperations());
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, provider, access, ingress_mapper_id, egress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES (:id, :name, 'TCP', CAST(:access AS profiles.access_mode), :ingressMapperId,
                :egressMapperId, '{}'::jsonb, :integrationId, :schemaVersion)
        """,
        new MapSqlParameterSource()
            .addValue("id", sourceId)
            .addValue("name", "source-" + sourceId)
            .addValue("access", access)
            .addValue("ingressMapperId", ingressMapperId)
            .addValue("egressMapperId", egressMapperId)
            .addValue("integrationId", NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId())
            .addValue("schemaVersion", NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
    return sourceId;
  }
}
