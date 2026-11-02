// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp;

import com.opencop.core.sdk.business.model.iam.access.Group;
import com.opencop.core.sdk.business.model.iam.access.RoleRef;
import java.util.List;
import java.util.Map;

public interface IdpGroupService {

  Group getGroup(String groupId) throws IdpException;

  List<Group> listGroups() throws IdpException;

  String createGroup(Map<String, Object> createRequest) throws IdpException;

  void delete(String groupId) throws IdpException;

  void update(String groupId, Map<String, Object> updateRequest) throws IdpException;

  // void assignToUser(String userId, UsersEndpoint.AssignGroupsRequest request);
  List<RoleRef> getRoles(String groupId) throws IdpException;

  void assignRole(String groupId, Map<String, List<String>> roles) throws IdpException;

  void deleteRole(String groupId, Map<String, List<String>> roles) throws IdpException;
}
