// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

final class IntegrationConnectionSetupOpenApiExamples {

  static final String CONNECTION_SETUP_RESPONSE =
      """
      {
        "integrationId": "8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20",
        "schemaVersion": 2,
        "protocols": [
          {
            "kind": "TCP",
            "accessModes": ["R", "W", "RW"],
            "extractors": [
              {
                "id": "3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91",
                "label": "Signed bundle",
                "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91.label",
                "fields": [
                  {
                    "name": "host",
                    "label": "Host",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91.fields.host",
                    "required": true,
                    "kind": "TEXT",
                    "standardKey": "HOST"
                  },
                  {
                    "name": "passphrase",
                    "label": "Passphrase",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91.fields.passphrase",
                    "required": true,
                    "kind": "SECRET"
                  },
                  {
                    "name": "bundle",
                    "label": "Signed bundle",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91.fields.bundle",
                    "required": true,
                    "kind": "FILE",
                    "extensions": [".p12"],
                    "mediaTypes": ["application/x-pkcs12"]
                  },
                  {
                    "name": "anchors",
                    "label": "Trust anchors",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.3f1b8a22-7c4d-4e91-8b03-2a5c6d7e8f91.fields.anchors",
                    "required": false,
                    "kind": "FILE_LIST",
                    "extensions": [".pem"],
                    "mediaTypes": ["application/x-pem-file"]
                  }
                ]
              }
            ],
            "ingress": [
              {
                "type": "EXACT_PAIR",
                "mapperId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
                "framingId": "9f8e7d6c-5b4a-4392-8170-6e5d4c3b2a19"
              },
              {"type": "ANY_MAPPER_WITH", "framingId": "2b3c4d5e-6f70-4182-93a4-b5c6d7e8f901"},
              {"type": "ANY_MAPPER_CUSTOM_FRAMING"}
            ],
            "egress": {
              "anyMapper": false,
              "allowedMappers": ["4d5e6f70-8192-4a3b-b5c6-d7e8f9012a3b"]
            },
            "framings": [
              {"id": "2b3c4d5e-6f70-4182-93a4-b5c6d7e8f901", "mode": "passthrough"},
              {"id": "9f8e7d6c-5b4a-4392-8170-6e5d4c3b2a19", "mode": "delimiterInclude"}
            ]
          },
          {
            "kind": "UDP",
            "accessModes": ["R"],
            "extractors": [
              {
                "id": "5d2c9b33-8e5f-4a02-9c14-3b6d7e8f9a02",
                "label": "Endpoint",
                "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.5d2c9b33-8e5f-4a02-9c14-3b6d7e8f9a02.label",
                "fields": [
                  {
                    "name": "host",
                    "label": "Host",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.5d2c9b33-8e5f-4a02-9c14-3b6d7e8f9a02.fields.host",
                    "required": true,
                    "kind": "TEXT",
                    "standardKey": "HOST"
                  },
                  {
                    "name": "port",
                    "label": "Port",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.5d2c9b33-8e5f-4a02-9c14-3b6d7e8f9a02.fields.port",
                    "required": true,
                    "kind": "TEXT",
                    "standardKey": "PORT"
                  }
                ]
              }
            ],
            "ingress": [{"type": "ANY_MAPPER"}]
          },
          {
            "kind": "RTSP",
            "accessModes": ["R"],
            "extractors": [
              {
                "id": "6e3dac44-9f60-4b13-8d25-4c7e8f9a0b13",
                "label": "Stream",
                "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.6e3dac44-9f60-4b13-8d25-4c7e8f9a0b13.label",
                "fields": [
                  {
                    "name": "streamUrl",
                    "label": "Stream URL",
                    "labelKey": "integrations.8a1f0c4e-2d3b-4a5c-9e6f-7b8c9d0e1f20.extractors.6e3dac44-9f60-4b13-8d25-4c7e8f9a0b13.fields.streamUrl",
                    "required": true,
                    "kind": "TEXT"
                  }
                ]
              }
            ],
            "ingress": [{"type": "MAPPER", "mapperId": "5e6f7081-92a3-4b4c-86d7-e8f9012a3b4c"}]
          }
        ]
      }
      """;

  static final String NOT_FOUND_RESPONSE =
      """
      {
        "code": "INTEGRATION_NOT_FOUND",
        "message": "No such integration schema version"
      }
      """;

  static final String INACTIVE_RESPONSE =
      """
      {
        "code": "INTEGRATION_INACTIVE",
        "message": "Integration is inactive"
      }
      """;

  static final String UNAVAILABLE_RESPONSE =
      """
      {
        "code": "INTEGRATION_UNAVAILABLE",
        "message": "Integration is not available in this runtime"
      }
      """;

  static final String PROTOCOL_UNSUPPORTED_RESPONSE =
      """
      {
        "code": "INTEGRATION_PROTOCOL_UNSUPPORTED",
        "message": "Integration declares no connection setup"
      }
      """;

  private IntegrationConnectionSetupOpenApiExamples() {}
}
