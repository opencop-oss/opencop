// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.gateway.group;

import com.opencop.core.api.general.technical.model.group.GroupCreateRequest;
import com.opencop.core.api.general.technical.model.group.GroupMemberResponse;
import com.opencop.core.api.general.technical.model.group.GroupMembersPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupPatchRequest;
import com.opencop.core.api.general.technical.model.group.GroupResponse;
import com.opencop.core.api.general.technical.model.group.GroupSearchResult;
import com.opencop.core.sdk.model.iam.group.GroupReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface GroupService {

  List<GroupResponse> listGroups();

  /**
   * Paged, searchable user groups under the minimal picker projection. Unlike {@link
   * #listGroups()}, which backs the admin directory, this is reachable by any authenticated caller,
   * so it returns {@link GroupSearchResult} rather than the full response model.
   */
  PageResponse<GroupSearchResult> getPage(GroupReadFilter filter, Pageable pageable);

  GroupResponse createGroup(GroupCreateRequest request) throws PersistenceException;

  void deleteGroup(UUID groupId) throws PersistenceException;

  List<GroupMemberResponse> getMembers(UUID groupId) throws PersistenceException;

  void patchMembers(UUID groupId, GroupMembersPatchRequest request) throws PersistenceException;

  GroupResponse patchGroup(UUID groupId, GroupPatchRequest request) throws PersistenceException;
}
