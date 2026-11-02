// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.opencop;

import java.util.UUID;

public record PersistRemarkRequest(UUID sourceId, String objectId, String content, UUID userId) {}
