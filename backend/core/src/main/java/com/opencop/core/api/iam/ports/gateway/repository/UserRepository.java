// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.gateway.repository;

import java.util.List;

public interface UserRepository<O> {

  O getUser(Long userId);

  List<O> getAllUsers();

  O createUser(String username);

  O addGroupToUser(Long userId, Long groupId);

  O removeGroupFromUser(Long userId, Long groupId);

  O addAttributeToUser(Long userId, Long attributeId);

  O removeAttributeFromUser(Long userId, Long attributeId);

  boolean userHasAttribute(Long userId, Long attributeId);

  O addPermissionToUser(Long userId, Long permissionId);

  O removePermissionFromUser(Long userId, Long permissionId);

  boolean userHasPermission(Long userId, Long permissionId);

  void deleteUser(Long userId);
}
