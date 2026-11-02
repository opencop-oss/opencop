// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.extension;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.business.model.integration.connection.extension.AccessLevel;
import org.junit.jupiter.api.Test;

class ConnectionExtensionAccessPolicyTest {

  @Test
  void anyoneWhoSeesTheConnectionMayRead() {
    assertThat(ConnectionExtensionAccessPolicy.isInvocable(AccessLevel.READ, false)).isTrue();
    assertThat(ConnectionExtensionAccessPolicy.requiresAdministration(AccessLevel.READ)).isFalse();
  }

  @Test
  void onlyWhoAdministersTheConnectionMayManage() {
    assertThat(ConnectionExtensionAccessPolicy.isInvocable(AccessLevel.MANAGE, false)).isFalse();
    assertThat(ConnectionExtensionAccessPolicy.isInvocable(AccessLevel.MANAGE, true)).isTrue();
    assertThat(ConnectionExtensionAccessPolicy.requiresAdministration(AccessLevel.MANAGE)).isTrue();
  }
}
