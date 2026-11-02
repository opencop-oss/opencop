// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.export;

import com.opencop.core.sdk.model.SubjectType;
import java.util.UUID;

public record ActiveFeatureExportRow(
    UUID id, UUID sourceId, SubjectType sourceType, String objectId, String payload) {}
