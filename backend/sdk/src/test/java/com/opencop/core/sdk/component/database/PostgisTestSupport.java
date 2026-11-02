// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.component.database;

import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgisTestSupport {

  private PostgisTestSupport() {}

  public static void createCompatibilityFunctions(DataSource dataSource) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute(
        "CREATE OR REPLACE FUNCTION public.ST_MakeEnvelope("
            + "double precision, double precision, double precision, double precision, integer"
            + ") RETURNS text LANGUAGE SQL IMMUTABLE AS $$ "
            + "SELECT format('ENVELOPE(%s,%s,%s,%s,%s)', $1, $2, $3, $4, $5); $$");
    jdbcTemplate.execute(
        """
        CREATE OR REPLACE FUNCTION public.text_bbox_overlaps(point_geom text, envelope text)
        RETURNS boolean
        LANGUAGE plpgsql
        IMMUTABLE
        AS $$
        DECLARE
          point_match text[];
          envelope_match text[];
          lon double precision;
          lat double precision;
          min_lon double precision;
          min_lat double precision;
          max_lon double precision;
          max_lat double precision;
        BEGIN
          point_match := regexp_match(
              point_geom,
              'POINT(?: Z)? \\(([-0-9.]+) ([-0-9.]+)(?: [-0-9.]+)?\\)');
          envelope_match := regexp_match(
              envelope,
              'ENVELOPE\\(([-0-9.]+),([-0-9.]+),([-0-9.]+),([-0-9.]+),([-0-9.]+)\\)');

          IF point_match IS NULL OR envelope_match IS NULL THEN
            RETURN FALSE;
          END IF;

          lon := point_match[1]::double precision;
          lat := point_match[2]::double precision;
          min_lon := envelope_match[1]::double precision;
          min_lat := envelope_match[2]::double precision;
          max_lon := envelope_match[3]::double precision;
          max_lat := envelope_match[4]::double precision;

          RETURN lon BETWEEN min_lon AND max_lon
              AND lat BETWEEN min_lat AND max_lat;
        END;
        $$;
        """);
    jdbcTemplate.execute("DROP OPERATOR IF EXISTS public.&&(text, text)");
    jdbcTemplate.execute(
        "CREATE OPERATOR public.&& (LEFTARG = text, RIGHTARG = text, PROCEDURE = public.text_bbox_overlaps)");
  }
}
