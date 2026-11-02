// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.geocoding;

/** Why a place a geocoder returned is unfit to send to clients. */
public enum PlaceDefect {
  MISSING_PLACE,
  BLANK_ID,
  BLANK_NAME,
  BLANK_LABEL,
  INVALID_POSITION,
  INVALID_BBOX,
  INVALID_ATTRIBUTION
}
