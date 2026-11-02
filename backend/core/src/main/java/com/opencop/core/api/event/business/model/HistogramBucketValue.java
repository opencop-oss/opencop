// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import java.util.UUID;

public record HistogramBucketValue(UUID key, HistogramMetric metric, long count) {}
