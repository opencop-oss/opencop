// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.datasourcegroup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.repository.DataSourceRepository;
import com.opencop.core.api.general.technical.gateway.scim.user.InternalUserRepository;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupCreateRequest;
import com.opencop.core.api.general.technical.model.datasourcegroup.DataSourceGroupPatchRequest;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.DataSourceGroupEntity;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupDataSourceRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupMembersRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.datasourcegroup.IamDataSourceGroupUserGroupRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.group.IamGroupMembersRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class AppDataSourceGroupServiceTest {

  private static final UUID GROUP_ID = UUID.fromString("550e8400-e29b-41d4-a716-556655443001");

  private IamDataSourceGroupRepository dataSourceGroupRepo;
  private IamDataSourceGroupMembersRepository membersRepo;
  private InternalUserRepository userRepo;
  private IamDataSourceGroupUserGroupRepository dataSourceGroupUserGroupRepo;
  private IamGroupMembersRepository groupMembersRepo;
  private IamDataSourceGroupDataSourceRepository dataSourceRepo;
  private DataSourceRepository coreDataSourceRepo;
  private AppDataSourceGroupService service;

  @BeforeEach
  void setUp() {
    dataSourceGroupRepo = mock(IamDataSourceGroupRepository.class);
    membersRepo = mock(IamDataSourceGroupMembersRepository.class);
    userRepo = mock(InternalUserRepository.class);
    dataSourceGroupUserGroupRepo = mock(IamDataSourceGroupUserGroupRepository.class);
    groupMembersRepo = mock(IamGroupMembersRepository.class);
    dataSourceRepo = mock(IamDataSourceGroupDataSourceRepository.class);
    coreDataSourceRepo = mock(DataSourceRepository.class);
    service =
        new AppDataSourceGroupService(
            dataSourceGroupRepo,
            membersRepo,
            userRepo,
            dataSourceGroupUserGroupRepo,
            groupMembersRepo,
            dataSourceRepo,
            coreDataSourceRepo);
  }

  @Test
  void createGroup_withValidName_returnsResponseAndSavesOnce() throws PersistenceException {
    when(dataSourceGroupRepo.existsByName("Alpha")).thenReturn(false);
    when(dataSourceGroupRepo.save(any())).thenReturn(dataSourceGroup(GROUP_ID, "Alpha", "desc"));

    var result = service.createGroup(new DataSourceGroupCreateRequest("Alpha", "desc"));

    verify(dataSourceGroupRepo).save(any());
    assertEquals(GROUP_ID, result.id());
    assertEquals("Alpha", result.name());
    assertEquals("desc", result.description());
  }

  @Test
  void createGroup_trimsNameBeforeDuplicateCheckAndSave() throws PersistenceException {
    when(dataSourceGroupRepo.existsByName("Alpha")).thenReturn(false);
    when(dataSourceGroupRepo.save(any()))
        .thenAnswer(
            invocation -> {
              DataSourceGroupEntity saved = invocation.getArgument(0);
              return dataSourceGroup(GROUP_ID, saved.getName(), saved.getDescription());
            });

    var result = service.createGroup(new DataSourceGroupCreateRequest("  Alpha  ", "desc"));

    var captor = ArgumentCaptor.forClass(DataSourceGroupEntity.class);
    verify(dataSourceGroupRepo).existsByName("Alpha");
    verify(dataSourceGroupRepo).save(captor.capture());
    assertEquals("Alpha", captor.getValue().getName());
    assertEquals("Alpha", result.name());
  }

  @Test
  void createGroup_withNullName_throwsPersistenceExceptionAndDoesNotSave() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new DataSourceGroupCreateRequest(null, null)));

    assertEquals("Data source group name is required", ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void createGroup_withBlankName_throwsPersistenceExceptionAndDoesNotSave() {
    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new DataSourceGroupCreateRequest("  ", null)));

    assertEquals("Data source group name is required", ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void createGroup_withDuplicateName_throwsPersistenceExceptionAndDoesNotSave() {
    when(dataSourceGroupRepo.existsByName("Alpha")).thenReturn(true);

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new DataSourceGroupCreateRequest("Alpha", null)));

    assertEquals("A data source group with that name already exists", ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void createGroup_dataIntegrityViolationOnSave_translatesToDuplicateNamePersistenceException() {
    when(dataSourceGroupRepo.existsByName("Alpha")).thenReturn(false);
    when(dataSourceGroupRepo.save(any())).thenThrow(new DataIntegrityViolationException("unique"));

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.createGroup(new DataSourceGroupCreateRequest("Alpha", null)));

    assertEquals("A data source group with that name already exists", ex.getMessage());
  }

  @Test
  void patchGroup_updatesNameAndDescriptionAndSavesOnce() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Old", "old desc");
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.existsByName("New")).thenReturn(false);
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("New", "new desc"));

    verify(dataSourceGroupRepo).save(entity);
    assertEquals("New", result.name());
    assertEquals("new desc", result.description());
  }

  @Test
  void patchGroup_trimsNameBeforeDuplicateCheckAndSave() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Old", null);
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.existsByName("New")).thenReturn(false);
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("  New  ", null));

    verify(dataSourceGroupRepo).existsByName("New");
    verify(dataSourceGroupRepo).save(entity);
    assertEquals("New", entity.getName());
    assertEquals("New", result.name());
  }

  @Test
  void patchGroup_withNullName_updatesOnlyDescription() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Existing", "old desc");
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest(null, "new desc"));

    verify(dataSourceGroupRepo, never()).existsByName(any());
    verify(dataSourceGroupRepo).save(entity);
    assertEquals("Existing", result.name());
    assertEquals("new desc", result.description());
  }

  @Test
  void patchGroup_withNullDescription_leavesExistingDescriptionUnchanged()
      throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Existing", "old desc");
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("Existing", null));

    verify(dataSourceGroupRepo, never()).existsByName(any());
    verify(dataSourceGroupRepo).save(entity);
    assertEquals("Existing", result.name());
    assertEquals("old desc", result.description());
  }

  @Test
  void patchGroup_withBlankDescription_allowsBlankDescription() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Existing", "old desc");
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest(null, "  "));

    assertEquals("  ", result.description());
  }

  @Test
  void patchGroup_withBothFieldsNull_savesUnchangedEntity() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Existing", "old desc");
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    var result = service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest(null, null));

    verify(dataSourceGroupRepo, never()).existsByName(any());
    verify(dataSourceGroupRepo).save(entity);
    assertEquals("Existing", result.name());
    assertEquals("old desc", result.description());
  }

  @Test
  void patchGroup_withBlankName_throwsPersistenceExceptionAndDoesNotSave() {
    var entity = dataSourceGroup(GROUP_ID, "Existing", null);
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("  ", null)));

    assertEquals("Data source group name cannot be blank", ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void patchGroup_renameToExistingName_throwsPersistenceExceptionAndDoesNotSave() {
    var entity = dataSourceGroup(GROUP_ID, "Old", null);
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.existsByName("Existing")).thenReturn(true);

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("Existing", null)));

    assertEquals("A data source group with that name already exists", ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void patchGroup_sameNameRoundtrip_skipsDuplicateCheckAndSaves() throws PersistenceException {
    var entity = dataSourceGroup(GROUP_ID, "Same", null);
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.save(entity)).thenReturn(entity);

    service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("Same", null));

    verify(dataSourceGroupRepo, never()).existsByName(any());
    verify(dataSourceGroupRepo).save(entity);
  }

  @Test
  void patchGroup_missingGroup_throwsPersistenceExceptionAndDoesNotSave() {
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("New", null)));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    verify(dataSourceGroupRepo, never()).save(any());
  }

  @Test
  void patchGroup_dataIntegrityViolationOnSave_translatesToDuplicateNamePersistenceException() {
    var entity = dataSourceGroup(GROUP_ID, "Old", null);
    when(dataSourceGroupRepo.findById(GROUP_ID)).thenReturn(Optional.of(entity));
    when(dataSourceGroupRepo.existsByName("New")).thenReturn(false);
    when(dataSourceGroupRepo.save(entity)).thenThrow(new DataIntegrityViolationException("unique"));

    var ex =
        assertThrows(
            PersistenceException.class,
            () -> service.patchGroup(GROUP_ID, new DataSourceGroupPatchRequest("New", null)));

    assertEquals("A data source group with that name already exists", ex.getMessage());
  }

  @Test
  void deleteGroup_whenFound_deletesById() throws PersistenceException {
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(true);

    service.deleteGroup(GROUP_ID);

    verify(dataSourceGroupRepo).deleteById(GROUP_ID);
  }

  @Test
  void deleteGroup_whenNotFound_throwsPersistenceExceptionAndDoesNotDelete() {
    when(dataSourceGroupRepo.existsById(GROUP_ID)).thenReturn(false);

    var ex = assertThrows(PersistenceException.class, () -> service.deleteGroup(GROUP_ID));

    assertEquals("Could not find data source group: " + GROUP_ID, ex.getMessage());
    verify(dataSourceGroupRepo, never()).deleteById(any());
  }

  private static DataSourceGroupEntity dataSourceGroup(UUID id, String name, String description) {
    var entity = new DataSourceGroupEntity();
    entity.setId(id);
    entity.setName(name);
    entity.setDescription(description);
    return entity;
  }
}
