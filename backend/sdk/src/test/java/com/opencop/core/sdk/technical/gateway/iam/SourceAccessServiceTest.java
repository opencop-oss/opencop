// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.opencop.core.sdk.model.iam.source.AccessVia;
import com.opencop.core.sdk.technical.gateway.db.FlywayGeoFeaturesPlaceholders;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.testing.integration.NativeIntegrationFixture;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

@SpringJUnitConfig(SourceAccessServiceTest.Config.class)
class SourceAccessServiceTest {

  private static final Map<String, String> FLYWAY_PLACEHOLDERS =
      FlywayGeoFeaturesPlaceholders.defaultPlaceholders();

  private final NamedParameterJdbcTemplate jdbc;
  private final SourceAccessService sourceAccessService;

  @Autowired
  SourceAccessServiceTest(
      NamedParameterJdbcTemplate jdbc, SourceAccessService sourceAccessService) {
    this.jdbc = jdbc;
    this.sourceAccessService = sourceAccessService;
  }

  @Test
  void userHasAccess_returnsFalse_whenOnlyLayerGrantIsArchived() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "archived-only-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);
    var archivedBy = UUID.randomUUID();
    insertUser(archivedBy);
    archiveLayer(layerId, archivedBy);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsFalse_whenOnlyLayerGrantIsPendingMerge() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "pending-merge-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);
    updateLayerLifecycle(layerId, "PENDING_MERGE");

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsTrue_whenLayerGrantIsActive() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "active-only-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
  }

  @Test
  void userRole_returnsNull_whenOnlyLayerGrantIsMergeSourceLocked() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "locked-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);
    updateLayerLifecycle(layerId, "MERGE_SOURCE_LOCKED");

    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRole_returnsViewer_whenLayerGrantIsActive() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "active-role-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.VIEWER, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.VIEWER, AccessVia.INDIRECT);
  }

  @Test
  void userRole_returnsGroupSourceRole_forLayerEmbeddedDataSourceGroup() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertLayer(layerId, "embedded-source-group-layer");
    insertLayerMember(layerId, userId);
    insertDataSourceGroup(groupId, "embedded-source-group");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, groupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userRole_returnsGroupSourceRole_forGlobalLayerEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerDataSourceGroupChain("global-layer-dsg");

    assertTrue(sourceAccessService.userHasAccess(chain.userId(), chain.sourceId()));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(chain.userId(), chain.sourceId()));
    assertUserRoleMatchesListing(
        chain.userId(), chain.sourceId(), ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerDataSourceGroupEmbeddingIsRemovedForGlobalLayer() {
    var chain = insertGlobalLayerDataSourceGroupChain("removed-global-layer-dsg");

    removeLayerDataSourceGroup(chain.layerId(), chain.dataSourceGroupId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void
      userHasAccess_returnsFalse_afterDataSourceGroupDataSourceAssignmentIsRemovedForGlobalLayer() {
    var chain = insertGlobalLayerDataSourceGroupChain("removed-global-layer-dsg-source");

    removeDataSourceGroupDataSource(chain.dataSourceGroupId(), chain.sourceId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_whenLayerIsArchivedForGlobalLayerEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerDataSourceGroupChain("archived-global-layer-dsg");
    var archivedBy = UUID.randomUUID();
    insertUser(archivedBy);

    archiveLayer(chain.layerId(), archivedBy);

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_whenGlobalLayerBecomesPrivateForEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerDataSourceGroupChain("private-global-layer-dsg");

    updateLayerAccessScope(chain.layerId(), "PRIVATE");

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userRole_returnsGroupSourceRole_forDirectLayerGroupMemberToEmbeddedDataSourceGroup() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertLayer(layerId, "layer-group-embedded-source-group-layer");
    insertLayerGroup(layerGroupId, "direct-layer-group");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "direct-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userRole_returnsGroupSourceRole_forUserGroupLayerGroupToEmbeddedDataSourceGroup() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertUserGroup(userGroupId, "layer-group-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertLayer(layerId, "user-group-layer-group-embedded-source-group-layer");
    insertLayerGroup(layerGroupId, "user-group-layer-group");
    insertLayerGroupUserGroup(layerGroupId, userGroupId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "user-group-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userHasAccess_returnsFalse_afterUserGroupMembershipIsRemovedForUserGroupLayerGroupChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("removed-user-group-member");

    removeUserGroupMember(chain.userGroupId(), chain.userId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerGroupUserGroupAssignmentIsRemovedForChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("removed-layer-group-user-group");

    removeLayerGroupUserGroup(chain.layerGroupId(), chain.userGroupId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerGroupLayerAssignmentIsRemovedForUserGroupChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("removed-layer-group-layer");

    removeLayerGroupLayer(chain.layerGroupId(), chain.layerId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerDataSourceGroupEmbeddingIsRemovedForUserGroupChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("removed-layer-dsg");

    removeLayerDataSourceGroup(chain.layerId(), chain.dataSourceGroupId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void
      userHasAccess_returnsFalse_afterDataSourceGroupDataSourceAssignmentIsRemovedForUserGroupChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("removed-dsg-source");

    removeDataSourceGroupDataSource(chain.dataSourceGroupId(), chain.sourceId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_whenLayerIsArchivedForUserGroupLayerGroupChain() {
    var chain = insertUserGroupLayerGroupDataSourceGroupChain("archived-user-group-layer-group");
    var archivedBy = UUID.randomUUID();
    insertUser(archivedBy);

    archiveLayer(chain.layerId(), archivedBy);

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userRole_returnsGroupSourceRole_forGlobalLayerGroupToEmbeddedDataSourceGroup() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, "global-layer-group-embedded-source-group-layer");
    insertLayerGroup(layerGroupId, "global-layer-group", "GLOBAL");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "global-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userHasAccess_returnsFalse_afterGlobalLayerGroupIsRemovedForEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerGroupDataSourceGroupChain("removed-global-layer-group");

    removeLayerGroup(chain.layerGroupId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void
      userHasAccess_returnsFalse_afterGlobalLayerGroupLayerAssignmentIsRemovedForEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerGroupDataSourceGroupChain("removed-global-layer-group-layer");

    removeLayerGroupLayer(chain.layerGroupId(), chain.layerId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerDataSourceGroupEmbeddingIsRemovedForGlobalLayerGroup() {
    var chain = insertGlobalLayerGroupDataSourceGroupChain("removed-global-layer-dsg");

    removeLayerDataSourceGroup(chain.layerId(), chain.dataSourceGroupId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void
      userHasAccess_returnsFalse_afterDataSourceGroupDataSourceAssignmentIsRemovedForGlobalLayerGroup() {
    var chain = insertGlobalLayerGroupDataSourceGroupChain("removed-global-dsg-source");

    removeDataSourceGroupDataSource(chain.dataSourceGroupId(), chain.sourceId());

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userHasAccess_returnsFalse_whenLayerIsArchivedForGlobalLayerGroupEmbeddedDataSourceGroup() {
    var chain = insertGlobalLayerGroupDataSourceGroupChain("archived-global-layer-group");
    var archivedBy = UUID.randomUUID();
    insertUser(archivedBy);

    archiveLayer(chain.layerId(), archivedBy);

    assertNoSourceAccess(chain.userId(), chain.sourceId());
  }

  @Test
  void userRole_returnsHighestRoleAcrossLayerGroupEmbeddedDataSourceGroupAndDirectPaths() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "VIEWER");
    insertLayer(layerId, "highest-layer-group-embedded-source-group-layer");
    insertLayerGroup(layerGroupId, "highest-direct-layer-group");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "highest-direct-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.BOTH);
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerGroupLayerAssignmentIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertLayer(layerId, "removed-layer-group-layer");
    insertLayerGroup(layerGroupId, "removed-layer-group");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "removed-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "VIEWER");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    removeLayerGroupLayer(layerGroupId, layerId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerGroupIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertLayer(layerId, "deleted-layer-group-layer");
    insertLayerGroup(layerGroupId, "deleted-layer-group");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "deleted-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "VIEWER");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);

    removeLayerGroup(layerGroupId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsFalse_whenLayerGroupEmbeddedDataSourceGroupLayerIsArchived() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(sourceId);
    insertLayer(layerId, "archived-layer-group-embedded-source-group-layer");
    insertLayerGroup(layerGroupId, "archived-layer-group");
    insertLayerGroupMember(layerGroupId, userId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, "archived-layer-group-source-group");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "VIEWER");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);
    archiveLayer(layerId, archivedBy);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsFalse_afterLayerDataSourceGroupEmbeddingIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertLayer(layerId, "removed-embedded-source-group-layer");
    insertLayerMember(layerId, userId);
    insertDataSourceGroup(groupId, "removed-embedded-source-group");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, groupId);

    removeLayerDataSourceGroup(layerId, groupId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRole_returnsDirectSourceRole() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "ADMIN");

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.DIRECT);
  }

  @Test
  void userRole_returnsGroupSourceRole_forUserGroupDataSourceGroupAssignment() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertUserGroup(userGroupId, "source-access-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertDataSourceGroup(dataSourceGroupId, "assigned-user-group-source-group");
    insertDataSourceGroupUserGroup(dataSourceGroupId, userGroupId, "MEMBER");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userHasAccess_returnsFalse_afterDataSourceGroupUserGroupAssignmentIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertUserGroup(userGroupId, "removed-source-access-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertDataSourceGroup(dataSourceGroupId, "removed-assigned-user-group-source-group");
    insertDataSourceGroupUserGroup(dataSourceGroupId, userGroupId, "MEMBER");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "VIEWER");

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));

    removeDataSourceGroupUserGroup(dataSourceGroupId, userGroupId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRole_returnsHighestRoleAcrossDirectAndIndirectPaths() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "VIEWER");
    insertDataSourceGroup(groupId, "highest-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.BOTH);
  }

  @Test
  void userRole_returnsHighestRoleAcrossAllComposedSourceGrantPaths() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var directGroupId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var userGroupDataSourceGroupId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerDataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "VIEWER");
    insertDataSourceGroup(directGroupId, "all-paths-direct-source-group");
    insertDataSourceGroupMember(directGroupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(directGroupId, sourceId, "ADMIN");
    insertUserGroup(userGroupId, "all-paths-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertDataSourceGroup(userGroupDataSourceGroupId, "all-paths-user-group-source-group");
    insertDataSourceGroupUserGroup(userGroupDataSourceGroupId, userGroupId, "MEMBER");
    insertDataSourceGroupDataSource(userGroupDataSourceGroupId, sourceId, "VIEWER");
    insertLayer(layerId, "all-paths-layer");
    insertLayerMember(layerId, userId);
    insertServerLayerAccess(sourceId, layerId);
    insertDataSourceGroup(layerDataSourceGroupId, "all-paths-layer-source-group");
    insertDataSourceGroupDataSource(layerDataSourceGroupId, sourceId, "OWNER");
    insertLayerDataSourceGroup(layerId, layerDataSourceGroupId);

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.OWNER, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.OWNER, AccessVia.BOTH);
  }

  @Test
  void userRole_returnsNull_whenDirectGrantSourceIsArchived() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "OWNER");
    archiveSource(sourceId, archivedBy);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRoles_returnsArchivedDirectGrantForVisibilityFilteredListings() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(sourceId);
    insertServerUserAccess(sourceId, userId, "OWNER");
    archiveSource(sourceId, archivedBy);

    var roles = sourceAccessService.userRoles(userId);

    assertEquals(1, roles.size());
    assertEquals(sourceId, roles.getFirst().serverId());
    assertEquals(ServerRole.OWNER, roles.getFirst().role());
    assertEquals(AccessVia.DIRECT, roles.getFirst().via());
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRole_returnsGroupSourceRole_forDirectDataSourceGroupMember() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "direct-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");

    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
  }

  @Test
  void userHasAccess_returnsFalse_afterDirectDataSourceGroupMemberIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "removed-member-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "VIEWER");

    removeDataSourceGroupMember(groupId, userId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userRole_returnsNull_whenDataSourceGroupGrantSourceIsArchived() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    var archivedBy = UUID.randomUUID();
    insertUser(userId);
    insertUser(archivedBy);
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "archived-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "ADMIN");
    archiveSource(sourceId, archivedBy);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  @Test
  void userHasAccess_returnsFalse_afterDirectDataSourceGroupDataSourceAssignmentIsRemoved() {
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var groupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertDataSourceGroup(groupId, "removed-source-assignment-source-group");
    insertDataSourceGroupMember(groupId, userId, "MEMBER");
    insertDataSourceGroupDataSource(groupId, sourceId, "VIEWER");

    removeDataSourceGroupDataSource(groupId, sourceId);

    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  private void assertUserRoleMatchesListing(
      UUID userId, UUID sourceId, ServerRole expectedRole, AccessVia expectedVia) {
    var listedRole =
        sourceAccessService.userRoles(userId).stream()
            .filter(role -> role.serverId().equals(sourceId))
            .findFirst()
            .orElseThrow();

    assertEquals(expectedRole, sourceAccessService.userRole(userId, sourceId));
    assertEquals(expectedRole, listedRole.role());
    assertEquals(expectedVia, listedRole.via());
  }

  private void assertNoSourceAccess(UUID userId, UUID sourceId) {
    assertFalse(sourceAccessService.userHasAccess(userId, sourceId));
    assertNull(sourceAccessService.userRole(userId, sourceId));
  }

  private UserGroupLayerGroupDataSourceGroupChain insertUserGroupLayerGroupDataSourceGroupChain(
      String nameSuffix) {
    var uniqueName = nameSuffix + "-" + UUID.randomUUID();
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var userGroupId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertUser(userId);
    insertSource(sourceId);
    insertUserGroup(userGroupId, uniqueName + "-user-group");
    insertUserGroupMember(userGroupId, userId);
    insertLayer(layerId, uniqueName + "-layer");
    insertLayerGroup(layerGroupId, uniqueName + "-layer-group");
    insertLayerGroupUserGroup(layerGroupId, userGroupId, "MEMBER");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, uniqueName + "-dsg");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);
    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
    return new UserGroupLayerGroupDataSourceGroupChain(
        userId, sourceId, userGroupId, layerId, layerGroupId, dataSourceGroupId);
  }

  private LayerGroupDataSourceGroupChain insertGlobalLayerGroupDataSourceGroupChain(
      String nameSuffix) {
    var uniqueName = nameSuffix + "-" + UUID.randomUUID();
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var layerGroupId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, uniqueName + "-layer");
    insertLayerGroup(layerGroupId, uniqueName + "-layer-group", "GLOBAL");
    insertLayerGroupLayer(layerGroupId, layerId, "MEMBER");
    insertDataSourceGroup(dataSourceGroupId, uniqueName + "-dsg");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);
    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
    return new LayerGroupDataSourceGroupChain(
        userId, sourceId, layerId, layerGroupId, dataSourceGroupId);
  }

  private LayerDataSourceGroupChain insertGlobalLayerDataSourceGroupChain(String nameSuffix) {
    var uniqueName = nameSuffix + "-" + UUID.randomUUID();
    var userId = UUID.randomUUID();
    var sourceId = UUID.randomUUID();
    var layerId = UUID.randomUUID();
    var dataSourceGroupId = UUID.randomUUID();
    insertSource(sourceId);
    insertLayer(layerId, uniqueName + "-layer", "GLOBAL");
    insertDataSourceGroup(dataSourceGroupId, uniqueName + "-dsg");
    insertDataSourceGroupDataSource(dataSourceGroupId, sourceId, "ADMIN");
    insertLayerDataSourceGroup(layerId, dataSourceGroupId);
    assertTrue(sourceAccessService.userHasAccess(userId, sourceId));
    assertEquals(ServerRole.ADMIN, sourceAccessService.userRole(userId, sourceId));
    assertUserRoleMatchesListing(userId, sourceId, ServerRole.ADMIN, AccessVia.INDIRECT);
    return new LayerDataSourceGroupChain(userId, sourceId, layerId, dataSourceGroupId);
  }

  private record UserGroupLayerGroupDataSourceGroupChain(
      UUID userId,
      UUID sourceId,
      UUID userGroupId,
      UUID layerId,
      UUID layerGroupId,
      UUID dataSourceGroupId) {}

  private record LayerGroupDataSourceGroupChain(
      UUID userId, UUID sourceId, UUID layerId, UUID layerGroupId, UUID dataSourceGroupId) {}

  private record LayerDataSourceGroupChain(
      UUID userId, UUID sourceId, UUID layerId, UUID dataSourceGroupId) {}

  private void insertLayer(UUID layerId, String name) {
    insertLayer(layerId, name, "PRIVATE");
  }

  private void insertLayer(UUID layerId, String name, String accessScope) {
    jdbc.update(
        """
        INSERT INTO iam.layer(id, name, access_scope)
        VALUES (:id, :name, CAST(:accessScope AS iam.layer_access_scope))
        """,
        Map.of("id", layerId, "name", name, "accessScope", accessScope));
  }

  private void insertLayerMember(UUID layerId, UUID userId) {
    jdbc.update(
        "INSERT INTO iam.layer_member(layer_id, user_id, role) VALUES (:layerId, :userId, 'MEMBER')",
        Map.of("layerId", layerId, "userId", userId));
  }

  private void insertServerLayerAccess(UUID sourceId, UUID layerId) {
    jdbc.update(
        "INSERT INTO iam.server_layer_access(server_id, layer_id, role) VALUES (:serverId, :layerId, 'VIEWER')",
        Map.of("serverId", sourceId, "layerId", layerId));
  }

  private void insertServerUserAccess(UUID sourceId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.server_user_access(server_id, user_id, role)
        VALUES (:sourceId, :userId, CAST(:role AS iam.server_role))
        """,
        Map.of("sourceId", sourceId, "userId", userId, "role", role));
  }

  private void insertSource(UUID sourceId) {
    var mapperId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO profiles.mapper(id, qualifier)
        VALUES (:id, 'INGRESS'::profiles.mapper_qualifier)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", mapperId));
    jdbc.update(
        """
        INSERT INTO profiles.data_mapper(id, version, name, mapper)
        VALUES (:id, 1, :name, '{}'::jsonb)
        """,
        Map.of("id", mapperId, "name", "mapper-" + mapperId));
    NativeIntegrationFixture.seed(jdbc.getJdbcOperations());
    jdbc.update(
        """
        INSERT INTO profiles.data_source(
          id, name, tenant, provider, access, ingress_mapper_id,
          connection_spec, integration_id, connection_schema_version
        )
        VALUES(
          :id, :name, 'default', 'TCP'::profiles.transport_kind, 'R'::profiles.access_mode,
          :mapperId, '{}'::jsonb,
          :integrationId, :schemaVersion
        )
        """,
        Map.of(
            "id",
            sourceId,
            "name",
            "source-" + sourceId,
            "mapperId",
            mapperId,
            "integrationId",
            NativeIntegrationFixture.CONNECTION_SCHEMA.integrationId(),
            "schemaVersion",
            NativeIntegrationFixture.CONNECTION_SCHEMA.schemaVersion()));
  }

  private void insertDataSourceGroup(UUID groupId, String name) {
    jdbc.update(
        "INSERT INTO iam.data_source_group(id, name) VALUES (:id, :name)",
        Map.of("id", groupId, "name", name));
  }

  private void insertLayerGroup(UUID layerGroupId, String name) {
    insertLayerGroup(layerGroupId, name, "PRIVATE");
  }

  private void insertLayerGroup(UUID layerGroupId, String name, String accessScope) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group(id, name, access_scope)
        VALUES (:id, :name, CAST(:accessScope AS iam.layer_group_access_scope))
        """,
        Map.of("id", layerGroupId, "name", name, "accessScope", accessScope));
  }

  private void insertLayerGroupMember(UUID layerGroupId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_member(layer_group_id, user_id, role)
        VALUES (:layerGroupId, :userId, CAST(:role AS iam.layer_group_role))
        """,
        Map.of("layerGroupId", layerGroupId, "userId", userId, "role", role));
  }

  private void insertLayerGroupUserGroup(UUID layerGroupId, UUID userGroupId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_user_group(layer_group_id, user_group_id, role)
        VALUES (:layerGroupId, :userGroupId, CAST(:role AS iam.layer_group_role))
        """,
        Map.of("layerGroupId", layerGroupId, "userGroupId", userGroupId, "role", role));
  }

  private void insertLayerGroupLayer(UUID layerGroupId, UUID layerId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.layer_group_layer(layer_group_id, layer_id, role)
        VALUES (:layerGroupId, :layerId, CAST(:role AS iam.layer_role))
        """,
        Map.of("layerGroupId", layerGroupId, "layerId", layerId, "role", role));
  }

  private void insertDataSourceGroupMember(UUID groupId, UUID userId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_member(data_source_group_id, user_id, role)
        VALUES (:groupId, :userId, CAST(:role AS iam.data_source_group_role))
        """,
        Map.of("groupId", groupId, "userId", userId, "role", role));
  }

  private void insertDataSourceGroupUserGroup(UUID groupId, UUID userGroupId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_user_group(data_source_group_id, user_group_id, role)
        VALUES (:groupId, :userGroupId, CAST(:role AS iam.data_source_group_role))
        """,
        Map.of("groupId", groupId, "userGroupId", userGroupId, "role", role));
  }

  private void insertDataSourceGroupDataSource(UUID groupId, UUID sourceId, String role) {
    jdbc.update(
        """
        INSERT INTO iam.data_source_group_data_source(data_source_group_id, data_source_id, role)
        VALUES (:groupId, :sourceId, CAST(:role AS iam.server_role))
        """,
        Map.of("groupId", groupId, "sourceId", sourceId, "role", role));
  }

  private void insertLayerDataSourceGroup(UUID layerId, UUID groupId) {
    jdbc.update(
        """
        INSERT INTO iam.layer_data_source_group(layer_id, data_source_group_id)
        VALUES (:layerId, :groupId)
        """,
        Map.of("layerId", layerId, "groupId", groupId));
  }

  private void removeLayerDataSourceGroup(UUID layerId, UUID groupId) {
    jdbc.update(
        """
        DELETE FROM iam.layer_data_source_group
        WHERE layer_id = :layerId AND data_source_group_id = :groupId
        """,
        Map.of("layerId", layerId, "groupId", groupId));
  }

  private void removeLayerGroupLayer(UUID layerGroupId, UUID layerId) {
    jdbc.update(
        """
        DELETE FROM iam.layer_group_layer
        WHERE layer_group_id = :layerGroupId AND layer_id = :layerId
        """,
        Map.of("layerGroupId", layerGroupId, "layerId", layerId));
  }

  private void removeLayerGroup(UUID layerGroupId) {
    jdbc.update(
        """
        DELETE FROM iam.layer_group
        WHERE id = :layerGroupId
        """,
        Map.of("layerGroupId", layerGroupId));
  }

  private void removeUserGroupMember(UUID groupId, UUID userId) {
    jdbc.update(
        """
        DELETE FROM iam.user_group_member
        WHERE group_id = :groupId AND user_id = :userId
        """,
        Map.of("groupId", groupId, "userId", userId));
  }

  private void removeLayerGroupUserGroup(UUID layerGroupId, UUID userGroupId) {
    jdbc.update(
        """
        DELETE FROM iam.layer_group_user_group
        WHERE layer_group_id = :layerGroupId AND user_group_id = :userGroupId
        """,
        Map.of("layerGroupId", layerGroupId, "userGroupId", userGroupId));
  }

  private void removeDataSourceGroupMember(UUID groupId, UUID userId) {
    jdbc.update(
        """
        DELETE FROM iam.data_source_group_member
        WHERE data_source_group_id = :groupId AND user_id = :userId
        """,
        Map.of("groupId", groupId, "userId", userId));
  }

  private void removeDataSourceGroupUserGroup(UUID groupId, UUID userGroupId) {
    jdbc.update(
        """
        DELETE FROM iam.data_source_group_user_group
        WHERE data_source_group_id = :groupId AND user_group_id = :userGroupId
        """,
        Map.of("groupId", groupId, "userGroupId", userGroupId));
  }

  private void removeDataSourceGroupDataSource(UUID groupId, UUID sourceId) {
    jdbc.update(
        """
        DELETE FROM iam.data_source_group_data_source
        WHERE data_source_group_id = :groupId AND data_source_id = :sourceId
        """,
        Map.of("groupId", groupId, "sourceId", sourceId));
  }

  private void archiveLayer(UUID layerId, UUID archivedBy) {
    jdbc.update(
        """
        UPDATE iam.layer
        SET archived_at = now(),
            archived_by = :archivedBy,
            lifecycle_state = 'ARCHIVED'::iam.layer_lifecycle_state
        WHERE id = :id
        """,
        Map.of("archivedBy", archivedBy, "id", layerId));
  }

  private void updateLayerLifecycle(UUID layerId, String lifecycleState) {
    jdbc.update(
        """
        UPDATE iam.layer
        SET lifecycle_state = CAST(:lifecycleState AS iam.layer_lifecycle_state)
        WHERE id = :id
        """,
        Map.of("lifecycleState", lifecycleState, "id", layerId));
  }

  private void updateLayerAccessScope(UUID layerId, String accessScope) {
    jdbc.update(
        """
        UPDATE iam.layer
        SET access_scope = CAST(:accessScope AS iam.layer_access_scope)
        WHERE id = :id
        """,
        Map.of("accessScope", accessScope, "id", layerId));
  }

  private void archiveSource(UUID sourceId, UUID archivedBy) {
    jdbc.update(
        """
        UPDATE profiles.data_source
        SET archived_at = now(),
            archived_by = :archivedBy
        WHERE id = :id
        """,
        Map.of("archivedBy", archivedBy, "id", sourceId));
  }

  private void insertUser(UUID userId) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, email, first_name, last_name, display_name
        ) VALUES (:id, :idpUserId, :username, :issuer, :username, :email, :firstName, :lastName, :displayName)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of(
            "id",
            userId,
            "idpUserId",
            "idp-" + userId,
            "username",
            "user-" + userId,
            "issuer",
            "https://issuer.test",
            "email",
            "user@example.test",
            "firstName",
            "Test",
            "lastName",
            "User",
            "displayName",
            "Test User"));
  }

  private void insertUserGroup(UUID groupId, String name) {
    jdbc.update(
        """
        INSERT INTO iam.user_group(id, name)
        VALUES (:id, :name)
        """,
        Map.of("id", groupId, "name", name));
  }

  private void insertUserGroupMember(UUID groupId, UUID userId) {
    jdbc.update(
        """
        INSERT INTO iam.user_group_member(group_id, user_id)
        VALUES (:groupId, :userId)
        """,
        Map.of("groupId", groupId, "userId", userId));
  }

  @Import(SourceAccessService.class)
  @EnableJpaRepositories(basePackageClasses = EffectiveSourceRoleRepository.class)
  static class Config {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
      return EmbeddedPostgres.builder().start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres postgres) {
      return postgres.getPostgresDatabase();
    }

    @Bean
    NamedParameterJdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    Flyway flyway(DataSource dataSource) {
      return Flyway.configure()
          .dataSource(dataSource)
          .baselineOnMigrate(true)
          .locations("classpath:db/migration")
          .placeholders(FLYWAY_PLACEHOLDERS)
          .load();
    }

    @Bean
    InitializingBean flywayInitializer(Flyway flyway) {
      return flyway::migrate;
    }

    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        DataSource dataSource, InitializingBean flywayInitializer) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setPackagesToScan("com.opencop.core.sdk.technical.gateway.db.iam");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
      return factory;
    }

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
      return new JpaTransactionManager(entityManagerFactory);
    }
  }
}
