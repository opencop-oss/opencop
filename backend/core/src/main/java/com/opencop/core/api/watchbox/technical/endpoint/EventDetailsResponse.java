// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import org.geojson.GeoJsonObject;

/**
 * An event embedded in a detection detail: the flat shape of the event details endpoints plus the
 * geometry. The component must use OpenCopProperties: OCPropertiesExtension omits id, element,
 * importId, and author.
 */
public record EventDetailsResponse(
    @JsonUnwrapped OpenCopProperties properties, GeoJsonObject geometry) {
  public static EventDetailsResponse from(EventDetails details) {
    return new EventDetailsResponse(details.properties(), details.geometry());
  }
}
