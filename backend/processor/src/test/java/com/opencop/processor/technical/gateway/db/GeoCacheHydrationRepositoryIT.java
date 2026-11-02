// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.geojson.Feature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Which revision of each object a source's cache hydration reads: the latest one, and only when
 * that latest revision is not stale.
 */
class GeoCacheHydrationRepositoryIT {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655448000");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655448001");
  private static final Instant NOW = Instant.now();
  private static final Instant FAR_STALE = NOW.plus(Duration.ofDays(365));

  private static EmbeddedPostgres postgres;
  private static DataSource dataSource;
  private static JdbcTemplate jdbc;

  private GeoCacheHydrationRepository repository;

  @BeforeAll
  static void startDatabase() throws Exception {
    postgres = EmbeddedPostgres.builder().start();
    dataSource = postgres.getPostgresDatabase();
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations(
            "classpath:db/migration",
            "filesystem:"
                + Path.of("..", "sdk", "src", "main", "resources", "db", "migration")
                    .toAbsolutePath())
        .placeholders(FlywayGeoFeaturesPlaceholders.defaultPlaceholders())
        .load()
        .migrate();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    if (postgres != null) {
      postgres.close();
    }
  }

  @BeforeEach
  void setUp() {
    jdbc.execute("DELETE FROM geo.features");
    repository =
        new GeoCacheHydrationRepository(
            new NamedParameterJdbcTemplate(jdbc),
            new ObjectMapper(),
            new DataSourceTransactionManager(dataSource));
  }

  @Test
  void aDeletedObjectIsNotHydratedFromItsOlderRevision() {
    var start = NOW.minus(Duration.ofDays(2));
    seed("deleted", "import", start, start, FAR_STALE);
    seedDelete("deleted", start, NOW.minus(Duration.ofDays(1)));
    seed("live", "live", start, start, FAR_STALE);

    assertThat(hydrate(1000)).containsExactly("live/live");
  }

  @Test
  void anObjectRecreatedAfterADeleteHydratesItsLatestRevision() {
    var start = NOW.minus(Duration.ofDays(3));
    seed("recreated", "import", start, start, FAR_STALE);
    seedDelete("recreated", start, NOW.minus(Duration.ofDays(2)));
    var recreatedAt = NOW.minus(Duration.ofDays(1));
    seed("recreated", "recreated", recreatedAt, recreatedAt, FAR_STALE);

    assertThat(hydrate(1000)).containsExactly("recreated/recreated");
  }

  @Test
  void anUpdateThatHasExpiredHidesAnOlderRevisionWithALongerStaleTime() {
    var importedAt = NOW.minus(Duration.ofDays(2));
    seed("updated", "import", importedAt, importedAt, FAR_STALE);
    var updatedAt = NOW.minus(Duration.ofDays(1));
    seed("updated", "short-update", updatedAt, updatedAt, NOW.minus(Duration.ofHours(1)));

    assertThat(hydrate(1000)).isEmpty();
  }

  @Test
  void anActiveLatestRevisionIsHydratedAndOtherSourcesAreIgnored() {
    var importedAt = NOW.minus(Duration.ofDays(2));
    seed("moved", "import", importedAt, importedAt, FAR_STALE);
    var movedAt = NOW.minus(Duration.ofDays(1));
    seed("moved", "moved", movedAt, movedAt, FAR_STALE);
    seed("never-stale", "never-stale", importedAt, importedAt, null);
    seedRow(
        UUID.randomUUID(), OTHER_SOURCE_ID, "other", "other", importedAt, importedAt, FAR_STALE);

    assertThat(hydrate(1000)).containsExactly("moved/moved", "never-stale/never-stale");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 1000})
  void everyPageSizeHydratesTheSameObjectsWhenDeletedObjectsSitBetweenLiveOnes(int pageSize) {
    var start = NOW.minus(Duration.ofDays(2));
    for (String objectId : List.of("a", "b", "c", "d", "e")) {
      seed(objectId, "import", start, start, FAR_STALE);
    }
    seedDelete("b", start, NOW.minus(Duration.ofDays(1)));
    seedDelete("d", start, NOW.minus(Duration.ofDays(1)));

    assertThat(hydrate(pageSize)).containsExactly("a/import", "c/import", "e/import");
  }

  /** Hydrates the source and returns {@code objectId/label} for each feature, in page order. */
  private List<String> hydrate(int pageSize) {
    var hydrated = new ArrayList<String>();
    repository.hydrateSnapshot(
        SOURCE_ID,
        pageSize,
        page -> {
          for (Feature feature : page) {
            var opencop = OpenCopPropertiesParser.readFromFeature(feature);
            hydrated.add(opencop.getObjectId() + "/" + feature.getProperty("label"));
          }
        });
    return hydrated;
  }

  private static void seed(
      String objectId, String label, Instant startAt, Instant createdAt, Instant staleAt) {
    seedRow(UUID.randomUUID(), SOURCE_ID, objectId, label, startAt, createdAt, staleAt);
  }

  /** A delete as the broadcast path writes it: a new revision, same start, stale at its time. */
  private static void seedDelete(String objectId, Instant startAt, Instant deletedAt) {
    seedRow(UUID.randomUUID(), SOURCE_ID, objectId, "delete", startAt, deletedAt, deletedAt);
  }

  private static void seedRow(
      UUID eventId,
      UUID sourceId,
      String objectId,
      String label,
      Instant startAt,
      Instant createdAt,
      Instant staleAt) {
    jdbc.update(
        """
        INSERT INTO geo.features(
          id, feature_kind, source, object_id, created_at, start_at, stale_at, geom, payload
        )
        VALUES (?, 'event', ?, ?, ?, ?, ?, ?, ?::jsonb)
        """,
        eventId,
        sourceId,
        objectId,
        Timestamp.from(createdAt),
        Timestamp.from(startAt),
        staleAt == null ? null : Timestamp.from(staleAt),
        "{\"type\":\"Point\",\"coordinates\":[-73.5,45.5]}",
        """
        {"type":"Feature","geometry":{"type":"Point","coordinates":[-73.5,45.5]},\
        "properties":{"label":"%s","opencop":{"id":"%s","objectId":"%s","source":"%s"}}}
        """
            .formatted(label, eventId, objectId, sourceId));
  }
}
