// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.model;

import java.time.Instant;
import org.geojson.FeatureCollection;

public record EventHistoryResponse(
    String objectId, Instant from, Instant to, SamplingMeta sampling, FeatureCollection data) {}
