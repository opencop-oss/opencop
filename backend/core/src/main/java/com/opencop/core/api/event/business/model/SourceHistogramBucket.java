// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import java.time.Instant;
import java.util.List;

public record SourceHistogramBucket(
    Instant start, Instant end, List<HistogramBucketValue> values) {}
