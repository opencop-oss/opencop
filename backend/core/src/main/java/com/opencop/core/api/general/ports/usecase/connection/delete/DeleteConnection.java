// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.connection.delete;

import com.opencop.core.sdk.business.model.iam.access.SourceUser;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.Set;
import java.util.UUID;

public interface DeleteConnection {

  /**
   * Archives the source and returns the users who held access to it, so the caller can notify them.
   * The access list is resolved inside the authorized call: reading it beforehand would run an
   * unauthenticated existence check ahead of the authorization decision.
   */
  Set<SourceUser> delete(UUID sourceId, OpenCopPrincipal authentication);
}
