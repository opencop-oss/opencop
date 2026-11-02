// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.connection.extension;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.spec.ConnectionSpec;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionExtensionContext;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionRecordStore;
import com.opencop.core.sdk.ports.integration.connection.extension.ConnectionSecrets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** What one call into an extension knows about its connection. */
record ExtensionInvocationContext(
    UUID sourceId,
    IntegrationConnectionSchemaReference integration,
    Object settings,
    Optional<ConnectionSpec> setup,
    ConnectionSecrets secrets,
    ConnectionRecordStore records,
    UUID actingUserId)
    implements ConnectionExtensionContext<Object> {

  ExtensionInvocationContext {
    Objects.requireNonNull(settings, "settings");
  }
}
