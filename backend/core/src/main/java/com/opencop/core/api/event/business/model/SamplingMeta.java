// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

public record SamplingMeta(
    String strategy,
    int maxPointsRequested,
    String bucketSize,
    long totalPointsFound,
    long usedPointsReturned) {}
