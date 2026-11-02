// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.business.usecase;

import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import com.opencop.core.sdk.technical.security.OpenCopPrincipal;
import java.util.UUID;

/**
 * Reads an event's details for a principal permitted to see them.
 *
 * <p>Opening a detail view is itself layer activity, so implementations record it. That is part of
 * the contract rather than an adapter's concern: the rule that an authorized read counts as use
 * belongs with the read, not with whichever transport happened to ask for it.
 */
public interface RetrieveEventDetailHandler {

  /** Details of one event by its id. */
  OpenCopProperties getDetails(OpenCopPrincipal principal, UUID eventId)
      throws PersistenceException;

  /** Newest details for an object on a source, which clients poll while a panel stays open. */
  OpenCopProperties getLatestDetails(OpenCopPrincipal principal, UUID sourceId, String objectId)
      throws PersistenceException;
}
