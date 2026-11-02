// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationConnectionSchemaReferenceTest {

  @Test
  void requiresAnIntegrationIdAndPositiveSchemaVersion() {
    assertThatNullPointerException()
        .isThrownBy(() -> new IntegrationConnectionSchemaReference(null, 1))
        .withMessage("integrationId");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new IntegrationConnectionSchemaReference(UUID.randomUUID(), 0))
        .withMessage("schemaVersion must be positive");
  }
}
