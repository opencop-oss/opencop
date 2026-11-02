// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection;

import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.datasource.AccessMode;

public record ValidatedConnectionMetadataPatch(
    String name,
    Boolean defaultVisible,
    AccessMode access,
    IntegrationConnectionSchemaReference integration) {}
