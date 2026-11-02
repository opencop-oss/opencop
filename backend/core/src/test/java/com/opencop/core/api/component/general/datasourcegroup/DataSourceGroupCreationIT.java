// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.component.general.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.usecase.datasourcegroup.DataSourceGroupContainerUseCase;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupAuthorization;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupMemberPatchGuard;
import com.opencop.core.api.general.ports.gateway.datasourcegroup.DataSourceGroupService;
import com.opencop.core.api.general.ports.usecase.datasourcegroup.DataSourceGroupContainerManagement;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMemberResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupResponse;
import com.opencop.core.api.general.technical.model.datasourcegroup.MyDataSourceGroupResponse;
import com.opencop.core.sdk.component.ComponentWebIT;
import com.opencop.core.sdk.component.database.AbstractDatabaseIT;
import com.opencop.core.sdk.component.database.ComponentTestDatabaseConfig;
import com.opencop.core.sdk.ports.exception.GlobalOpenCopException;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@ComponentWebIT(classes = DataSourceGroupCreationIT.TestApplication.class)
class DataSourceGroupCreationIT extends AbstractDatabaseIT {

  private static final UUID OWNER_ID = UUID.fromString("550e8400-e29b-41d4-a716-bb0000000001");
  private static final UUID MISSING_OWNER_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-bb0000000002");

  @Autowired private DataSourceGroupContainerManagement containerManagement;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void cleanDataSourceGroups() {
    jdbc.update("DELETE FROM iam.data_source_group_member");
    jdbc.update("DELETE FROM iam.data_source_group_user_group");
    jdbc.update("DELETE FROM iam.data_source_group");
    jdbc.update("DELETE FROM opencop_user WHERE id IN (?, ?)", OWNER_ID, MISSING_OWNER_ID);
  }

  @Test
  void create_missingCreatorUser_rollsBackCreatedGroup() {
    var request = new DataSourceGroupCreateRequest("Rollback Group", null);

    var ex =
        assertThrows(
            GlobalOpenCopException.class,
            () -> containerManagement.create(request, principal(MISSING_OWNER_ID)));

    assertEquals("Could not find user: " + MISSING_OWNER_ID, ex.getMessage());
    assertEquals(0, countGroupsNamed("Rollback Group"));
  }

  @Test
  void create_existingCreatorUser_persistsCreatorAsOwner() {
    insertUser(OWNER_ID);
    var request = new DataSourceGroupCreateRequest("Owned Group", "owned");

    var response = containerManagement.create(request, principal(OWNER_ID));

    assertNotNull(response.id());
    assertEquals("Owned Group", response.name());
    assertEquals("OWNER", memberRole(response.id(), OWNER_ID));
  }

  private int countGroupsNamed(String name) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM iam.data_source_group WHERE name = ?", Integer.class, name);
  }

  private String memberRole(UUID groupId, UUID userId) {
    return jdbc.queryForObject(
        """
        SELECT role::text
        FROM iam.data_source_group_member
        WHERE data_source_group_id = ? AND user_id = ?
        """,
        String.class,
        groupId,
        userId);
  }

  private void insertUser(UUID id) {
    jdbc.update(
        """
        INSERT INTO opencop_user (
          id, idp_user_id, idp_username, idp_issuer, username, active, administrator
        )
        VALUES (?, ?, ?, 'issuer', ?, true, false)
        """,
        id,
        id.toString(),
        "user-" + id,
        "user-" + id);
  }

  private static OpenCopPrincipal principal(UUID userId) {
    OpenCopPrincipal principal = mock(OpenCopPrincipal.class);
    when(principal.internalUserId()).thenReturn(userId);
    return principal;
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({ComponentTestDatabaseConfig.class, DataSourceGroupContainerUseCase.class})
  static class TestApplication {

    @Bean
    DataSourceGroupAuthorization dataSourceGroupAuthorization() {
      return mock(DataSourceGroupAuthorization.class);
    }

    @Bean
    DataSourceGroupService dataSourceGroupService(JdbcTemplate jdbc) {
      return new JdbcDataSourceGroupService(jdbc);
    }
  }

  private static final class JdbcDataSourceGroupService implements DataSourceGroupService {

    private final JdbcTemplate jdbc;

    private JdbcDataSourceGroupService(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public List<DataSourceGroupResponse> listGroups() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<DataSourceGroupResponse> listGroupsForUser(UUID userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<MyDataSourceGroupResponse> listMyGroups(UUID userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DataSourceGroupResponse createGroup(DataSourceGroupCreateRequest request) {
      UUID id = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO iam.data_source_group (id, name, description)
          VALUES (?, ?, ?)
          """,
          id,
          request.name(),
          request.description());
      return new DataSourceGroupResponse(id, request.name(), request.description(), Instant.now());
    }

    @Override
    public DataSourceGroupResponse getGroup(UUID groupId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public DataSourceGroupResponse patchGroup(UUID groupId, DataSourceGroupPatchRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteGroup(UUID groupId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<DataSourceGroupMemberResponse> getMembers(UUID dataSourceGroupId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void patchMembers(
        UUID dataSourceGroupId,
        DataSourceGroupMembersPatchRequest request,
        DataSourceGroupMemberPatchGuard guard)
        throws PersistenceException {
      for (DataSourceGroupMembersPatchRequest.MemberAdd member : request.add()) {
        int userCount =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM opencop_user WHERE id = ?", Integer.class, member.userId());
        if (userCount == 0) {
          throw new PersistenceException("Could not find user: " + member.userId());
        }
        jdbc.update(
            """
            INSERT INTO iam.data_source_group_member (data_source_group_id, user_id, role)
            VALUES (?, ?, ?::iam.data_source_group_role)
            """,
            dataSourceGroupId,
            member.userId(),
            member.role().name());
      }
    }
  }
}
