// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.map;

public record MapView(
    double bearing, double latitude, double longitude, double pitch, double zoom) {}
