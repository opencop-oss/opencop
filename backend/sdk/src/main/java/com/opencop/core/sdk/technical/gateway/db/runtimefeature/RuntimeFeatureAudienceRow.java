// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import java.time.Instant;
import java.util.UUID;

interface RuntimeFeatureAudienceRow {

  String getFeatureName();

  RuntimeFeatureConsumerClassEntity getConsumerClass();

  RuntimeFeatureAudienceModeEntity getAudienceMode();

  long getRevision();

  Instant getUpdatedAt();

  UUID getUserGroupId();
}
