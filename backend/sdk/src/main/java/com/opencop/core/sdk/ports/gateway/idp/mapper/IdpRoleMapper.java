// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.ports.gateway.idp.mapper;

import com.opencop.core.sdk.business.model.iam.access.Role;

public interface IdpRoleMapper<I> {

  Role toRoleBusinessModel(I role);
}
