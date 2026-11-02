// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.geocoding;

final class GeocodingOpenApiExamples {

  static final String GEOCODERS_RESPONSE =
      """
      [
        {
          "integrationId": "00a1f521-8dd4-4270-9f9d-404004e84df3",
          "integrationLabel": "Nominatim",
          "geocoderId": "680892bb-c3a8-4484-8b93-7d3deea339c0",
          "label": "Nominatim",
          "description": "Places from OpenStreetMap, searched through Nominatim.",
          "operations": ["SEARCH"]
        }
      ]
      """;

  static final String NO_GEOCODERS_RESPONSE = "[]";

  static final String SEARCH_RESPONSE =
      """
      {
        "integrationId": "00a1f521-8dd4-4270-9f9d-404004e84df3",
        "geocoderId": "680892bb-c3a8-4484-8b93-7d3deea339c0",
        "attribution": ["© OpenStreetMap contributors"],
        "results": [
          {
            "id": "R2988933",
            "name": "朝阳区",
            "label": "朝阳区, 北京市, 中国",
            "placeType": "locality",
            "position": {"lat": 39.9215, "lon": 116.4435},
            "bbox": {"minLat": 39.8143, "minLon": 116.3425, "maxLat": 40.1105, "maxLon": 116.6435},
            "attribution": ["© OpenStreetMap contributors"]
          }
        ]
      }
      """;

  static final String EMPTY_SEARCH_RESPONSE =
      """
      {
        "integrationId": "00a1f521-8dd4-4270-9f9d-404004e84df3",
        "geocoderId": "680892bb-c3a8-4484-8b93-7d3deea339c0",
        "attribution": [],
        "results": []
      }
      """;

  static final String INVALID_REQUEST_RESPONSE =
      """
      {
        "code": "GEOCODING-1000",
        "message": "Invalid geocoding request"
      }
      """;

  static final String GEOCODER_NOT_FOUND_RESPONSE =
      """
      {
        "code": "GEOCODING-1001",
        "message": "Geocoder not found"
      }
      """;

  static final String PROVIDER_FAILED_RESPONSE =
      """
      {
        "code": "GEOCODING-1002",
        "message": "The geocoding provider could not answer"
      }
      """;

  static final String PROVIDER_TIMED_OUT_RESPONSE =
      """
      {
        "code": "GEOCODING-1003",
        "message": "The geocoding provider did not answer in time"
      }
      """;

  static final String PROVIDER_RATE_LIMITED_RESPONSE =
      """
      {
        "code": "GEOCODING-1004",
        "message": "The geocoding provider is refusing requests for now"
      }
      """;

  static final String TOO_MANY_REQUESTS_RESPONSE =
      """
      {
        "code": "GEOCODING-1005",
        "message": "Too many geocoding requests; slow down"
      }
      """;

  private GeocodingOpenApiExamples() {}
}
