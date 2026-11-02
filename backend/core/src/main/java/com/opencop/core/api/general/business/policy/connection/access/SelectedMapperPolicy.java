// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection.access;

import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import java.util.Set;
import java.util.UUID;

/**
 * A connection never commits with an archived mapper, even one archived after its selection was
 * validated: the mapper would stop decoding or encoding for it the moment it started.
 */
public final class SelectedMapperPolicy {

  private SelectedMapperPolicy() {}

  /**
   * @param archived the selected mappers whose latest revision is archived
   */
  public static void assertNoneArchived(Set<UUID> archived) {
    if (!archived.isEmpty()) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.MAPPER_ARCHIVED);
    }
  }
}
