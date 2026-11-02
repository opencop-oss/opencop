// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.geocoding;

/** An optional place field a geocoder left null, which the host filled with its default. */
public enum DefaultedField {
  PLACE_TYPE,
  CATEGORY,
  BBOX
}
