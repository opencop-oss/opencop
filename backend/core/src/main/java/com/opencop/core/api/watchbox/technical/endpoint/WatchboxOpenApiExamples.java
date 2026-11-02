// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.watchbox.technical.endpoint;

final class WatchboxOpenApiExamples {

  static final String CREATE_REQUEST =
      """
      {
        "name": "Hostile SIDC entry watchbox",
        "description": "Alert when selected hostile SIDC tracks enter or leave the sample area.",
        "direction": "BOTH",
        "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
        "polygon": {
          "type": "Polygon",
          "coordinates": [[
            [-100.2, 39.8],
            [-99.8, 39.8],
            [-99.8, 40.2],
            [-100.2, 40.2],
            [-100.2, 39.8]
          ]]
        },
        "filter": {
          "combine": "ALL",
          "predicates": [{
            "path": "properties.opencop.element.symbol",
            "operation": "SUBSTRING_IN",
            "offset": 1,
            "length": 1,
            "values": ["h"]
          }]
        },
        "enabled": true
      }
      """;

  static final String FILTER_TEST_REQUEST =
      """
      {
        "filter": {
          "combine": "ALL",
          "predicates": [{
            "path": "properties.opencop.element.symbol",
            "operation": "SUBSTRING_IN",
            "offset": 1,
            "length": 1,
            "values": ["h"]
          }]
        },
        "feature": {
          "type": "Feature",
          "properties": {
            "opencop": {
              "element": {
                "type": "symbol",
                "symbol": "shgp-----------"
              }
            }
          },
          "geometry": {
            "type": "Point",
            "coordinates": [-100.0, 40.0]
          }
        }
      }
      """;

  static final String FILTER_TEST_REQUEST_NOT_FRIENDLY =
      """
      {
        "filter": {
          "combine": "ALL",
          "predicates": [{
            "path": "properties.opencop.element.symbol",
            "operation": "SUBSTRING_IN",
            "offset": 1,
            "length": 1,
            "values": ["f"],
            "negate": true
          }]
        },
        "feature": {
          "type": "Feature",
          "properties": {
            "opencop": {
              "element": {
                "type": "symbol",
                "symbol": "sugp-----------"
              }
            }
          },
          "geometry": {
            "type": "Point",
            "coordinates": [-100.0, 40.0]
          }
        }
      }
      """;

  static final String FILTER_TEST_RESPONSE_MATCH =
      """
      {
        "matches": true,
        "predicateResults": [{
          "index": 0,
          "path": "properties.opencop.element.symbol",
          "operation": "SUBSTRING_IN",
          "negate": false,
          "matched": true
        }]
      }
      """;

  static final String FILTER_TEST_RESPONSE_NOT_FRIENDLY_MATCH =
      """
      {
        "matches": true,
        "predicateResults": [{
          "index": 0,
          "path": "properties.opencop.element.symbol",
          "operation": "SUBSTRING_IN",
          "negate": true,
          "matched": true
        }]
      }
      """;

  static final String PATCH_RESET_REQUEST =
      """
      {
        "polygon": {
          "type": "Polygon",
          "coordinates": [[
            [-100.1, 39.9],
            [-99.9, 39.9],
            [-99.9, 40.1],
            [-100.1, 40.1],
            [-100.1, 39.9]
          ]]
        },
        "confirmTrackingReset": true
      }
      """;

  static final String DEV_OVERRIDE_REQUEST =
      """
      {
        "alertingStatus": "DEGRADED",
        "alertingReasons": ["WATCHBOX_OBJECT_ID_CHURN_DETECTED"],
        "sourceStatuses": [{
          "sourceId": "550e8400-e29b-41d4-a716-446655440020",
          "sourceName": "fixture source",
          "status": "DEGRADED",
          "reason": "WATCHBOX_OBJECT_ID_CHURN_DETECTED",
          "observedCount": 1001,
          "cap": 1000
        }]
      }
      """;

  static final String WATCHBOX_CREATE_SUCCESS =
      """
      {
        "id": "550e8400-e29b-41d4-a716-446655440010",
        "name": "Hostile SIDC entry watchbox",
        "description": "Alert when selected hostile SIDC tracks enter or leave the sample area.",
        "enabled": true,
        "direction": "BOTH",
        "polygon": {
          "type": "Polygon",
          "coordinates": [[
            [-100.2, 39.8],
            [-99.8, 39.8],
            [-99.8, 40.2],
            [-100.2, 40.2],
            [-100.2, 39.8]
          ]]
        },
        "filter": {
          "combine": "ALL",
          "predicates": [{
            "path": "properties.opencop.element.symbol",
            "operation": "SUBSTRING_IN",
            "offset": 1,
            "length": 1,
            "values": ["h"]
          }]
        },
        "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
        "configEpoch": 1,
        "alertingStatus": "ACTIVE",
        "alertingReasons": [],
        "sourceStatuses": [],
        "capabilities": {"canPatch": true, "canDelete": true},
        "createdAt": "2026-06-26T20:00:00Z",
        "updatedAt": "2026-06-26T20:00:00Z"
      }
      """;

  static final String WATCHBOX_LIST =
      """
      [
        {
          "id": "550e8400-e29b-41d4-a716-446655440010",
          "name": "Sample area watchbox",
          "description": "Alert when tracked units enter or leave the sample area.",
          "enabled": true,
          "direction": "BOTH",
          "polygon": {
            "type": "Polygon",
            "coordinates": [[
              [-100.2, 39.8],
              [-99.8, 39.8],
              [-99.8, 40.2],
              [-100.2, 40.2],
              [-100.2, 39.8]
            ]]
          },
          "filter": {"predicates": []},
          "layerIds": ["550e8400-e29b-41d4-a716-446655440002"],
          "configEpoch": 1,
          "alertingStatus": "ACTIVE",
          "alertingReasons": [],
          "sourceStatuses": [],
          "capabilities": {"canPatch": false, "canDelete": false},
          "createdAt": "2026-06-26T20:00:00Z",
          "updatedAt": "2026-06-26T20:00:00Z"
        },
        {
          "id": "550e8400-e29b-41d4-a716-446655440011",
          "name": "Unstable source watchbox",
          "description": "Mapper object IDs need attention.",
          "enabled": true,
          "direction": "ENTER",
          "polygon": {
            "type": "Polygon",
            "coordinates": [[
              [-100.1, 39.9],
              [-99.9, 39.9],
              [-99.9, 40.1],
              [-100.1, 40.1],
              [-100.1, 39.9]
            ]]
          },
          "filter": {"predicates": []},
          "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
          "configEpoch": 3,
          "alertingStatus": "DEGRADED",
          "alertingReasons": ["WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"],
          "sourceStatuses": [{
            "sourceId": "550e8400-e29b-41d4-a716-446655440020",
            "sourceName": "cot-source",
            "status": "DEGRADED",
            "reason": "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
            "sinceAt": "2026-06-26T20:05:00Z",
            "observedCount": null,
            "cap": null,
            "updatedAt": "2026-06-26T20:05:00Z"
          }],
          "capabilities": {"canPatch": true, "canDelete": true},
          "createdAt": "2026-06-26T20:00:00Z",
          "updatedAt": "2026-06-26T20:05:00Z"
        },
        {
          "id": "550e8400-e29b-41d4-a716-446655440012",
          "name": "Churn-tripped watchbox",
          "description": "A source exceeded the per-epoch tracked-object safety cap.",
          "enabled": true,
          "direction": "LEAVE",
          "polygon": {
            "type": "Polygon",
            "coordinates": [[
              [-99.6, 40.4],
              [-99.4, 40.4],
              [-99.4, 40.6],
              [-99.6, 40.6],
              [-99.6, 40.4]
            ]]
          },
          "filter": {"predicates": []},
          "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
          "configEpoch": 2,
          "alertingStatus": "DEGRADED",
          "alertingReasons": ["WATCHBOX_OBJECT_ID_CHURN_DETECTED"],
          "sourceStatuses": [{
            "sourceId": "550e8400-e29b-41d4-a716-446655440021",
            "sourceName": "high-cardinality-source",
            "status": "DEGRADED",
            "reason": "WATCHBOX_OBJECT_ID_CHURN_DETECTED",
            "sinceAt": "2026-06-26T20:10:00Z",
            "observedCount": 10001,
            "cap": 10000,
            "updatedAt": "2026-06-26T20:10:00Z"
          }],
          "capabilities": {"canPatch": true, "canDelete": true},
          "createdAt": "2026-06-26T20:00:00Z",
          "updatedAt": "2026-06-26T20:10:00Z"
        }
      ]
      """;

  static final String WATCHBOX_DETAIL_WITH_SOURCE_STATUS =
      """
      {
        "id": "550e8400-e29b-41d4-a716-446655440011",
        "name": "Unstable source watchbox",
        "description": "Mapper object IDs need attention.",
        "enabled": true,
        "direction": "ENTER",
        "polygon": {
          "type": "Polygon",
          "coordinates": [[
            [-100.1, 39.9],
            [-99.9, 39.9],
            [-99.9, 40.1],
            [-100.1, 40.1],
            [-100.1, 39.9]
          ]]
        },
        "filter": {"predicates": []},
        "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
        "configEpoch": 3,
        "alertingStatus": "DEGRADED",
        "alertingReasons": ["WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN"],
        "sourceStatuses": [{
          "sourceId": "550e8400-e29b-41d4-a716-446655440020",
          "sourceName": "cot-source",
          "status": "DEGRADED",
          "reason": "WATCHBOX_SOURCE_OBJECT_ID_UNSTABLE_OR_UNKNOWN",
          "sinceAt": "2026-06-26T20:05:00Z",
          "observedCount": null,
          "cap": null,
          "updatedAt": "2026-06-26T20:05:00Z"
        }],
        "capabilities": {"canPatch": true, "canDelete": true},
        "createdAt": "2026-06-26T20:00:00Z",
        "updatedAt": "2026-06-26T20:05:00Z"
      }
      """;

  static final String DEV_OVERRIDE_RESPONSE =
      """
      {
        "id": "550e8400-e29b-41d4-a716-446655440011",
        "name": "Unstable source watchbox",
        "description": "Mapper object IDs need attention.",
        "enabled": true,
        "direction": "ENTER",
        "polygon": {
          "type": "Polygon",
          "coordinates": [[
            [-100.1, 39.9],
            [-99.9, 39.9],
            [-99.9, 40.1],
            [-100.1, 40.1],
            [-100.1, 39.9]
          ]]
        },
        "filter": {"predicates": []},
        "layerIds": ["550e8400-e29b-41d4-a716-446655440001"],
        "configEpoch": 3,
        "alertingStatus": "DEGRADED",
        "alertingReasons": ["WATCHBOX_OBJECT_ID_CHURN_DETECTED"],
        "sourceStatuses": [{
          "sourceId": "550e8400-e29b-41d4-a716-446655440020",
          "sourceName": "fixture source",
          "status": "DEGRADED",
          "reason": "WATCHBOX_OBJECT_ID_CHURN_DETECTED",
          "sinceAt": null,
          "observedCount": 1001,
          "cap": 1000,
          "updatedAt": null
        }],
        "capabilities": {"canPatch": true, "canDelete": true},
        "createdAt": "2026-06-26T20:00:00Z",
        "updatedAt": "2026-06-26T20:05:00Z"
      }
      """;

  static final String TRACKING_RESET_REQUIRED =
      """
      {
        "code": "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
        "message": "Watchbox tracking reset confirmation is required",
        "fields": ["polygon"],
        "reasons": ["POLYGON_CHANGED"],
        "context": {}
      }
      """;

  static final String FEATURE_DISABLED =
      """
      {
        "code": "WATCHBOX_FEATURE_DISABLED",
        "message": "Watchbox feature is disabled",
        "fields": [],
        "reasons": [],
        "context": {}
      }
      """;

  static final String INVALID_REQUEST =
      """
      {
        "code": "WATCHBOX_INVALID_REQUEST",
        "message": "Watchbox request body is required",
        "fields": ["body"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String INVALID_REQUEST_MALFORMED =
      """
      {
        "code": "WATCHBOX_INVALID_REQUEST",
        "message": "Invalid request body",
        "fields": ["body"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String INVALID_POLYGON =
      """
      {
        "code": "WATCHBOX_INVALID_POLYGON",
        "message": "polygon crosses itself near longitude -100.00000, latitude 40.00000",
        "fields": ["polygon"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String OBJECT_ID_ON_MISSING_NOT_ERROR =
      """
      {
        "code": "WATCHBOX_OBJECT_ID_ON_MISSING_NOT_ERROR",
        "message": "Selected sources' object ID rule must use 'Error' for On Missing",
        "fields": ["layerIds"],
        "reasons": [],
        "context": {
          "sourceNames": ["admission-source-a", "admission-source-b"]
        }
      }
      """;

  static final String INVALID_FILTER =
      """
      {
        "code": "WATCHBOX_INVALID_FILTER",
        "message": "Watchbox filter is invalid",
        "fields": ["filter.predicates[0].path"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String INVALID_NAME =
      """
      {
        "code": "WATCHBOX_INVALID_NAME",
        "message": "Watchbox name is required",
        "fields": ["name"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String INVALID_DEV_OVERRIDE =
      """
      {
        "code": "WATCHBOX_INVALID_FILTER",
        "message": "Watchbox status override is invalid",
        "fields": ["sourceStatuses[0].reason"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String MANAGEMENT_ROLE_REQUIRED =
      """
      {
        "code": "WATCHBOX_MANAGEMENT_ROLE_REQUIRED",
        "message": "Watchbox management role is required",
        "fields": ["layerIds"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String NO_ELIGIBLE_SOURCES =
      """
      {
        "code": "WATCHBOX_NO_ELIGIBLE_SOURCES",
        "message": "Selected layers do not contain eligible sources",
        "fields": ["layerIds"],
        "reasons": [],
        "context": {}
      }
      """;

  static final String NOT_FOUND =
      """
      {
        "code": "WATCHBOX_NOT_FOUND",
        "message": "Watchbox not found",
        "fields": [],
        "reasons": [],
        "context": {}
      }
      """;

  private WatchboxOpenApiExamples() {}

  public static final String DETECTION_PAGE =
      """
      {"content":[{"id":"7c9d6d84-3a2b-4a0e-9c3e-2d2b7e6c5f10", "watchboxId":"550e8400-e29b-41d4-a716-446655440010",
          "configEpoch":2, "sourceId":"550e8400-e29b-41d4-a716-446655441002", "objectId":"track-17",
          "eventId":"0b6a1f3e-8d4c-4f0a-9a1e-6b2c3d4e5f60", "effectiveEventTime":"2026-09-10T14:03:11Z",
          "kind":"ENTER", "objectDisplayName":"Truck 17", "detectedAt":"2026-09-10T14:03:12.418Z"}], "page":0, "size":25, "totalElements":1, "totalPages":1}
      """;

  public static final String DETECTION_DETAIL =
      """
      {"id":"7c9d6d84-3a2b-4a0e-9c3e-2d2b7e6c5f10", "watchboxId":"550e8400-e29b-41d4-a716-446655440010",
          "configEpoch":2, "sourceId":"550e8400-e29b-41d4-a716-446655441002", "objectId":"track-17",
          "eventId":"0b6a1f3e-8d4c-4f0a-9a1e-6b2c3d4e5f60", "effectiveEventTime":"2026-09-10T14:03:11Z",
          "kind":"ENTER", "objectDisplayName":"Truck 17", "detectedAt":"2026-09-10T14:03:12.418Z",
       "triggeringEvent":null,
       "latestEvent":{"id":"3f2c1b0a-9e8d-4c7b-a6f5-4e3d2c1b0a99", "objectId":"track-17",
         "source":"550e8400-e29b-41d4-a716-446655441002", "geometry":{"type":"Point","coordinates":[-97,32]}}}
      """;
}
