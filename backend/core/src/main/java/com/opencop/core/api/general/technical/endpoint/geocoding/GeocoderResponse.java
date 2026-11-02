// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import com.opencop.core.api.general.ports.usecase.geocoding.GeocoderSummary;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** A discovery entry: the ids search accepts, display labels and the supported operations. */
@Schema(
    name = "GeocoderResponse",
    description =
        """
        A geocoder clients may use. Address it in operation paths by `integrationId` and \
        `geocoderId` together; neither identifies it alone.""")
record GeocoderResponse(
    @Schema(
            description = "Integration contributing the geocoder; first id of the pair",
            example = "00a1f521-8dd4-4270-9f9d-404004e84df3")
        UUID integrationId,
    @Schema(description = "Display name of the integration", example = "Nominatim")
        String integrationLabel,
    @Schema(
            description = "Stable geocoder id, unique across integrations; second id of the pair",
            example = "680892bb-c3a8-4484-8b93-7d3deea339c0")
        UUID geocoderId,
    @Schema(description = "Display name of the geocoder", example = "Nominatim") String label,
    @Schema(
            description = "One-sentence description to show beside the label",
            example = "Places from OpenStreetMap, searched through Nominatim.")
        String description,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        """
                        Operations this geocoder supports, each served at its own path under \
                        the geocoder. Ignore values you do not recognise."""))
        List<GeocodingOperation> operations) {

  static GeocoderResponse from(GeocoderSummary summary) {
    return new GeocoderResponse(
        summary.integrationId(),
        summary.integrationLabel(),
        summary.geocoderId(),
        summary.label(),
        summary.description(),
        summary.operations());
  }
}
