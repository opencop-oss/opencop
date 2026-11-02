// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.api.general.ports.usecase.geocoding.GeocodingExceptionCodes;
import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import com.opencop.core.sdk.ports.exception.GlobalErrorResponse;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Annotations compile whatever they claim, so these pin the published examples to the contract they
 * document: the response records and the geocoding error codes.
 */
class GeocodingOpenApiExamplesTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void everyPublishedExampleIsValidJson() throws Exception {
    for (var field : GeocodingOpenApiExamples.class.getDeclaredFields()) {
      if (field.getType() == String.class && Modifier.isStatic(field.getModifiers())) {
        assertThat(objectMapper.readValue((String) field.get(null), Object.class))
            .as(field.getName())
            .isNotNull();
      }
    }
  }

  @Test
  void theDiscoveryExampleDeserializesIntoTheResponseItDocuments() {
    var geocoders =
        objectMapper.readValue(
            GeocodingOpenApiExamples.GEOCODERS_RESPONSE,
            new TypeReference<List<GeocoderResponse>>() {});

    assertThat(geocoders)
        .singleElement()
        .satisfies(
            geocoder -> {
              assertThat(geocoder.integrationId()).isNotNull();
              assertThat(geocoder.geocoderId()).isNotNull();
              assertThat(geocoder.operations()).containsExactly(GeocodingOperation.SEARCH);
            });
  }

  @Test
  void theSearchExamplesDeserializeIntoTheResponseTheyDocument() {
    var found =
        objectMapper.readValue(
            GeocodingOpenApiExamples.SEARCH_RESPONSE, GeocodingSearchResponse.class);
    var empty =
        objectMapper.readValue(
            GeocodingOpenApiExamples.EMPTY_SEARCH_RESPONSE, GeocodingSearchResponse.class);

    assertThat(found.attribution()).isNotEmpty();
    assertThat(found.results())
        .singleElement()
        .satisfies(
            place -> {
              assertThat(place.placeType()).isEqualTo("locality");
              assertThat(place.position()).isNotNull();
              assertThat(place.bbox()).isNotNull();
              assertThat(place.attribution()).containsExactlyElementsOf(found.attribution());
            });
    assertThat(empty.results()).isEmpty();
    assertThat(empty.attribution()).isEmpty();
  }

  @Test
  void everyGeocodingErrorCodeIsDocumentedByExactlyOneExample() {
    var documented =
        Stream.of(
                GeocodingOpenApiExamples.INVALID_REQUEST_RESPONSE,
                GeocodingOpenApiExamples.GEOCODER_NOT_FOUND_RESPONSE,
                GeocodingOpenApiExamples.PROVIDER_FAILED_RESPONSE,
                GeocodingOpenApiExamples.PROVIDER_TIMED_OUT_RESPONSE,
                GeocodingOpenApiExamples.PROVIDER_RATE_LIMITED_RESPONSE,
                GeocodingOpenApiExamples.TOO_MANY_REQUESTS_RESPONSE)
            .map(example -> objectMapper.readValue(example, GlobalErrorResponse.class))
            .toList();

    assertThat(documented)
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(GeocodingExceptionCodes.values())
                .map(code -> new GlobalErrorResponse(code.code(), code.description()))
                .toList());
  }
}
