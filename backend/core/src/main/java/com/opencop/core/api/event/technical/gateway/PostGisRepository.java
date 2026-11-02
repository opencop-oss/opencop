// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.event.business.model.HistogramBucketValue;
import com.opencop.core.api.event.business.model.HistogramMetric;
import com.opencop.core.api.event.business.model.SamplingMeta;
import com.opencop.core.api.event.business.model.SourceHistogramBucket;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.api.event.ports.gateway.PostGisService;
import com.opencop.core.sdk.technical.gateway.db.NotFoundException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.model.opencop.parser.OpenCopPropertiesParser;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.geojson.GeoJsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PostGisRepository implements PostGisService {

  private static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 5;
  private static final int HISTOGRAM_QUERY_TIMEOUT_SECONDS = 30;

  private static final Logger log = LoggerFactory.getLogger(PostGisRepository.class);

  private final NamedParameterJdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate histogramJdbc;
  private final ObjectMapper objectMapper;
  private final FeatureRowMapper featureRowMapper = new FeatureRowMapper();

  public PostGisRepository(final NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    jdbc.getJdbcTemplate().setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
    this.histogramJdbc = new NamedParameterJdbcTemplate(createHistogramJdbcTemplate(jdbc));
    this.objectMapper = objectMapper;
  }

  private static JdbcTemplate createHistogramJdbcTemplate(NamedParameterJdbcTemplate jdbc) {
    DataSource dataSource = jdbc.getJdbcTemplate().getDataSource();
    if (dataSource == null) {
      throw new IllegalStateException("Histogram query DataSource is not configured");
    }

    JdbcTemplate histogramJdbcTemplate = new JdbcTemplate(dataSource);
    histogramJdbcTemplate.setQueryTimeout(HISTOGRAM_QUERY_TIMEOUT_SECONDS);
    return histogramJdbcTemplate;
  }

  public EventHistoryResponse getEventHistoryBucketed(
      UUID sourceId, String objectId, Instant from, Instant to, int maxPoints) {

    String bucketSize = computeBucketSize(from, to, maxPoints);

    String sql =
        """
      WITH filtered AS (
        SELECT *
        FROM geo.features
        WHERE source = :source
          AND object_id = :objectId
          AND created_at >= :from::timestamptz
          AND created_at <  :to::timestamptz
      ),
      tot AS ( SELECT COUNT(*)::bigint AS total_points FROM filtered ),
      bucketed AS (
        SELECT *,
               date_bin(:bucketSize::interval, created_at, :from) AS b
        FROM filtered
      ),
      ranked AS (
        SELECT *,
               row_number() OVER (PARTITION BY b ORDER BY created_at ASC)  AS rn_first,
               row_number() OVER (PARTITION BY b ORDER BY created_at DESC) AS rn_last
        FROM bucketed
      ),
      sampled AS (
        SELECT *
        FROM ranked
        WHERE rn_first = 1 OR rn_last = 1
        ORDER BY created_at ASC
      ),
      used AS ( SELECT COUNT(*)::bigint AS used_points FROM sampled )
      SELECT s.*, t.total_points, u.used_points
      FROM sampled s
      CROSS JOIN tot t
      CROSS JOIN used u
      ORDER BY s.created_at ASC
      """;

    Map<String, Object> params =
        Map.of(
            "source",
            sourceId,
            "objectId",
            objectId,
            "from",
            Timestamp.from(from),
            "to",
            Timestamp.from(to),
            "bucketSize",
            bucketSize);

    return jdbc.query(
        sql,
        params,
        rs -> {
          List<Feature> features = new ArrayList<>();
          long totalPoints = 0;
          long usedPoints = 0;
          boolean countsRead = false;

          while (rs.next()) {
            if (!countsRead) {
              totalPoints = rs.getLong("total_points");
              usedPoints = rs.getLong("used_points");
              countsRead = true;
            }
            features.add(featureRowMapper.mapRow(rs, rs.getRow()));
          }

          FeatureCollection fc = new FeatureCollection();
          fc.setFeatures(features);

          SamplingMeta sampling =
              new SamplingMeta("first_last_bucket", maxPoints, bucketSize, totalPoints, usedPoints);

          return new EventHistoryResponse(objectId, from, to, sampling, fc);
        });
  }

  public EventHistoryResponse getEventHistoryAsLine(
      UUID sourceId, String objectId, Instant from, Instant to, int maxPoints) {

    String bucketSize = computeBucketSize(from, to, maxPoints);

    String sql =
        """
      WITH filtered AS (
        SELECT ST_Force3D(geom) AS geom, created_at
        FROM geo.features
        WHERE source = :source
          AND object_id = :objectId
          AND created_at >= :from
          AND created_at <  :to
      ),
      tot AS ( SELECT COUNT(*)::bigint AS total_points FROM filtered ),
      bucketed AS (
        SELECT geom, created_at,
               date_bin(:bucketSize::interval, created_at, :from) AS b
        FROM filtered
      ),
      ranked AS (
        SELECT geom, created_at,
               row_number() OVER (PARTITION BY b ORDER BY created_at ASC)  AS rn_first,
               row_number() OVER (PARTITION BY b ORDER BY created_at DESC) AS rn_last
        FROM bucketed
      ),
      sampled AS (
        SELECT geom, created_at
        FROM ranked
        WHERE rn_first = 1 OR rn_last = 1
        ORDER BY created_at
      ),
      used AS ( SELECT COUNT(*)::bigint AS used_points FROM sampled ),
      line AS (
        SELECT ST_AsGeoJSON(ST_MakeLine(geom ORDER BY created_at)) AS line_geojson
        FROM sampled
      )
      SELECT line.line_geojson, tot.total_points, used.used_points
      FROM line
      CROSS JOIN tot
      CROSS JOIN used
      """;

    Map<String, Object> params =
        Map.of(
            "source",
            sourceId,
            "objectId",
            objectId,
            "from",
            Timestamp.from(from),
            "to",
            Timestamp.from(to),
            "bucketSize",
            bucketSize);

    return jdbc.query(
        sql,
        params,
        rs -> {
          String lineGeoJson = null;
          long totalPoints = 0;
          long usedPoints = 0;

          if (rs.next()) {
            lineGeoJson = rs.getString("line_geojson");
            totalPoints = rs.getLong("total_points");
            usedPoints = rs.getLong("used_points");
          }

          FeatureCollection fc = new FeatureCollection();
          fc.setFeatures(List.of());

          if (lineGeoJson != null && !lineGeoJson.isBlank() && usedPoints >= 2) {
            GeoJsonObject geometry = objectMapper.readValue(lineGeoJson, GeoJsonObject.class);

            Feature lineFeature = new Feature();
            lineFeature.setGeometry(geometry);

            fc.setFeatures(List.of(lineFeature));
          }

          SamplingMeta sampling =
              new SamplingMeta("first_last_bucket", maxPoints, bucketSize, totalPoints, usedPoints);

          return new EventHistoryResponse(objectId, from, to, sampling, fc);
        });
  }

  @Override
  public SourceHistogramResponse getSourceHistogram(
      UUID sourceId, Instant from, Instant to, int bars, Duration bucketWidth, BoundingBox bbox) {
    Instant startedAt = Instant.now();
    String sql = buildSourceHistogramSql(bbox);

    Map<String, Object> params = new HashMap<>();
    params.put("source", sourceId);
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));
    params.put("bars", bars);
    params.put("bucketSeconds", bucketWidth.toSeconds());
    if (bbox != null) {
      params.put("minLon", bbox.minLon());
      params.put("minLat", bbox.minLat());
      params.put("maxLon", bbox.maxLon());
      params.put("maxLat", bbox.maxLat());
    }

    log.info(
        "Starting source histogram query: sourceId={}, from={}, to={}, bars={}, bboxPresent={}",
        sourceId,
        from,
        to,
        bars,
        bbox != null);

    try {
      SourceHistogramResponse response =
          histogramJdbc.query(
              sql,
              params,
              rs -> {
                List<SourceHistogramBucket> buckets = new ArrayList<>();

                while (rs.next()) {
                  buckets.add(
                      new SourceHistogramBucket(
                          rs.getTimestamp("bucket_from").toInstant(),
                          rs.getTimestamp("bucket_to").toInstant(),
                          List.of(
                              new HistogramBucketValue(
                                  sourceId, HistogramMetric.ACTIVE, rs.getLong("active_count")),
                              new HistogramBucketValue(
                                  sourceId, HistogramMetric.UPDATED, rs.getLong("bucket_count")))));
                }

                return new SourceHistogramResponse(buckets);
              });

      log.info(
          "Completed source histogram query: sourceId={}, bars={}, bucketCount={}, elapsedMs={}",
          sourceId,
          bars,
          response.buckets().size(),
          Duration.between(startedAt, Instant.now()).toMillis());

      return response;
    } catch (RuntimeException e) {
      log.warn(
          "Source histogram query failed: sourceId={}, from={}, to={}, bars={}, bboxPresent={}, elapsedMs={}",
          sourceId,
          from,
          to,
          bars,
          bbox != null,
          Duration.between(startedAt, Instant.now()).toMillis(),
          e);
      throw e;
    }
  }

  static String buildSourceHistogramSql(BoundingBox bbox) {
    String sql =
        """
      WITH constants AS (
        SELECT :bucketSeconds::numeric AS bucket_seconds
      ),
      buckets AS (
        SELECT
          gs.idx AS bucket_index,
          :from::timestamptz
            + ((gs.idx * constants.bucket_seconds) * INTERVAL '1 second') AS bucket_from,
          CASE
            WHEN gs.idx = :bars - 1 THEN :to::timestamptz
            ELSE :from::timestamptz
              + (((gs.idx + 1) * constants.bucket_seconds) * INTERVAL '1 second')
          END AS bucket_to
        FROM generate_series(0, :bars - 1) AS gs(idx)
        CROSS JOIN constants
      ),
      counts AS (
        SELECT
          buckets.bucket_index,
          counts.bucket_count
        FROM buckets
        CROSS JOIN LATERAL (
          SELECT COUNT(*)::bigint AS bucket_count
          FROM geo.features features
          WHERE features.source = :source
            AND features.start_at >= buckets.bucket_from
            AND features.start_at < buckets.bucket_to
      """;
    if (bbox != null) {
      if (bbox.crossesAntimeridian()) {
        sql +=
            """
            AND (
           features.geom && ST_MakeEnvelope(:minLon, :minLat, 180.0, :maxLat, 4326)
           OR features.geom && ST_MakeEnvelope(-180.0, :minLat, :maxLon, :maxLat, 4326)
         )
        """;
      } else {
        sql +=
            """
            AND features.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
        """;
      }
    }
    sql +=
        """
        ) counts
      ),
      active_counts AS (
        SELECT
          buckets.bucket_index,
          COUNT(helper.object_id)::bigint AS active_count
        FROM buckets
        LEFT JOIN geo.feature_latest_active_helper helper
          ON helper.source = :source
         AND helper.first_emission_at < buckets.bucket_to
         AND (helper.stale_at IS NULL OR helper.stale_at > buckets.bucket_from)
        """;
    if (bbox != null) {
      if (bbox.crossesAntimeridian()) {
        sql +=
            """
        LEFT JOIN geo.features active_features
          ON active_features.id = helper.event_id
         AND (
           active_features.geom && ST_MakeEnvelope(:minLon, :minLat, 180.0, :maxLat, 4326)
           OR active_features.geom && ST_MakeEnvelope(-180.0, :minLat, :maxLon, :maxLat, 4326)
         )
        WHERE active_features.id IS NOT NULL
        """;
      } else {
        sql +=
            """
        LEFT JOIN geo.features active_features
          ON active_features.id = helper.event_id
         AND active_features.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)
        WHERE active_features.id IS NOT NULL
        """;
      }
    }
    sql +=
        """
        GROUP BY buckets.bucket_index
      )
      SELECT
        buckets.bucket_index,
        buckets.bucket_from,
        buckets.bucket_to,
        COALESCE(counts.bucket_count, 0) AS bucket_count,
        COALESCE(active_counts.active_count, 0) AS active_count
      FROM buckets
      LEFT JOIN counts ON counts.bucket_index = buckets.bucket_index
      LEFT JOIN active_counts ON active_counts.bucket_index = buckets.bucket_index
      ORDER BY buckets.bucket_index
      """;
    return sql;
  }

  private static String computeBucketSize(Instant from, Instant to, int maxPoints) {
    long seconds = Math.max(1, Duration.between(from, to).getSeconds());
    int buckets = Math.max(1, maxPoints / 2);
    long bucketSeconds = Math.max(1, seconds / buckets);
    return bucketSeconds + " seconds";
  }

  @Override
  public OpenCopProperties getDetails(UUID id) throws PersistenceException {
    String featureSql =
        """
            SELECT *
            FROM geo.features
            WHERE id = :id
        """;
    final Feature feature;
    try {
      feature = jdbc.queryForObject(featureSql, Map.of("id", id), featureRowMapper);
    } catch (EmptyResultDataAccessException _) {
      throw new NotFoundException("Does not exist: " + id);
    }
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(feature);
    if (properties == null) {
      throw new PersistenceException("Could not parse properties");
    }
    return properties;
  }

  @Override
  public OpenCopProperties getLatestDetails(UUID sourceId, String objectId)
      throws PersistenceException {
    Feature feature = getLatestFeature(sourceId, objectId);
    OpenCopProperties properties = OpenCopPropertiesParser.readFromFeature(feature);
    if (properties == null) {
      throw new PersistenceException("Could not parse properties");
    }
    return properties;
  }

  @Override
  public Optional<EventDetails> findEventDetails(UUID sourceId, String objectId, UUID eventId)
      throws PersistenceException {
    // JPA won't work here: geo.features rows are PostGIS geometry plus a jsonb payload read
    // through the feature row mapper; no entity exists for them.
    Optional<Feature> feature;
    try {
      feature =
          jdbc
              .query(
                  """
          SELECT * FROM geo.features
          WHERE id = :id AND source = :source AND object_id = :objectId
          """,
                  Map.of("id", eventId, "source", sourceId, "objectId", objectId),
                  featureRowMapper)
              .stream()
              .findFirst();
    } catch (JacksonException e) {
      throw unreadable(e);
    }
    if (feature.isEmpty()) {
      return Optional.empty();
    }
    return agreeing(feature.get(), sourceId, objectId);
  }

  @Override
  public Optional<EventDetails> findLatestEventDetails(UUID sourceId, String objectId)
      throws PersistenceException {
    Optional<Feature> feature;
    try {
      feature = findLatestFeature(sourceId, objectId);
    } catch (JacksonException e) {
      throw unreadable(e);
    }
    if (feature.isEmpty()) {
      return Optional.empty();
    }
    return agreeing(feature.get(), sourceId, objectId);
  }

  /**
   * Fails closed when the stored payload names another source or object than the row it was
   * selected by: both optional readers apply the same rule, so a corrupt row can never let one
   * scope's payload out under another scope's authorization.
   */
  private Optional<EventDetails> agreeing(Feature feature, UUID sourceId, String objectId)
      throws PersistenceException {
    var details = toEventDetails(feature);
    if (!sourceId.equals(details.properties().getSource())
        || !objectId.equals(details.properties().getObjectId())) {
      log.warn(
          "geo.features payload disagrees with its columns for id={} source={} objectId={}",
          details.properties().getId(),
          sourceId,
          objectId);
      return Optional.empty();
    }
    return Optional.of(details);
  }

  private EventDetails toEventDetails(Feature feature) throws PersistenceException {
    OpenCopProperties properties;
    try {
      properties = OpenCopPropertiesParser.readFromFeature(feature);
    } catch (JacksonException | IllegalArgumentException e) {
      // Jackson 3 lets a databind failure through convertValue unwrapped; both shapes are the
      // same "this row cannot be decoded" condition.
      throw unreadable(e);
    }
    if (properties == null) {
      throw new PersistenceException("Could not parse properties");
    }
    return new EventDetails(properties, feature.getGeometry());
  }

  /**
   * The optional readers serve the detection embeds, which degrade instead of failing: a stored
   * feature that cannot be decoded is reported as unreadable rather than escaping as a runtime
   * exception. Database failures stay unchecked and loud.
   */
  private static PersistenceException unreadable(RuntimeException e) {
    return new PersistenceException("Could not decode stored feature: " + e.getMessage());
  }

  @Override
  public Feature getLatestFeature(UUID sourceId, String objectId) throws PersistenceException {
    return findLatestFeature(sourceId, objectId)
        .orElseThrow(() -> new NotFoundException("No feature found for object_id: " + objectId));
  }

  private Optional<Feature> findLatestFeature(UUID sourceId, String objectId) {
    // JPA won't work here: same geo.features payload read as findEventDetails, latest by
    // created_at.
    var sql =
        """
        SELECT * FROM geo.features
        WHERE source = :source AND object_id = :objectId
        ORDER BY created_at DESC LIMIT 1
        """;
    return jdbc
        .query(sql, Map.of("source", sourceId, "objectId", objectId), featureRowMapper)
        .stream()
        .findFirst();
  }
}
