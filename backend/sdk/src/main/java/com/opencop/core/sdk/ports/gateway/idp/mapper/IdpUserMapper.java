// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp.mapper;

import com.opencop.core.sdk.business.model.iam.access.GroupRef;
import com.opencop.core.sdk.business.model.iam.access.RoleRef;
import com.opencop.core.sdk.business.model.iam.identity.User;

public interface IdpUserMapper<I, G, R> {

  User toUserBusinessDto(I input);

  GroupRef toGroupBusinessDto(G group);

  RoleRef toRoleBusinessDto(R role);
}
