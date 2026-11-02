// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class MapTileConfigTypeBackfillMigrationTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  @Test
  void migrate_wrapsLegacyFlatMapTileConfigsAcrossSupportedKinds() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "14");
      var jdbc = new JdbcTemplate(dataSource);

      var legacyRows =
          List.of(
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440101"),
                  true,
                  "Legacy Vector",
                  "Vector basemap before V19",
                  "https://tiles.example.com/vector/style.json",
                  "VECTOR",
                  "{\"kind\":\"VECTOR\",\"tileSize\":512,\"minZoom\":0,\"maxZoom\":14}",
                  "vault/vector"),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440102"),
                  false,
                  "Legacy Raster",
                  "Raster basemap before V19",
                  "https://tiles.example.com/raster/{z}/{x}/{y}.png",
                  "RASTER",
                  "{\"kind\":\"RASTER\",\"tileSize\":256,\"minZoom\":0,\"maxZoom\":18}",
                  null),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440103"),
                  false,
                  "Legacy Raster DEM",
                  "Raster DEM before V19",
                  "https://tiles.example.com/dem/{z}/{x}/{y}.png",
                  "RASTER_DEM",
                  "{\"kind\":\"RASTER_DEM\",\"tileSize\":256,\"minZoom\":0,\"maxZoom\":15,\"encoding\":\"TERRARIUM\"}",
                  null),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440104"),
                  false,
                  "Legacy Terrain Mesh",
                  "Terrain mesh before V19",
                  "https://tiles.example.com/terrain/{z}/{x}/{y}.png",
                  "TERRAIN_MESH",
                  "{\"kind\":\"TERRAIN_MESH\",\"elevationUrl\":\"https://tiles.example.com/elevation/{z}/{x}/{y}.png\",\"encoding\":\"TERRAIN_RGB\",\"meshMaxError\":6,\"boundsPadding\":2}",
                  "vault/terrain"),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440105"),
                  false,
                  "Legacy 3D Tiles",
                  "3D tiles before V19",
                  "https://tiles.example.com/3d/tileset.json",
                  "TILE_3D",
                  "{\"kind\":\"TILE_3D\",\"tilesetUrl\":\"https://tiles.example.com/3d/tileset.json\",\"maximumScreenSpaceError\":24.5}",
                  null),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440106"),
                  false,
                  "Legacy WMS",
                  "WMS basemap before V19",
                  "https://wms.example.com/service",
                  "WMS",
                  "{\"kind\":\"WMS\",\"version\":\"1.3.0\",\"layers\":[\"base\"],\"styles\":[\"default\"],\"format\":\"image/png\",\"transparent\":true,\"crs\":\"EPSG:3857\",\"extraParams\":{\"TIME\":\"default\"}}",
                  null),
              new LegacyMapTileRow(
                  UUID.fromString("550e8400-e29b-41d4-a716-446655440107"),
                  false,
                  "Legacy WMTS",
                  "WMTS basemap before V19",
                  "https://wmts.example.com",
                  "WMTS",
                  "{\"kind\":\"WMTS\",\"version\":\"1.0.0\",\"layer\":\"precip\",\"style\":\"default\",\"format\":\"image/png\",\"tileMatrixSet\":\"GoogleMapsCompatible_Level6\",\"extraParams\":{\"Time\":\"default\"}}",
                  "vault/wmts"));

      legacyRows.forEach(row -> insertMapTile(jdbc, row));

      var beforeById = loadMapTiles(jdbc);

      migrateAll(dataSource);

      var afterById = loadMapTiles(jdbc);
      assertThat(afterById).hasSize(legacyRows.size());

      for (var row : legacyRows) {
        var before = beforeById.get(row.id());
        var after = afterById.get(row.id());

        assertThat(after).isNotNull();
        assertThat(before).isNotNull();
        assertThat(after.nonJsonColumns()).isEqualTo(before.nonJsonColumns());
        assertThat(json(after.configJson()))
            .isEqualTo(expectedWrappedConfig(json(before.configJson())));
      }
    }
  }

  @Test
  void migrate_leavesAlreadyWrappedRowsUnchanged() throws Exception {
    try (var postgres = EmbeddedPostgres.builder().start()) {
      var dataSource = postgres.getPostgresDatabase();
      migrateToVersion(dataSource, "14");
      var jdbc = new JdbcTemplate(dataSource);

      var wrappedConfig =
          """
          {
            "type": "BASEMAP",
            "extension": null,
            "tile": {
              "kind": "RASTER",
              "tileSize": 256,
              "minZoom": 0,
              "maxZoom": 12
            }
          }
          """;

      var wrappedRow =
          new LegacyMapTileRow(
              UUID.fromString("550e8400-e29b-41d4-a716-446655440108"),
              false,
              "Already Wrapped Raster",
              "Config already in envelope before V19 rerun",
              "https://tiles.example.com/already-wrapped/{z}/{x}/{y}.png",
              "RASTER",
              wrappedConfig,
              "vault/already-wrapped");

      insertMapTile(jdbc, wrappedRow);
      var before = loadMapTile(jdbc, wrappedRow.id());

      migrateAll(dataSource);

      var after = loadMapTile(jdbc, wrappedRow.id());

      assertThat(after.nonJsonColumns()).isEqualTo(before.nonJsonColumns());
      assertThat(json(after.configJson())).isEqualTo(json(before.configJson()));
    }
  }

  private static void migrateToVersion(DataSource dataSource, String targetVersion) {
    Flyway.configure()
        .dataSource(dataSource)
        .baselineOnMigrate(true)
        .locations("classpath:db/migration")
        .placeholders(FLYWAY_PLACEHOLDERS)
        .target(targetVersion)
        .load()
        .migrate();
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

  private static void insertMapTile(JdbcTemplate jdbc, LegacyMapTileRow row) {
    jdbc.update(
        """
        INSERT INTO map_tile (id, is_primary, name, description, url, kind, config, token_ref)
        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
        """,
        row.id(),
        row.primary(),
        row.name(),
        row.description(),
        row.url(),
        row.kind(),
        row.configJson(),
        row.tokenRef());
  }

  private static Map<UUID, PersistedMapTileRow> loadMapTiles(JdbcTemplate jdbc) {
    return jdbc
        .query(
            """
            SELECT id, is_primary, name, description, url, kind, config::text AS config_json, token_ref
            FROM map_tile
            ORDER BY id
            """,
            (rs, rowNum) ->
                new PersistedMapTileRow(
                    UUID.fromString(rs.getString("id")),
                    rs.getBoolean("is_primary"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("url"),
                    rs.getString("kind"),
                    rs.getString("config_json"),
                    rs.getString("token_ref")))
        .stream()
        .collect(LinkedHashMap::new, (rows, row) -> rows.put(row.id(), row), LinkedHashMap::putAll);
  }

  private static PersistedMapTileRow loadMapTile(JdbcTemplate jdbc, UUID id) {
    return jdbc.queryForObject(
        """
        SELECT id, is_primary, name, description, url, kind, config::text AS config_json, token_ref
        FROM map_tile
        WHERE id = ?
        """,
        (rs, rowNum) ->
            new PersistedMapTileRow(
                UUID.fromString(rs.getString("id")),
                rs.getBoolean("is_primary"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("url"),
                rs.getString("kind"),
                rs.getString("config_json"),
                rs.getString("token_ref")),
        id);
  }

  private static JsonNode expectedWrappedConfig(JsonNode legacyConfig) {
    ObjectNode wrapped = OBJECT_MAPPER.createObjectNode();
    wrapped.put("type", "BASEMAP");
    wrapped.putNull("extension");
    wrapped.set("tile", legacyConfig.deepCopy());
    return wrapped;
  }

  private static JsonNode json(String rawJson) {
    try {
      return OBJECT_MAPPER.readTree(rawJson);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse json: " + rawJson, e);
    }
  }

  private record LegacyMapTileRow(
      UUID id,
      boolean primary,
      String name,
      String description,
      String url,
      String kind,
      String configJson,
      String tokenRef) {}

  private record PersistedMapTileRow(
      UUID id,
      boolean primary,
      String name,
      String description,
      String url,
      String kind,
      String configJson,
      String tokenRef) {
    private NonJsonColumns nonJsonColumns() {
      return new NonJsonColumns(id, primary, name, description, url, kind, tokenRef);
    }
  }

  private record NonJsonColumns(
      UUID id,
      boolean primary,
      String name,
      String description,
      String url,
      String kind,
      String tokenRef) {}
}
