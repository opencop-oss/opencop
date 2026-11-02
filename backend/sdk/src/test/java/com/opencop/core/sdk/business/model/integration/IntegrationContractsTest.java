// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntegrationContractsTest {

  @Test
  void integrationsAndConnectionSchemasRemainOpenForExternalImplementations() {
    assertThat(Integration.class.isSealed()).isFalse();
    assertThat(IntegrationConnectionSchema.class.isSealed()).isFalse();
  }
}
