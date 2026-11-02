// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp.mapper;

import com.opencop.core.sdk.business.model.iam.access.Group;
import com.opencop.core.sdk.business.model.iam.access.RoleRef;

public interface IdpGroupMapper<G, R> {

  Group toBusinessDto(G input);

  RoleRef toRoleBusinessDto(R role);
}
