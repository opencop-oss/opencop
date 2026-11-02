// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.map;

import java.time.Instant;
import java.util.UUID;

public record MapCapture(
    UUID id, String name, MapView mapView, Instant createdAt, Instant updatedAt) {}
