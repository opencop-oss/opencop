// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.processor.business.model.history.HistoricalReadContext;
import com.opencop.processor.business.model.history.HistoricalReadCursor;
import com.opencop.processor.business.model.history.HistoricalReadPage;
import com.opencop.processor.business.model.history.HistoricalSourceEvent;
import com.opencop.processor.business.model.history.chunk.HistoricalChunkPlanEntry;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class PostGisHistoricalCheckpointReader {

  private static final int DELTA_REPLAY_PAGE_SIZE = 10_000;

  private final NamedParameterJdbcTemplate jdbc;
  private final PostGisHistoricalDeltaReader deltaReader;
  private final int queryTimeoutSeconds;

  PostGisHistoricalCheckpointReader(
      NamedParameterJdbcTemplate jdbc,
      PostGisHistoricalDeltaReader deltaReader,
      Duration queryTimeout) {
    this.jdbc = jdbc;
    this.deltaReader = deltaReader;
    this.queryTimeoutSeconds = JdbcQueryTimeout.seconds(queryTimeout);
  }

  HistoricalReadPage read(
      HistoricalReadContext context, HistoricalChunkPlanEntry chunk, int requestedPageSize) {
    return read(context, chunk, requestedPageSize, null);
  }

  HistoricalReadPage read(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      int requestedPageSize,
      HistoricalReadCursor cursor) {
    List<HistoricalSourceEvent> checkpointRows = readCheckpointRows(context, chunk);
    if (checkpointRows.isEmpty()) {
      return new HistoricalReadPage(List.of(), null, false);
    }

    Map<String, HistoricalSourceEvent> state = new LinkedHashMap<>();
    for (HistoricalSourceEvent row : checkpointRows) {
      state.put(row.objectId(), row);
    }

    replayDeltas(context, chunk, state);
    List<HistoricalSourceEvent> seededRows =
        state.values().stream()
            .filter(row -> row.playbackAt().isBefore(chunk.to()))
            .filter(row -> row.staleAt() == null || row.staleAt().isAfter(chunk.from()))
            .sorted(PostGisHistoricalReadRows.pageOrder())
            .toList();
    List<HistoricalSourceEvent> materializedRows = rowsFromCursor(seededRows, cursor);
    return PostGisHistoricalReadRows.page(materializedRows, requestedPageSize);
  }

  private List<HistoricalSourceEvent> readCheckpointRows(
      HistoricalReadContext context, HistoricalChunkPlanEntry chunk) {
    Map<String, Object> params = new HashMap<>();
    params.put("sourceId", context.singleSourceId());
    params.put("checkpointResolution", context.checkpointResolution().name());
    params.put("checkpointAt", Timestamp.from(chunk.from()));
    return TimedNamedParameterJdbcQuery.query(
        jdbc,
        PostGisHistoricalReadSql.CHECKPOINT_SQL,
        params,
        (rs, rowNum) -> PostGisHistoricalReadRows.mapRow(rs),
        queryTimeoutSeconds);
  }

  private void replayDeltas(
      HistoricalReadContext context,
      HistoricalChunkPlanEntry chunk,
      Map<String, HistoricalSourceEvent> state) {
    HistoricalReadContext deltaContext =
        new HistoricalReadContext(context.sourceIds(), context.singleSourceId(), false);
    HistoricalReadCursor cursor = null;
    boolean hasMore;
    do {
      HistoricalReadPage deltaPage =
          deltaReader.read(deltaContext, chunk, chunk.from(), cursor, DELTA_REPLAY_PAGE_SIZE);
      for (HistoricalSourceEvent row : deltaPage.rows()) {
        state.put(row.objectId(), row);
      }
      cursor = deltaPage.nextCursor();
      hasMore = deltaPage.hasMore();
    } while (hasMore);
  }

  private static List<HistoricalSourceEvent> rowsFromCursor(
      List<HistoricalSourceEvent> rows, HistoricalReadCursor cursor) {
    if (cursor == null) {
      return rows;
    }
    int start = 0;
    while (start < rows.size() && !isAfterCursor(rows.get(start), cursor)) {
      start++;
    }
    return rows.subList(start, rows.size());
  }

  private static boolean isAfterCursor(HistoricalSourceEvent row, HistoricalReadCursor cursor) {
    int playbackCmp = row.playbackAt().compareTo(cursor.playbackAt());
    if (playbackCmp != 0) {
      return playbackCmp > 0;
    }

    int objectCmp = row.objectId().compareTo(cursor.objectId());
    if (objectCmp != 0) {
      return objectCmp > 0;
    }
    return row.eventId().compareTo(cursor.eventId()) > 0;
  }
}
