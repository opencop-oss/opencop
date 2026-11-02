// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.util.Objects;
import org.geojson.GeoJsonObject;

/** One stored event revision, built by the gateway from the payload column. */
public record EventDetails(OpenCopProperties properties, GeoJsonObject geometry) {
  public EventDetails {
    Objects.requireNonNull(properties);
  }
}
