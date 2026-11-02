// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LayerGroupRoleTest {

  @Test
  void ownerIsHigherThanAdmin() {
    assertThat(LayerGroupRole.OWNER.isHigherThan(LayerGroupRole.ADMIN)).isTrue();
  }

  @Test
  void adminIsHigherThanMaintainer() {
    assertThat(LayerGroupRole.ADMIN.isHigherThan(LayerGroupRole.MAINTAINER)).isTrue();
  }

  @Test
  void memberIsNotHigherThanOwner() {
    assertThat(LayerGroupRole.MEMBER.isHigherThan(LayerGroupRole.OWNER)).isFalse();
  }

  @Test
  void ownerIsAtLeastOwner() {
    assertThat(LayerGroupRole.OWNER.isAtLeast(LayerGroupRole.OWNER)).isTrue();
  }

  @Test
  void adminIsAtLeastMaintainer() {
    assertThat(LayerGroupRole.ADMIN.isAtLeast(LayerGroupRole.MAINTAINER)).isTrue();
  }

  @Test
  void memberIsNotAtLeastMaintainer() {
    assertThat(LayerGroupRole.MEMBER.isAtLeast(LayerGroupRole.MAINTAINER)).isFalse();
  }

  @Test
  void memberIsLowerThanOwner() {
    assertThat(LayerGroupRole.MEMBER.isLowerThan(LayerGroupRole.OWNER)).isTrue();
  }

  @Test
  void ownerIsNotLowerThanMember() {
    assertThat(LayerGroupRole.OWNER.isLowerThan(LayerGroupRole.MEMBER)).isFalse();
  }

  @Test
  void fourValuesExist() {
    LayerGroupRole[] values = LayerGroupRole.values();
    assertThat(values)
        .containsExactly(
            LayerGroupRole.OWNER,
            LayerGroupRole.ADMIN,
            LayerGroupRole.MAINTAINER,
            LayerGroupRole.MEMBER);
  }
}
