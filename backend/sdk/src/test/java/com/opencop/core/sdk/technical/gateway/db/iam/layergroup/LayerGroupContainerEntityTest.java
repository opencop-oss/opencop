// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerGroupContainerEntityTest {

  @Test
  void layerGroupEntity_nameAndDescriptionAndEmptyMembers() {
    LayerGroupEntity group = new LayerGroupEntity();
    group.setName("Ops");
    group.setDescription("Operations layer-group");

    assertThat(group.getName()).isEqualTo("Ops");
    assertThat(group.getDescription()).isEqualTo("Operations layer-group");
    assertThat(group.getMembers()).isEmpty();
  }

  @Test
  void layerGroupMemberEntity_constructor_setsGroupRoleAndId() {
    UUID groupId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    LayerGroupEntity group = new LayerGroupEntity();
    group.setId(groupId);

    LayerGroupMemberEntity member = new LayerGroupMemberEntity(group, userId, LayerGroupRole.ADMIN);

    assertThat(member.getGroup()).isSameAs(group);
    assertThat(member.getId().getLayerGroupId()).isEqualTo(groupId);
    assertThat(member.getId().getUserId()).isEqualTo(userId);
    assertThat(member.getRole()).isEqualTo(LayerGroupRole.ADMIN);
  }

  @Test
  void layerGroupMemberIdEntity_equalWhenBothFieldsMatch() {
    UUID groupId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    LayerGroupMemberIdEntity id1 = new LayerGroupMemberIdEntity(groupId, userId);
    LayerGroupMemberIdEntity id2 = new LayerGroupMemberIdEntity(groupId, userId);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  void layerGroupMemberIdEntity_notEqualWhenUserIdDiffers() {
    UUID groupId = UUID.randomUUID();
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    LayerGroupMemberIdEntity idA = new LayerGroupMemberIdEntity(groupId, userA);
    LayerGroupMemberIdEntity idB = new LayerGroupMemberIdEntity(groupId, userB);

    assertThat(idA).isNotEqualTo(idB);
  }

  @Test
  void layerGroupMemberIdEntity_notEqualWhenGroupIdDiffers() {
    UUID groupA = UUID.randomUUID();
    UUID groupB = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    LayerGroupMemberIdEntity idA = new LayerGroupMemberIdEntity(groupA, userId);
    LayerGroupMemberIdEntity idB = new LayerGroupMemberIdEntity(groupB, userId);

    assertThat(idA).isNotEqualTo(idB);
  }
}
