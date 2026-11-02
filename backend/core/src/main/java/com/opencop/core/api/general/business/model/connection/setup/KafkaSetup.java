// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.setup;

import com.opencop.core.api.general.business.model.connection.access.ConnectionAccess;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.business.model.integration.connection.material.kafka.KafkaSecurityMaterial;
import java.util.Objects;

/**
 * A Kafka connection's setup once every selection in it is valid. Unlike a socket setup it has no
 * spec yet: its broker resources are provisioned as it is stored, from {@code security}.
 *
 * @param access the access mode and the mapper the connection reads with
 * @param integration the connection schema version the connection binds to
 * @param prefill the non-secret part of the setup, to fill a later setup form with
 * @param security what the extractor produced for the connection's broker resources
 */
public record KafkaSetup(
    ConnectionAccess access,
    IntegrationConnectionSchemaReference integration,
    SetupPrefill prefill,
    KafkaSecurityMaterial security) {

  public KafkaSetup {
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(integration, "integration");
    Objects.requireNonNull(prefill, "prefill");
    Objects.requireNonNull(security, "security");
  }
}
