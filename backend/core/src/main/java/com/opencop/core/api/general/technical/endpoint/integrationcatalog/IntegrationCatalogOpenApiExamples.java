// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

/** Literal payloads shown in the generated API documentation for the catalog listing. */
final class IntegrationCatalogOpenApiExamples {

  static final String CATALOG_RESPONSE =
      """
      [
        {
          "integrationId": "8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20",
          "schemaVersion": 1,
          "name": "Streaming feed",
          "description": "Streams over a persistent socket.",
          "nameKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.name",
          "descriptionKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.description",
          "available": true
        },
        {
          "integrationId": "8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20",
          "schemaVersion": 2,
          "name": "Streaming feed",
          "description": "Streams over a persistent socket.",
          "nameKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.name",
          "descriptionKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.description",
          "available": false
        }
      ]""";

  static final String UNKNOWN_PROTOCOL_RESPONSE =
      """
      {
        "code": "UNKNOWN_TRANSPORT_PROTOCOL",
        "message": "Unknown transport protocol 'SCTP'. Accepted values are TCP, UDP, RTSP, KAFKA."
      }""";

  private IntegrationCatalogOpenApiExamples() {}
}
