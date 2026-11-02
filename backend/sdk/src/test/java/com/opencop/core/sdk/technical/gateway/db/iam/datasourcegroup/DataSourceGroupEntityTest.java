// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataSourceGroupEntityTest {

  @Test
  void dataSourceGroupEntity_nameAndDescriptionAndEmptyMembers() {
    DataSourceGroupEntity group = new DataSourceGroupEntity();
    group.setName("Ops Sources");
    group.setDescription("Operations data-source group");

    assertThat(group.getName()).isEqualTo("Ops Sources");
    assertThat(group.getDescription()).isEqualTo("Operations data-source group");
    assertThat(group.getMembers()).isEmpty();
  }

  @Test
  void dataSourceGroupMemberEntity_constructor_setsGroupRoleAndId() {
    UUID groupId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    DataSourceGroupEntity group = new DataSourceGroupEntity();
    group.setId(groupId);

    DataSourceGroupMemberEntity member =
        new DataSourceGroupMemberEntity(group, userId, DataSourceGroupRole.ADMIN);

    assertThat(member.getGroup()).isSameAs(group);
    assertThat(member.getId().getDataSourceGroupId()).isEqualTo(groupId);
    assertThat(member.getId().getUserId()).isEqualTo(userId);
    assertThat(member.getRole()).isEqualTo(DataSourceGroupRole.ADMIN);
  }

  @Test
  void dataSourceGroupDataSourceEntity_constructor_setsSourceRoleAndId() {
    UUID groupId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    DataSourceGroupEntity group = new DataSourceGroupEntity();
    group.setId(groupId);

    DataSourceGroupDataSourceEntity entity =
        new DataSourceGroupDataSourceEntity(group, sourceId, ServerRole.ADMIN);

    assertThat(entity.getGroup()).isSameAs(group);
    assertThat(entity.getId().getDataSourceGroupId()).isEqualTo(groupId);
    assertThat(entity.getId().getDataSourceId()).isEqualTo(sourceId);
    assertThat(entity.getRole()).isEqualTo(ServerRole.ADMIN);
  }

  @Test
  void dataSourceGroupDataSourceEntity_constructor_nullRoleDefaultsToViewer() {
    DataSourceGroupEntity group = new DataSourceGroupEntity();
    group.setId(UUID.randomUUID());

    DataSourceGroupDataSourceEntity entity =
        new DataSourceGroupDataSourceEntity(group, UUID.randomUUID(), null);

    assertThat(entity.getRole()).isEqualTo(ServerRole.VIEWER);
  }

  @Test
  void dataSourceGroupMemberIdEntity_equalWhenBothFieldsMatch() {
    UUID groupId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    DataSourceGroupMemberIdEntity id1 = new DataSourceGroupMemberIdEntity(groupId, userId);
    DataSourceGroupMemberIdEntity id2 = new DataSourceGroupMemberIdEntity(groupId, userId);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  void dataSourceGroupDataSourceIdEntity_equalWhenBothFieldsMatch() {
    UUID groupId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    DataSourceGroupDataSourceIdEntity id1 =
        new DataSourceGroupDataSourceIdEntity(groupId, sourceId);
    DataSourceGroupDataSourceIdEntity id2 =
        new DataSourceGroupDataSourceIdEntity(groupId, sourceId);

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }
}
