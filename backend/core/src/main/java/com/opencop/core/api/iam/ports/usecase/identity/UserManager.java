// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.ports.usecase.identity;

import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface UserManager<O> {

  O get(UUID userId);

  /** Returns a filtered, sorted page of users resolved server-side. */
  PageResponse<O> listUsers(UserReadFilter filter, Pageable pageable);

  void updateAdminRole(UUID userId, boolean isAdmin);
}
