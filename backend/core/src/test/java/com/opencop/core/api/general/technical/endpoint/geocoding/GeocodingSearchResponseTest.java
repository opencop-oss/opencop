// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingSearchResult;
import com.opencop.core.sdk.business.model.integration.geocoding.BoundingBox;
import com.opencop.core.sdk.business.model.integration.geocoding.GeoPoint;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import com.opencop.core.sdk.business.model.integration.geocoding.PlaceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Pins the JSON clients receive, which the UI integrates against. */
class GeocodingSearchResponseTest {

  private static final UUID INTEGRATION_ID =
      UUID.fromString("00a1f521-8dd4-4270-9f9d-404004e84df3");
  private static final UUID GEOCODER_ID = UUID.fromString("680892bb-c3a8-4484-8b93-7d3deea339c0");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void writesTheEnvelope() {
    var json = serialize(List.of("© Example contributors"), List.of(placeWithoutExtras()));

    assertThat(fieldNames(json))
        .containsExactlyInAnyOrder("integrationId", "geocoderId", "attribution", "results");
    assertThat(json.get("integrationId").asString()).isEqualTo(INTEGRATION_ID.toString());
    assertThat(json.get("geocoderId").asString()).isEqualTo(GEOCODER_ID.toString());
    assertThat(json.get("attribution").get(0).asString()).isEqualTo("© Example contributors");
  }

  @Test
  void writesAnEmptyAttributionAndNoResultsAsEmptyArrays() {
    var json = serialize(List.of(), List.of());

    assertThat(json.get("attribution").isArray()).isTrue();
    assertThat(json.get("attribution").isEmpty()).isTrue();
    assertThat(json.get("results").isArray()).isTrue();
    assertThat(json.get("results").isEmpty()).isTrue();
  }

  @Test
  void writesEveryFieldOfAFullyKnownPlace() {
    var place =
        new GeocodedPlace(
            "R42",
            "Riverside",
            "Riverside, Example County",
            PlaceType.NEIGHBOURHOOD,
            Optional.of("airport"),
            new GeoPoint(10.5, 20.25),
            Optional.of(new BoundingBox(10.0, 20.0, 11.0, 21.0)),
            List.of("© Example contributors"));

    var result = serialize(List.of(), List.of(place)).get("results").get(0);

    assertThat(fieldNames(result))
        .containsExactlyInAnyOrder(
            "id", "name", "label", "placeType", "category", "position", "bbox", "attribution");
    assertThat(result.get("id").asString()).isEqualTo("R42");
    assertThat(result.get("name").asString()).isEqualTo("Riverside");
    assertThat(result.get("label").asString()).isEqualTo("Riverside, Example County");
    assertThat(result.get("placeType").asString()).isEqualTo("neighbourhood");
    assertThat(result.get("category").asString()).isEqualTo("airport");
    assertThat(result.get("position").get("lat").asDouble()).isEqualTo(10.5);
    assertThat(result.get("position").get("lon").asDouble()).isEqualTo(20.25);
    var bbox = result.get("bbox");
    assertThat(bbox.get("minLat").asDouble()).isEqualTo(10.0);
    assertThat(bbox.get("minLon").asDouble()).isEqualTo(20.0);
    assertThat(bbox.get("maxLat").asDouble()).isEqualTo(11.0);
    assertThat(bbox.get("maxLon").asDouble()).isEqualTo(21.0);
    assertThat(result.get("attribution").get(0).asString()).isEqualTo("© Example contributors");
  }

  @Test
  void omitsCategoryAndBboxWhenAbsentButAlwaysWritesAttribution() {
    var result = serialize(List.of(), List.of(placeWithoutExtras())).get("results").get(0);

    assertThat(fieldNames(result))
        .containsExactlyInAnyOrder("id", "name", "label", "placeType", "position", "attribution");
    assertThat(result.get("placeType").asString()).isEqualTo("other");
    assertThat(result.get("attribution").isArray()).isTrue();
    assertThat(result.get("attribution").isEmpty()).isTrue();
  }

  private static GeocodedPlace placeWithoutExtras() {
    return new GeocodedPlace(
        "N7",
        "Main Street",
        "Main Street, Riverside",
        PlaceType.OTHER,
        Optional.empty(),
        new GeoPoint(-10.5, -20.25),
        Optional.empty(),
        List.of());
  }

  private JsonNode serialize(List<String> attribution, List<GeocodedPlace> places) {
    var response =
        GeocodingSearchResponse.from(
            new GeocodingSearchResult(INTEGRATION_ID, GEOCODER_ID, attribution, places));
    return objectMapper.readTree(objectMapper.writeValueAsString(response));
  }

  private static List<String> fieldNames(JsonNode node) {
    return node.propertyNames().stream().toList();
  }
}
