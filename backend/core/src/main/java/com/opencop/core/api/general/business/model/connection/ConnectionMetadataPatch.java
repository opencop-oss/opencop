// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;

public record ConnectionMetadataPatch(
    String name,
    Boolean defaultVisible,
    String access,
    IntegrationConnectionSchemaReference integration) {}
