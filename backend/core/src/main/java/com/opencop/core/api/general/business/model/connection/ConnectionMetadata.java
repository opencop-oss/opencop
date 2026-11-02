// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import java.time.Instant;
import java.util.UUID;

public record ConnectionMetadata(
    UUID id,
    String name,
    UUID ingressMapperId,
    TransportKind transportKind,
    AccessMode access,
    boolean enabled,
    Instant archivedAt,
    boolean defaultVisible,
    IntegrationConnectionSchemaReference integration) {}
