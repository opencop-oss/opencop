// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class V33FlywayMigrationTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  // Asserted against a database no test fixture has touched: shared-database
  // suites reset these rows to the same values before reading them, so only a
  // fresh migration proves the rollout-critical V33 seeds themselves.
  @Test
  void v33Migration_seedsWatchboxNoneAndRtspAllAtRevisionZero() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateAll(dataSource);
      var jdbc = new JdbcTemplate(dataSource);

      assertThat(
              jdbc.queryForList(
                  "SELECT name || ':' || consumer_class || ':' || audience_mode || ':' || revision"
                      + " FROM config.runtime_feature ORDER BY name",
                  String.class))
          .containsExactly("rtsp:FRONTEND_ONLY:ALL:0", "watchbox:BACKEND_ENFORCED:NONE:0");

      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM config.runtime_feature_history", Long.class))
          .isZero();
    }
  }

  private static void migrateAll(javax.sql.DataSource dataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .load()
        .migrate();
  }
}
