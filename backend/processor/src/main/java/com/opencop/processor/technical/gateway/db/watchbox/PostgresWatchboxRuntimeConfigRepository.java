// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db.watchbox;

import com.opencop.core.sdk.model.watchbox.WatchboxDirection;
import com.opencop.core.sdk.model.watchbox.WatchboxFilter;
import com.opencop.core.sdk.model.watchbox.WatchboxStatusReason;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeSnapshot;
import com.opencop.processor.business.model.watchbox.WatchboxRuntimeTarget;
import com.opencop.processor.ports.technical.gateway.db.watchbox.WatchboxRuntimeConfigGateway;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Polygon;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

public final class PostgresWatchboxRuntimeConfigRepository implements WatchboxRuntimeConfigGateway {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PostgresWatchboxRuntimeConfigRepository.class);
  private static final String PARSE_FAILURE_METRIC =
      "opencop.watchbox.pipeline.config.parse_failures";

  /**
   * Loads every runtime target the Processor should evaluate.
   *
   * <p>The runtime-feature predicate is deliberately <em>not</em> the same check Core makes. {@code
   * CheckRuntimeFeatureAvailabilityUsecase} answers "may <em>this caller</em> use the Watchbox
   * feature", and so resolves {@code USER_GROUPS} against the caller's group membership. The
   * Processor has no caller: it runs as a background consumer and is answering a different
   * question, "is the Watchbox feature live for anybody".
   *
   * <p>That is why any {@code audience_mode <> 'NONE'} enables detection. When an administrator
   * enables Watchbox for a pilot group, members of that group can create watchboxes; those rows are
   * real and enabled, and detection must run for them. Requiring {@code 'ALL'} here would let a
   * group-scoped rollout create watchboxes that silently never fire.
   *
   * <p>{@code consumer_class} is intentionally not re-checked, and adding that check would be wrong
   * rather than merely redundant. {@code FRONTEND_ONLY} means "the backend does not enforce this
   * flag", so gating detection on it would read "the backend does not enforce this" as "the backend
   * disables this" — a guess, and the opposite of what the classification says. Today a mismatched
   * row already fails loudly in Core, whose typed availability path throws {@code
   * RUNTIME_FEATURE_REQUIRED_ROW_MISSING}: administrators immediately cannot manage watchboxes,
   * while existing ones keep being detected. Gating here would add a silent detection outage on top
   * of that loud failure.
   *
   * <p>The state is also unreachable in practice. The watchbox row's {@code consumer_class} is
   * written exactly once, by the V33 seed. {@code ManageRuntimeFeatures} registration writes the
   * column only when creating a missing FRONTEND_ONLY row and cannot touch this one: {@code
   * RuntimeFeatureNamePolicy} refuses every backend-enforced wire name, and the insert is {@code ON
   * CONFLICT DO NOTHING} ({@code
   * JpaRuntimeFeatureStoreTest.registerReportsAnExistingBackendEnforcedRowWithoutReclassifyingIt}
   * pins this against the seeded row). {@code V33FlywayMigrationTest} pins the seeded
   * classification and {@code RuntimeFeatureUsecasesTest} pins Core's loud rejection.
   *
   * <p>If this ever needs to be prevented rather than tolerated, add a database constraint such as
   * {@code CHECK (name <> 'watchbox' OR consumer_class = 'BACKEND_ENFORCED')} so the state cannot
   * exist. Make the bad state impossible; do not make a background consumer guess what it meant.
   *
   * <p>JPA won't work here: this query assembles a denormalized read model, not an entity graph —
   * each row's JSON columns are parsed into prepared geometries and filter values on plain records
   * with no identity or managed lifecycle to map.
   */
  private static final String LOAD_SQL =
      """
      SELECT w.id AS watchbox_id, w.config_epoch, w.direction,
             w.polygon_json, w.filter_json,
             sla.server_id AS source_id,
             s.status IS NOT NULL AS frozen, s.reason_code
      FROM watchbox.watchbox w
      JOIN watchbox.watchbox_layer wl ON wl.watchbox_id = w.id
      JOIN iam.layer l ON l.id = wl.layer_id AND l.lifecycle_state = 'ACTIVE'
      JOIN iam.server_layer_access sla ON sla.layer_id = wl.layer_id
      LEFT JOIN watchbox.watchbox_source_status s
        ON s.watchbox_id = w.id AND s.config_epoch = w.config_epoch
       AND s.source_id = sla.server_id AND s.status <> 'ACTIVE'
      WHERE w.enabled AND w.archived_at IS NULL
        AND EXISTS (SELECT 1 FROM config.runtime_feature
                    WHERE name = 'watchbox' AND audience_mode <> 'NONE')
      ORDER BY sla.server_id, w.id
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final ProcessorMetrics metrics;
  private final GeometryFactory geometryFactory = new GeometryFactory();

  public PostgresWatchboxRuntimeConfigRepository(
      JdbcTemplate jdbc, ObjectMapper objectMapper, ProcessorMetrics metrics) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.metrics = metrics;
  }

  @Override
  public WatchboxRuntimeSnapshot load() {
    List<RawTarget> rows =
        jdbc.query(
            LOAD_SQL,
            (resultSet, _) ->
                new RawTarget(
                    resultSet.getObject("watchbox_id", UUID.class),
                    resultSet.getLong("config_epoch"),
                    WatchboxDirection.valueOf(resultSet.getString("direction")),
                    resultSet.getString("polygon_json"),
                    resultSet.getString("filter_json"),
                    resultSet.getObject("source_id", UUID.class),
                    resultSet.getBoolean("frozen"),
                    resultSet.getString("reason_code")));

    Map<UUID, ParsedTarget> parsedByWatchbox = new HashMap<>();
    var invalidWatchboxes = new HashSet<UUID>();
    Map<UUID, LinkedHashMap<TargetKey, WatchboxRuntimeTarget>> indexed = new LinkedHashMap<>();
    for (var row : rows) {
      if (invalidWatchboxes.contains(row.watchboxId())) {
        continue;
      }
      var parsed = parsedByWatchbox.get(row.watchboxId());
      if (parsed == null) {
        try {
          parsed = parse(row);
          parsedByWatchbox.put(row.watchboxId(), parsed);
        } catch (RuntimeException exception) {
          invalidWatchboxes.add(row.watchboxId());
          metrics.incrementCounter(PARSE_FAILURE_METRIC, Tags.empty());
          LOGGER.warn(
              "Skipping invalid watchbox runtime config: watchboxId={}",
              row.watchboxId(),
              exception);
          continue;
        }
      }
      var frozenReason = row.frozen() ? WatchboxStatusReason.valueOf(row.reasonCode()) : null;
      var target =
          new WatchboxRuntimeTarget(
              parsed.watchboxId(),
              parsed.configEpoch(),
              parsed.direction(),
              parsed.geometry(),
              parsed.filter(),
              frozenReason);
      indexed
          .computeIfAbsent(row.sourceId(), _ -> new LinkedHashMap<>())
          .putIfAbsent(new TargetKey(row.watchboxId(), row.sourceId()), target);
    }

    Map<UUID, List<WatchboxRuntimeTarget>> targets = new LinkedHashMap<>();
    indexed.forEach((sourceId, byPair) -> targets.put(sourceId, List.copyOf(byPair.values())));
    return new WatchboxRuntimeSnapshot(targets);
  }

  private ParsedTarget parse(RawTarget row) {
    try {
      var polygon = objectMapper.readValue(row.polygonJson(), Polygon.class);
      var filter = objectMapper.readValue(row.filterJson(), WatchboxFilter.class);
      var coordinates =
          polygon.getExteriorRing().stream()
              .map(point -> new Coordinate(point.getLongitude(), point.getLatitude()))
              .toArray(Coordinate[]::new);
      var geometry = PreparedGeometryFactory.prepare(geometryFactory.createPolygon(coordinates));
      return new ParsedTarget(
          row.watchboxId(), row.configEpoch(), row.direction(), geometry, filter);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid watchbox runtime config", exception);
    }
  }

  private record RawTarget(
      UUID watchboxId,
      long configEpoch,
      WatchboxDirection direction,
      String polygonJson,
      String filterJson,
      UUID sourceId,
      boolean frozen,
      String reasonCode) {}

  private record ParsedTarget(
      UUID watchboxId,
      long configEpoch,
      WatchboxDirection direction,
      PreparedGeometry geometry,
      WatchboxFilter filter) {}

  private record TargetKey(UUID watchboxId, UUID sourceId) {}
}
