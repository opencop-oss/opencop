// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.gateway.db;

import com.opencop.core.api.watchbox.business.model.WatchboxDetection;
import com.opencop.core.api.watchbox.business.model.WatchboxDetectionPageRequest;
import com.opencop.core.api.watchbox.ports.gateway.WatchboxDetectionReader;
import com.opencop.core.sdk.model.watchbox.WatchboxTransitionKind;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC read over a table the processor owns for writes and garbage collection; deliberately no
 * entity.
 */
public class PostgresWatchboxDetectionReader implements WatchboxDetectionReader {
  // JPA won't work here: core owns no entity for watchbox_detection by design (the processor
  // writes and expires it), and mapping one only for this read would duplicate that ownership.
  private static final String SELECT =
      """
      SELECT id, watchbox_id, config_epoch, source_id, object_id, event_id,
             transition, event_time, object_display_name, created_at
      FROM watchbox.watchbox_detection
      """;
  private static final RowMapper<WatchboxDetection> ROW_MAPPER =
      (rs, row) ->
          new WatchboxDetection(
              rs.getObject("id", UUID.class),
              rs.getObject("watchbox_id", UUID.class),
              rs.getLong("config_epoch"),
              rs.getObject("source_id", UUID.class),
              rs.getString("object_id"),
              rs.getObject("event_id", UUID.class),
              rs.getTimestamp("event_time").toInstant(),
              WatchboxTransitionKind.valueOf(rs.getString("transition")),
              rs.getString("object_display_name"),
              rs.getTimestamp("created_at").toInstant());
  private final NamedParameterJdbcTemplate jdbc;

  public PostgresWatchboxDetectionReader(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PageResponse<WatchboxDetection> findPage(
      Set<UUID> watchboxIds, Set<WatchboxTransitionKind> kinds, WatchboxDetectionPageRequest page) {
    if (watchboxIds.isEmpty()) {
      return new PageResponse<>(List.of(), page.page(), page.size(), 0, 0);
    }
    var where =
        " WHERE watchbox_id IN (:watchboxIds)"
            + (kinds.isEmpty() ? "" : " AND transition IN (:kinds)");
    var params = new HashMap<String, Object>();
    params.put("watchboxIds", List.copyOf(watchboxIds));
    params.put("kinds", kinds.stream().map(Enum::name).toList());
    params.put("limit", page.size());
    params.put("offset", page.offset());
    // JPA won't work here: see SELECT; one page statement over a caller-computed id set.
    var content =
        jdbc.query(
            SELECT + where + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
            params,
            ROW_MAPPER);
    // JPA won't work here: see SELECT; the count shares the page's WHERE text so they cannot drift.
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM watchbox.watchbox_detection" + where, params, Long.class);
    int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / page.size());
    return new PageResponse<>(content, page.page(), page.size(), total, totalPages);
  }

  @Override
  public Optional<WatchboxDetection> findById(UUID detectionId) {
    // JPA won't work here: see SELECT.
    return jdbc.query(SELECT + " WHERE id = :id", Map.of("id", detectionId), ROW_MAPPER).stream()
        .findFirst();
  }
}
