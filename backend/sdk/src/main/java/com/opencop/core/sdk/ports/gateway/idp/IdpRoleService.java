// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp;

import com.opencop.core.sdk.business.model.iam.access.Role;
import java.util.List;

public interface IdpRoleService {

  Role getRole(int page, int size, String search) throws IdpException;

  List<Role> listRoles(int page, int size, String search) throws IdpException;
}
