// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.media;

import java.util.UUID;

public interface RetrieveMedia {

  /** Retrieves media with access checks enforced for the given user. */
  MediaView getAsUser(UUID mediaId, UUID requestingUserId) throws MediaNotFoundException;

  /**
   * Retrieves media without access checks. Only valid after the caller has independently verified
   * resource-scoped authorization (e.g., layer membership, event membership) and IDOR checks.
   */
  MediaView getWithoutAccessCheck(UUID mediaId) throws MediaNotFoundException;
}
