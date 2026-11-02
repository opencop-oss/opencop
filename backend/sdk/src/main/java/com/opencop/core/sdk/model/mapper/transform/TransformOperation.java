// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.mapper.transform;

public enum TransformOperation {
  TRIM,
  LOWER,
  UPPER,
  REPLACE_REGEX,
  TO_INT,
  TO_DOUBLE,
  RAD_TO_DEGREES,
  GRAD_TO_DEGREES,
  MILS_TO_DEGREES,
  KPH_TO_MPS,
  MPH_TO_MPS,
  KNOTS_TO_MPS,
  FPS_TO_MPS,
  PARSE_DATE_TIME,
  DEFAULT_NOW,
  ADD_DURATION,
  DEFAULT_IF_BLANK,
  MAP_VALUES,
  MGRS_TO_POINT,
  UTM_TO_POINT,
  USNG_TO_POINT,
  DMS_TO_POINT,
  DM_TO_POINT,
  GEOHASH_TO_POINT,
  GEOMETRY
}
