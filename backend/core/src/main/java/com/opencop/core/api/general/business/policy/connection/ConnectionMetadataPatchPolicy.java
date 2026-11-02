// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.policy.connection;

import com.opencop.core.api.general.business.model.connection.ConnectionMetadata;
import com.opencop.core.api.general.business.model.connection.ConnectionMetadataPatch;
import com.opencop.core.api.general.business.model.connection.ValidatedConnectionMetadataPatch;
import com.opencop.core.api.general.business.policy.ResourceLifecyclePolicy;
import com.opencop.core.api.general.business.policy.connection.access.AccessModeFormatPolicy;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionExceptionCodes;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import com.opencop.core.sdk.model.datasource.AccessMode;

public final class ConnectionMetadataPatchPolicy {

  private static final String OPERATION = "patch connection metadata";

  public ValidatedConnectionMetadataPatch validate(
      ConnectionMetadataPatch patch, ConnectionMetadata current) {
    ResourceLifecyclePolicy.assertDataSourceActive(current.archivedAt(), OPERATION);

    if (patch.integration() != null) {
      throw new ConnectionManagementException(
          ConnectionExceptionCodes.INTEGRATION_PATCH_NOT_ALLOWED);
    }

    if (patch.name() == null
        && patch.defaultVisible() == null
        && patch.access() == null
        && patch.integration() == null) {
      throw new ConnectionManagementException(ConnectionExceptionCodes.NO_FIELDS_TO_UPDATE);
    }
    if (patch.name() != null) {
      ConnectionNamePolicy.require(patch.name());
    }

    var access = parseAccess(patch.access());
    return new ValidatedConnectionMetadataPatch(
        patch.name(), patch.defaultVisible(), access, patch.integration());
  }

  private static AccessMode parseAccess(String rawAccess) {
    if (rawAccess == null) {
      return null;
    }
    return AccessModeFormatPolicy.parse(rawAccess);
  }
}
