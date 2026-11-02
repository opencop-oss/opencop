// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import org.springframework.stereotype.Component;

@Component
public class JobPublishLogSanitizer {

  private static final String HISTORICAL_USER_KEY_PREFIX = "USER:";
  private static final String HISTORICAL_USER_KEY_CATEGORY = "historical-user-key";
  private static final String PUBLISH_FAILURE_TEMPLATE =
      "Failed to publish job to topic %s with key %s";

  public String safeKey(String key) {
    if (key != null && key.startsWith(HISTORICAL_USER_KEY_PREFIX)) {
      return HISTORICAL_USER_KEY_CATEGORY;
    }
    return key;
  }

  public String safePublishFailureMessage(String topic, String key) {
    return PUBLISH_FAILURE_TEMPLATE.formatted(topic, safeKey(key));
  }
}
