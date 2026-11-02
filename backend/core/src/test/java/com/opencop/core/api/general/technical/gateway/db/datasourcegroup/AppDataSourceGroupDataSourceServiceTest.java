// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupDataSourceAssignRequest;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.model.connection.spec.security.NoSecurity;
import com.opencop.core.sdk.model.connection.spec.transport.TcpConfig;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.DataSource;
import com.opencop.core.sdk.model.datasource.DataSourceReadFilter;
import com.opencop.core.sdk.model.datasource.SocketDataSource;
import com.opencop.core.sdk.model.visibility.RecordVisibility;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupDataSourceIdEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupDataSourceRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppDataSourceGroupDataSourceServiceTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443100");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655443101");
  private static final UUID SECOND_SOURCE_ID =
      UUID.fromString("550e8400-e29b-41d4-a716-446655443102");

  private IamDataSourceGroupRepository dataSourceGroupRepo;
  private IamDataSourceGroupDataSourceRepository dataSourceRepo;
  private DataSourceRepository coreDataSourceRepo;
  private AppDataSourceGroupDataSourceService service;

  @BeforeEach
  void setUp() {
    dataSourceGroupRepo = mock(IamDataSourceGroupRepository.class);
    dataSourceRepo = mock(IamDataSourceGroupDataSourceRepository.class);
    coreDataSourceRepo = mock(DataSourceRepository.class);
    service =
        new AppDataSourceGroupDataSourceService(
            dataSourceGroupRepo, dataSourceRepo, coreDataSourceRepo);
  }

  @Test
  void getDataSources_batchLoadsSourceMetadata() throws PersistenceException {
    var group = group(GROUP_ID, "Sources");
    var firstAssignment = new DataSourceGroupDataSourceEntity(group, SOURCE_ID, ServerRole.VIEWER);
    var secondAssignment =
        new DataSourceGroupDataSourceEntity(group, SECOND_SOURCE_ID, ServerRole.ADMIN);
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(true);
    when(dataSourceRepo.findAllByDataSourceGroupId(GROUP_ID))
        .thenReturn(List.of(firstAssignment, secondAssignment));
    when(coreDataSourceRepo.findAll(any(DataSourceReadFilter.class)))
        .thenReturn(List.of(source(SOURCE_ID, "camera"), source(SECOND_SOURCE_ID, "ais")));

    var result = service.getDataSources(GROUP_ID);

    var filterCaptor = ArgumentCaptor.forClass(DataSourceReadFilter.class);
    verify(coreDataSourceRepo).findAll(filterCaptor.capture());
    verify(coreDataSourceRepo, never()).findById(any());
    assertEquals(Set.of(SOURCE_ID, SECOND_SOURCE_ID), filterCaptor.getValue().ids());
    assertEquals(RecordVisibility.ALL, filterCaptor.getValue().recordVisibilityOrDefault());
    assertEquals("camera", result.get(0).dataSourceName());
    assertEquals("ais", result.get(1).dataSourceName());
  }

  @Test
  void getDataSources_emptyAssignments_skipsSourceMetadataLookup() throws PersistenceException {
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(true);
    when(dataSourceRepo.findAllByDataSourceGroupId(GROUP_ID)).thenReturn(List.of());

    var result = service.getDataSources(GROUP_ID);

    assertEquals(List.of(), result);
    verify(coreDataSourceRepo, never()).findAll(any(DataSourceReadFilter.class));
    verify(coreDataSourceRepo, never()).findById(any());
  }

  @Test
  void assignDataSources_newAssignment_savesViewerWhenRoleDefaults() throws PersistenceException {
    var group = group(GROUP_ID, "Sources");
    var source = source(SOURCE_ID, "camera");
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(coreDataSourceRepo.findById(SOURCE_ID)).thenReturn(Optional.of(source));
    when(dataSourceRepo.findById(new DataSourceGroupDataSourceIdEntity(GROUP_ID, SOURCE_ID)))
        .thenReturn(Optional.empty());
    when(dataSourceRepo.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, DataSourceGroupDataSourceEntity.class));

    var result =
        service.assignDataSources(
            GROUP_ID, new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), null));

    ArgumentCaptor<DataSourceGroupDataSourceEntity> captor =
        ArgumentCaptor.forClass(DataSourceGroupDataSourceEntity.class);
    verify(dataSourceRepo).save(captor.capture());
    verify(coreDataSourceRepo, times(1)).findById(SOURCE_ID);
    assertEquals(ServerRole.VIEWER, captor.getValue().getRole());
    assertEquals(ServerRole.VIEWER, result.getFirst().role());
    assertEquals("camera", result.getFirst().dataSourceName());
  }

  @Test
  void assignDataSources_existingAssignment_updatesRoleInPlace() throws PersistenceException {
    var group = group(GROUP_ID, "Sources");
    var source = source(SOURCE_ID, "camera");
    var existing = new DataSourceGroupDataSourceEntity(group, SOURCE_ID, ServerRole.VIEWER);
    when(dataSourceGroupRepo.findByIdForUpdate(GROUP_ID)).thenReturn(Optional.of(group));
    when(coreDataSourceRepo.findById(SOURCE_ID)).thenReturn(Optional.of(source));
    when(dataSourceRepo.findById(new DataSourceGroupDataSourceIdEntity(GROUP_ID, SOURCE_ID)))
        .thenReturn(Optional.of(existing));
    when(dataSourceRepo.save(existing)).thenReturn(existing);

    service.assignDataSources(
        GROUP_ID, new DataSourceGroupDataSourceAssignRequest(Set.of(SOURCE_ID), ServerRole.ADMIN));

    verify(dataSourceRepo).save(existing);
    assertEquals(ServerRole.ADMIN, existing.getRole());
  }

  @Test
  void removeDataSource_notAssigned_throwsAndDoesNotDelete() {
    var id = new DataSourceGroupDataSourceIdEntity(GROUP_ID, SOURCE_ID);
    when(dataSourceRepo.existsById(id)).thenReturn(false);

    assertThrows(PersistenceException.class, () -> service.removeDataSource(GROUP_ID, SOURCE_ID));

    verify(dataSourceRepo, never()).deleteByDataSourceGroupIdAndDataSourceId(any(), any());
  }

  private static DataSourceGroupEntity group(UUID id, String name) {
    var group = new DataSourceGroupEntity();
    group.setId(id);
    group.setName(name);
    return group;
  }

  private static DataSource source(UUID id, String name) {
    return new SocketDataSource(
        id,
        name,
        "tenant",
        new ConnectionSpec(
            new TcpConfig("127.0.0.1", 8087, null, null, null, null, new NoSecurity())),
        null,
        null,
        null,
        AccessMode.READ,
        true,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }
}
