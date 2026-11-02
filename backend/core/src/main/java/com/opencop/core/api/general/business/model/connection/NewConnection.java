// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.api.general.business.model.connection.setup.SetupPrefill;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.settings.SettingsDocument;
import java.util.Objects;
import java.util.UUID;

/**
 * What the persistence helpers store for a new connection alongside its transport.
 *
 * <p>A creation enters as a {@link NewSocketConnection} or {@link NewKafkaConnection}, whose
 * validated setup the persistence helpers turn into this form, so both kinds store a connection the
 * same way.
 *
 * <p>{@code access} carries the connection's mode and the mappers it holds for it.
 *
 * <p>{@code integration} is the one the connection is stored with; every stored connection belongs
 * to one.
 *
 * <p>{@code settings} are the connection's normalised integration settings overrides, already
 * validated against {@code integration}; empty when it overrides none, and then nothing is stored.
 *
 * <p>{@code prefill} is the non-secret part of the setup that created it, stored to fill a later
 * setup form with.
 */
public record NewConnection(
    UUID sourceId,
    String sourceName,
    ConnectionAccess access,
    IntegrationConnectionSchemaReference integration,
    SettingsDocument settings,
    SetupPrefill prefill) {

  public NewConnection {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(integration, "integration");
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(prefill, "prefill");
  }
}
