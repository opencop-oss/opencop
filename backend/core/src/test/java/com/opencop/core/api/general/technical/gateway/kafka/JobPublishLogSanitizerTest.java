// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JobPublishLogSanitizerTest {

  private final JobPublishLogSanitizer sanitizer = new JobPublishLogSanitizer();

  @Test
  void safeKey_whenHistoricalUserKey_redactsCategory() {
    assertEquals(
        "historical-user-key", sanitizer.safeKey("USER:550e8400-e29b-41d4-a716-446655440120"));
  }

  @Test
  void safeKey_whenOtherKey_keepsKey() {
    assertEquals("layer-copy-key", sanitizer.safeKey("layer-copy-key"));
  }

  @Test
  void safeKey_whenNull_returnsNull() {
    assertNull(sanitizer.safeKey(null));
  }

  @Test
  void safePublishFailureMessage_redactsHistoricalUserKey() {
    assertEquals(
        "Failed to publish job to topic opencop.history.jobs with key historical-user-key",
        sanitizer.safePublishFailureMessage(
            "opencop.history.jobs", "USER:550e8400-e29b-41d4-a716-446655440120"));
  }
}
