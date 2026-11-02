// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostgresSourceRestartEpochRepositoryTest {

  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441302");
  private static final UUID OTHER_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655441303");
  private static final UUID MAPPER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655441304");

  private static PostgresGatewayRepositoryTestSupport database;
  private static NamedParameterJdbcTemplate jdbc;

  private PostgresSourceRestartEpochRepository repository;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = PostgresGatewayRepositoryTestSupport.start();
    jdbc = database.jdbc();
  }

  @AfterAll
  static void stopDatabase() throws Exception {
    if (database != null) {
      database.close();
    }
  }

  @BeforeEach
  void setUp() {
    repository = new PostgresSourceRestartEpochRepository(jdbc);
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.socket_source_restart");
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.data_source");
    jdbc.getJdbcTemplate().execute("DELETE FROM profiles.data_mapper");
    database.seedMapper(MAPPER_ID);
    database.seedSocketSource(SOURCE_ID, MAPPER_ID);
    database.seedSocketSource(OTHER_SOURCE_ID, MAPPER_ID);
  }

  @Test
  void incrementRestartEpoch_upsertsAndReturnsAdvancedEpoch() {
    assertEquals(1, repository.incrementRestartEpoch(SOURCE_ID));
    assertEquals(2, repository.incrementRestartEpoch(SOURCE_ID));

    var epochs = repository.findRestartEpochs(Set.of(SOURCE_ID, OTHER_SOURCE_ID));

    assertEquals(2, epochs.get(SOURCE_ID));
    assertTrue(!epochs.containsKey(OTHER_SOURCE_ID));
  }

  @Test
  void findRestartEpochs_whenSourceIdsEmptyReturnsEmptyMap() {
    assertTrue(repository.findRestartEpochs(Set.of()).isEmpty());
  }
}
