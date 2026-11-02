// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp;

import com.opencop.core.sdk.business.model.iam.access.GroupRef;
import com.opencop.core.sdk.business.model.iam.access.RoleRef;
import com.opencop.core.sdk.business.model.iam.identity.User;
import java.util.List;
import java.util.Map;

public interface IdpUserService {

  User getUser(String userId) throws IdpException;

  List<User> listUsers() throws IdpException;

  String createUser(Map<String, Object> createRequest) throws IdpException;

  void delete(String userId) throws IdpException;

  void update(String userId, Map<String, Object> updateRequest) throws IdpException;

  List<GroupRef> getUserGroups(String userId) throws IdpException;

  void assignToGroup(String userId, String groupId) throws IdpException;

  void deleteFromGroup(String userId, String groupId) throws IdpException;

  List<RoleRef> getRoles(String userId) throws IdpException;

  void assignRole(String userId, Map<String, List<String>> roles) throws IdpException;

  void deleteRole(String userId, Map<String, List<String>> roles) throws IdpException;
}
