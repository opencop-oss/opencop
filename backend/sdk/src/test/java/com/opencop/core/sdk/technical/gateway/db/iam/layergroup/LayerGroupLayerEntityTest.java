// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.layergroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.iam.LayerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.layer.LayerEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LayerGroupLayerEntityTest {

  @Test
  void constructor_setsGroupLayerRoleAndCompositeId() {
    UUID groupId = UUID.randomUUID();
    UUID layerId = UUID.randomUUID();

    LayerGroupEntity group = new LayerGroupEntity();
    group.setId(groupId);

    LayerEntity layer = new LayerEntity();
    layer.setId(layerId);

    LayerGroupLayerEntity entity = new LayerGroupLayerEntity(group, layer, LayerRole.EDITOR);

    assertThat(entity.getGroup()).isSameAs(group);
    assertThat(entity.getLayer()).isSameAs(layer);
    assertThat(entity.getId().getLayerGroupId()).isEqualTo(groupId);
    assertThat(entity.getId().getLayerId()).isEqualTo(layerId);
    assertThat(entity.getRole()).isEqualTo(LayerRole.EDITOR);
  }

  @Test
  void constructor_nullRole_defaultsToMember() {
    LayerGroupEntity group = new LayerGroupEntity();
    group.setId(UUID.randomUUID());

    LayerEntity layer = new LayerEntity();
    layer.setId(UUID.randomUUID());

    LayerGroupLayerEntity entity = new LayerGroupLayerEntity(group, layer, null);

    assertThat(entity.getRole()).isEqualTo(LayerRole.MEMBER);
  }

  @Test
  void layerGroupLayerIdEntity_notEqualWhenLayerGroupIdDiffers() {
    UUID groupA = UUID.randomUUID();
    UUID groupB = UUID.randomUUID();
    UUID layerId = UUID.randomUUID();

    LayerGroupLayerIdEntity idA = new LayerGroupLayerIdEntity(groupA, layerId);
    LayerGroupLayerIdEntity idB = new LayerGroupLayerIdEntity(groupB, layerId);

    assertThat(idA).isNotEqualTo(idB);
  }

  @Test
  void layerGroupLayerIdEntity_notEqualWhenLayerIdDiffers() {
    UUID groupId = UUID.randomUUID();
    UUID layerA = UUID.randomUUID();
    UUID layerB = UUID.randomUUID();

    LayerGroupLayerIdEntity idA = new LayerGroupLayerIdEntity(groupId, layerA);
    LayerGroupLayerIdEntity idB = new LayerGroupLayerIdEntity(groupId, layerB);

    assertThat(idA).isNotEqualTo(idB);
  }
}
