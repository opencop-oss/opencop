// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = LayerCopyJobSubmission.class, name = "LAYER_COPY"),
  @JsonSubTypes.Type(value = LayerMergeJobSubmission.class, name = "LAYER_MERGE"),
  @JsonSubTypes.Type(value = GeoCacheHydrationJobSubmission.class, name = "GEO_CACHE_HYDRATION"),
  @JsonSubTypes.Type(value = HistoricalTracksJobSubmission.class, name = "HISTORICAL_TRACKS"),
  @JsonSubTypes.Type(value = GhostEvictionJobSubmission.class, name = "GHOST_EVICTION")
})
public sealed interface JobSubmission
    permits GeoCacheHydrationJobSubmission,
        GhostEvictionJobSubmission,
        HistoricalTracksJobSubmission,
        LayerCopyJobSubmission,
        LayerMergeJobSubmission {

  String TOPIC = "opencop.jobs";

  java.util.UUID jobId();

  JobType jobType();

  JobIssuer issuer();

  @JsonIgnore
  default String topic() {
    return TOPIC;
  }

  @JsonIgnore
  String key();
}
