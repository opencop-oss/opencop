// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.technical.gateway;

import com.opencop.core.api.general.business.model.user.InternalUser;
import com.opencop.core.sdk.model.iam.user.UserReadFilter;
import com.opencop.core.sdk.ports.gateway.idp.IdpException;
import com.opencop.core.sdk.technical.gateway.db.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface UserService {

  boolean exists(UUID id);

  InternalUser get(UUID id) throws IdpException;

  InternalUser get(String idpUserId, String issuer) throws IdpException;

  /** Returns a filtered, sorted page of users resolved server-side. */
  PageResponse<InternalUser> getPage(UserReadFilter filter, Pageable pageable);
}
