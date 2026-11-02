// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.export;

import java.util.UUID;

interface ActiveFeatureExportProjection {

  UUID getId();

  UUID getSourceId();

  String getSourceType();

  String getObjectId();

  String getPayload();
}
