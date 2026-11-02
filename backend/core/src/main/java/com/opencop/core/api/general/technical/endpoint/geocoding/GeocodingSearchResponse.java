// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingSearchResult;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The search envelope: the geocoder that answered, the places it found and their credits, each
 * listed once.
 */
@Schema(
    name = "GeocodingSearchResponse",
    description = "The places a geocoder found, each with the attribution clients must display.")
record GeocodingSearchResponse(
    @Schema(description = "Integration owning the geocoder that answered") UUID integrationId,
    @Schema(description = "Geocoder that answered") UUID geocoderId,
    @ArraySchema(
            arraySchema =
                @Schema(
                    description =
                        """
                        Every result's `attribution`, each credit once, in the order the results \
                        first name it. Show it wherever the result list is displayed. Plain text, \
                        never HTML. Empty when there are no results or none require credit, never \
                        absent."""),
            schema = @Schema(example = "© OpenStreetMap contributors"))
        List<String> attribution,
    @ArraySchema(
            arraySchema = @Schema(description = "Places found, best match first; may be empty"))
        List<PlaceResponse> results) {

  static GeocodingSearchResponse from(GeocodingSearchResult result) {
    return new GeocodingSearchResponse(
        result.integrationId(),
        result.geocoderId(),
        result.attribution(),
        result.places().stream().map(PlaceResponse::from).toList());
  }

  /** Omits {@code category} and {@code bbox} when absent; {@code placeType} is lowercase. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "GeocodedPlace", description = "A place a geocoder found.")
  record PlaceResponse(
      @Schema(
              description = "Provider-scoped place id, stable across provider data refreshes",
              example = "R2988933")
          String id,
      @Schema(description = "Short name of the place", example = "朝阳区") String name,
      @Schema(
              description = "Full name with its enclosing areas, for display in a result list",
              example = "朝阳区, 北京市, 中国")
          String label,
      @Schema(
              description =
                  """
                  Kind of place, from the Who's On First / Pelias vocabulary. Treat an \
                  unknown value as `other`.""",
              allowableValues = {
                "country",
                "region",
                "county",
                "locality",
                "neighbourhood",
                "postalcode",
                "street",
                "address",
                "venue",
                "other"
              },
              example = "locality")
          String placeType,
      @Schema(
              description =
                  """
                  Finer category from the Overture Maps `basic_category` vocabulary, such as \
                  `airport` or `hospital`. Omitted when unknown; ignore unrecognised values.""",
              example = "airport")
          String category,
      @Schema(description = "Point to centre the map on") PositionResponse position,
      @Schema(
              description =
                  """
                  Extent to fit the map to, when the provider knows it; omitted otherwise. \
                  `minLon` greater than `maxLon` means the box crosses the antimeridian.""")
          BoundingBoxResponse bbox,
      @ArraySchema(
              arraySchema =
                  @Schema(
                      description =
                          """
                          Credits the provider's terms or this place's data licence require to be \
                          shown wherever this place is displayed. Plain text, never HTML. May be \
                          empty, never absent."""),
              schema = @Schema(example = "© OpenStreetMap contributors"))
          List<String> attribution) {

    static PlaceResponse from(GeocodedPlace place) {
      return new PlaceResponse(
          place.id(),
          place.name(),
          place.label(),
          place.placeType().name().toLowerCase(Locale.ROOT),
          place.category().orElse(null),
          PositionResponse.from(place.position()),
          place.bbox().map(BoundingBoxResponse::from).orElse(null),
          place.attribution());
    }
  }

  @Schema(name = "GeoPoint", description = "A WGS84 position.")
  record PositionResponse(
      @Schema(description = "Latitude in degrees, -90 to 90", example = "39.9215") double lat,
      @Schema(description = "Longitude in degrees, -180 to 180", example = "116.4435") double lon) {

    static PositionResponse from(GeoPoint point) {
      return new PositionResponse(point.lat(), point.lon());
    }
  }

  @Schema(name = "GeoBoundingBox", description = "A WGS84 bounding box, in degrees.")
  record BoundingBoxResponse(
      @Schema(description = "Southern edge", example = "39.8143") double minLat,
      @Schema(description = "Western edge", example = "116.3425") double minLon,
      @Schema(description = "Northern edge", example = "40.1105") double maxLat,
      @Schema(description = "Eastern edge", example = "116.6435") double maxLon) {

    static BoundingBoxResponse from(BoundingBox box) {
      return new BoundingBoxResponse(box.minLat(), box.minLon(), box.maxLat(), box.maxLon());
    }
  }
}
