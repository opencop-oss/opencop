// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.technical.gateway.db;

import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import com.opencop.processor.ports.technical.gateway.db.GeoCacheHydrationGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Repository
public class GeoCacheHydrationRepository implements GeoCacheHydrationGateway {

  private static final Logger log = LoggerFactory.getLogger(GeoCacheHydrationRepository.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate repeatableReadTransaction;

  public GeoCacheHydrationRepository(
      NamedParameterJdbcTemplate jdbc,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.repeatableReadTransaction = new TransactionTemplate(transactionManager);
    this.repeatableReadTransaction.setReadOnly(true);
    this.repeatableReadTransaction.setIsolationLevelName("ISOLATION_REPEATABLE_READ");
  }

  @Override
  public HydrationSnapshotStats hydrateSnapshot(
      UUID sourceId, int pageSize, java.util.function.Consumer<List<Feature>> pageConsumer) {
    return repeatableReadTransaction.execute(
        _ -> {
          String lastObjectId = null;
          int pageCount = 0;
          int featureCount = 0;
          while (true) {
            List<Feature> page = findLatestActiveBySource(sourceId, lastObjectId, pageSize);
            if (page.isEmpty()) {
              return new HydrationSnapshotStats(pageCount, featureCount, lastObjectId);
            }

            pageConsumer.accept(page);
            pageCount++;
            featureCount += page.size();
            lastObjectId = extractLastObjectId(sourceId, page);
            log.debug(
                "Hydration snapshot page loaded for source {}: page={}, features={}, lastObjectId={}",
                sourceId,
                pageCount,
                page.size(),
                lastObjectId);
          }
        });
  }

  /**
   * One page of the source's live objects: each object's latest revision, kept only when that
   * revision is not stale. Staleness is judged after the latest revision is chosen, so a delete (a
   * newer revision already stale) hides the object instead of exposing an older revision that is
   * still within its stale time.
   */
  private List<Feature> findLatestActiveBySource(UUID sourceId, String lastObjectId, int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT payload
            FROM (
              SELECT DISTINCT ON (object_id)
                object_id,
                payload,
                stale_at
              FROM geo.features
              WHERE source = :sourceId
            """);

    if (lastObjectId != null) {
      sql.append("\n        AND object_id > :lastObjectId");
    }

    sql.append(
        """

              ORDER BY object_id, COALESCE(start_at, created_at) DESC, created_at DESC, id DESC
            ) latest
            WHERE stale_at IS NULL OR stale_at > now()
            ORDER BY object_id
            LIMIT :limit
            """);

    Map<String, Object> params = new HashMap<>();
    params.put("sourceId", sourceId);
    if (lastObjectId != null) {
      params.put("lastObjectId", lastObjectId);
    }
    params.put("limit", limit);

    return jdbc.query(
        sql.toString(),
        params,
        (rs, rowNum) -> objectMapper.readValue(rs.getString("payload"), Feature.class));
  }

  private static String extractLastObjectId(UUID sourceId, List<Feature> page) {
    Feature last = page.getLast();
    var opencop = OpenCopPropertiesParser.readFromFeature(last);
    if (opencop == null || !sourceId.equals(opencop.getSource())) {
      throw new IllegalStateException("Hydration page ended with an invalid feature payload");
    }
    return opencop.getObjectId();
  }
}
