// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.technical.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.api.event.business.model.BoundingBox;
import org.junit.jupiter.api.Test;

class PostGisRepositoryTest {

  @Test
  void buildSourceHistogramSql_withBbox_usesGeomEnvelopeFilter() {
    String sql =
        PostGisRepository.buildSourceHistogramSql(new BoundingBox(-73.7, 45.4, -73.5, 45.6));

    assertTrue(sql.contains("geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)"));
    assertFalse(sql.contains("payload->'geometry'"));
    assertFalse(sql.contains("coordinates"));
  }

  @Test
  void buildSourceHistogramSql_withoutBbox_doesNotIncludeSpatialFilter() {
    String sql = PostGisRepository.buildSourceHistogramSql(null);

    assertFalse(sql.contains("ST_MakeEnvelope"));
    assertFalse(sql.contains("payload->'geometry'"));
  }

  @Test
  void buildSourceHistogramSql_usesBucketDrivenUpdatedRangeCounts() {
    String sql = PostGisRepository.buildSourceHistogramSql(null);

    assertTrue(sql.contains("CROSS JOIN LATERAL"));
    assertTrue(sql.contains("SELECT COUNT(*)::bigint AS bucket_count"));
    assertTrue(sql.contains("WHERE features.source = :source"));
    assertTrue(sql.contains("features.start_at >= buckets.bucket_from"));
    assertTrue(sql.contains("features.start_at < buckets.bucket_to"));
    assertFalse(sql.contains("COUNT(features.id)::bigint AS bucket_count"));
    assertFalse(sql.contains("LEFT JOIN geo.features features"));
    assertFalse(sql.contains("filtered AS"));
    assertFalse(sql.contains("bucketed AS"));
    assertFalse(sql.contains("FROM bucketed"));
    assertFalse(sql.contains("GROUP BY bucket_index"));
  }

  @Test
  void buildSourceHistogramSql_usesPlannerBucketWidthForBucketBoundaries() {
    String sql = PostGisRepository.buildSourceHistogramSql(null);

    assertTrue(sql.contains(":bucketSeconds::numeric AS bucket_seconds"));
    assertTrue(sql.contains("constants.bucket_seconds"));
    assertFalse(sql.contains("total_seconds / :bars"));
  }

  @Test
  void buildSourceHistogramSql_keepsActiveHelperOverlapPredicates() {
    String sql = PostGisRepository.buildSourceHistogramSql(null);

    assertTrue(sql.contains("geo.feature_latest_active_helper helper"));
    assertTrue(sql.contains("helper.first_emission_at < buckets.bucket_to"));
    assertTrue(sql.contains("helper.stale_at IS NULL OR helper.stale_at > buckets.bucket_from"));
  }

  @Test
  void buildSourceHistogramSql_withAntimeridianBbox_splitsIntoTwoEnvelopes() {
    String sql =
        PostGisRepository.buildSourceHistogramSql(new BoundingBox(170.0, 10.0, -170.0, 20.0));

    assertTrue(sql.contains("geom && ST_MakeEnvelope(:minLon, :minLat, 180.0, :maxLat, 4326)"));
    assertTrue(sql.contains("geom && ST_MakeEnvelope(-180.0, :minLat, :maxLon, :maxLat, 4326)"));
  }
}
