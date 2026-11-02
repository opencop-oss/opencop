// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.sdk.technical.gateway.db.iam.group.GroupEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerGroupUserGroupEntityTest {

  @Test
  void constructor_setsGroupUserGroupRoleAndCompositeId() {
    UUID layerGroupId = UUID.randomUUID();
    UUID userGroupId = UUID.randomUUID();

    LayerGroupEntity layerGroup = new LayerGroupEntity();
    layerGroup.setId(layerGroupId);

    GroupEntity userGroup = new GroupEntity();
    userGroup.setId(userGroupId);

    LayerGroupUserGroupEntity entity =
        new LayerGroupUserGroupEntity(layerGroup, userGroup, LayerGroupRole.MAINTAINER);

    assertThat(entity.getLayerGroup()).isSameAs(layerGroup);
    assertThat(entity.getUserGroup()).isSameAs(userGroup);
    assertThat(entity.getId().getLayerGroupId()).isEqualTo(layerGroupId);
    assertThat(entity.getId().getUserGroupId()).isEqualTo(userGroupId);
    assertThat(entity.getRole()).isEqualTo(LayerGroupRole.MAINTAINER);
  }

  @Test
  void compositeId_equalWhenBothFieldsMatch() {
    UUID layerGroupId = UUID.randomUUID();
    UUID userGroupId = UUID.randomUUID();

    LayerGroupUserGroupIdEntity id1 = new LayerGroupUserGroupIdEntity(layerGroupId, userGroupId);
    LayerGroupUserGroupIdEntity id2 = new LayerGroupUserGroupIdEntity(layerGroupId, userGroupId);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  void compositeId_notEqualWhenUserGroupIdDiffers() {
    UUID layerGroupId = UUID.randomUUID();
    UUID ugA = UUID.randomUUID();
    UUID ugB = UUID.randomUUID();

    LayerGroupUserGroupIdEntity idA = new LayerGroupUserGroupIdEntity(layerGroupId, ugA);
    LayerGroupUserGroupIdEntity idB = new LayerGroupUserGroupIdEntity(layerGroupId, ugB);

    assertThat(idA).isNotEqualTo(idB);
  }

  @Test
  void compositeId_notEqualWhenLayerGroupIdDiffers() {
    UUID lgA = UUID.randomUUID();
    UUID lgB = UUID.randomUUID();
    UUID userGroupId = UUID.randomUUID();

    LayerGroupUserGroupIdEntity idA = new LayerGroupUserGroupIdEntity(lgA, userGroupId);
    LayerGroupUserGroupIdEntity idB = new LayerGroupUserGroupIdEntity(lgB, userGroupId);

    assertThat(idA).isNotEqualTo(idB);
  }

  @Test
  void repository_findAllByLayerGroupId_returnsEntitiesForGivenGroup() {
    UUID layerGroupId = UUID.randomUUID();
    UUID userGroupId = UUID.randomUUID();

    LayerGroupEntity layerGroup = new LayerGroupEntity();
    layerGroup.setId(layerGroupId);

    GroupEntity userGroup = new GroupEntity();
    userGroup.setId(userGroupId);

    LayerGroupUserGroupEntity entity =
        new LayerGroupUserGroupEntity(layerGroup, userGroup, LayerGroupRole.MAINTAINER);

    IamLayerGroupUserGroupRepository repository = mock(IamLayerGroupUserGroupRepository.class);
    when(repository.findAllByLayerGroupId(layerGroupId)).thenReturn(List.of(entity));

    List<LayerGroupUserGroupEntity> result = repository.findAllByLayerGroupId(layerGroupId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRole()).isEqualTo(LayerGroupRole.MAINTAINER);
    assertThat(result.get(0).getId().getLayerGroupId()).isEqualTo(layerGroupId);
    assertThat(result.get(0).getId().getUserGroupId()).isEqualTo(userGroupId);
  }

  @Test
  void repository_findLayerGroupIdsByUserGroupIdIn_returnsCorrectGroupIds() {
    UUID lg1 = UUID.randomUUID();
    UUID lg2 = UUID.randomUUID();
    UUID lg3 = UUID.randomUUID();
    UUID ug1 = UUID.randomUUID();
    UUID ug2 = UUID.randomUUID();

    IamLayerGroupUserGroupRepository repository = mock(IamLayerGroupUserGroupRepository.class);
    when(repository.findLayerGroupIdsByUserGroupIdIn(Set.of(ug1))).thenReturn(Set.of(lg1, lg2));

    Set<UUID> result = repository.findLayerGroupIdsByUserGroupIdIn(Set.of(ug1));

    assertThat(result).containsExactlyInAnyOrder(lg1, lg2);
    assertThat(result).doesNotContain(lg3);
  }
}
