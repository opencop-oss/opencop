// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.runtimefeature;

final class RuntimeFeatureOpenApiExamples {

  static final String DISABLE_RTSP_REQUEST =
      """
      {
        "audience": {"mode": "NONE"}
      }
      """;

  static final String ENABLE_WATCHBOX_FOR_ALL_REQUEST =
      """
      {
        "audience": {"mode": "ALL"},
        "expectedRevision": 2
      }
      """;

  static final String ENABLE_WATCHBOX_FOR_USER_GROUPS_REQUEST =
      """
      {
        "audience": {
          "mode": "USER_GROUPS",
          "userGroupIds": ["11111111-1111-4111-8111-111111111111"]
        },
        "expectedRevision": 2
      }
      """;

  static final String REGISTER_REQUEST =
      """
      {
        "name": "themes-page"
      }
      """;

  static final String RECONCILE_REQUEST =
      """
      {
        "names": ["themes-page", "plugin-loader", "rtsp"]
      }
      """;

  static final String RECONCILE_PARTIAL_RESPONSE =
      """
      {
        "created": [
          {
            "name": "themes-page",
            "consumerClass": "FRONTEND_ONLY",
            "audience": {"mode": "NONE"},
            "revision": 0,
            "updatedAt": "2026-08-11T17:30:00Z"
          }
        ],
        "alreadyRegistered": [
          {
            "name": "rtsp",
            "consumerClass": "FRONTEND_ONLY",
            "audience": {"mode": "ALL"},
            "revision": 1,
            "updatedAt": "2026-08-10T14:22:00Z"
          }
        ],
        "rejected": [
          {
            "name": "plugin-loader",
            "code": "RUNTIME_FEATURE_CLASS_CONFLICT",
            "message": "Runtime feature is already registered as BACKEND_ENFORCED: plugin-loader"
          }
        ],
        "complete": false
      }
      """;

  static final String INVALID_REQUEST_RESPONSE =
      """
      {
        "code": "RUNTIME_FEATURE_INVALID_REQUEST",
        "message": "Invalid runtime feature replacement request"
      }
      """;

  static final String UNPARSEABLE_REQUEST_RESPONSE =
      """
      {
        "code": "INVALID_REQUEST",
        "message": "Invalid request body"
      }
      """;

  private RuntimeFeatureOpenApiExamples() {}
}
