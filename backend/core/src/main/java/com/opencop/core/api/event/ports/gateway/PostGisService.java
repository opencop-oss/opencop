// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.ports.gateway;

import com.opencop.core.api.event.business.model.BoundingBox;
import com.opencop.core.api.event.business.model.EventDetails;
import com.opencop.core.api.event.business.model.EventHistoryResponse;
import com.opencop.core.api.event.business.model.SourceHistogramResponse;
import com.opencop.core.sdk.technical.gateway.db.PersistenceException;
import com.opencop.core.sdk.technical.model.opencop.OpenCopProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.geojson.Feature;

public interface PostGisService {

  /**
   * Scope-verifying find: a revision outside this source/object or a disagreeing payload is absent.
   * Absence is an ordinary state for callers that embed related events.
   */
  Optional<EventDetails> findEventDetails(UUID sourceId, String objectId, UUID eventId)
      throws PersistenceException;

  /** Finds the latest stored event; absence is an ordinary state for callers that embed. */
  Optional<EventDetails> findLatestEventDetails(UUID sourceId, String objectId)
      throws PersistenceException;

  EventHistoryResponse getEventHistoryBucketed(
      UUID sourceId, String objectId, Instant from, Instant to, int maxPoints)
      throws PersistenceException;

  EventHistoryResponse getEventHistoryAsLine(
      UUID sourceId, String objectId, Instant from, Instant to, int maxPoints)
      throws PersistenceException;

  SourceHistogramResponse getSourceHistogram(
      UUID sourceId, Instant from, Instant to, int bars, Duration bucketWidth, BoundingBox bbox)
      throws PersistenceException;

  OpenCopProperties getDetails(UUID id) throws PersistenceException;

  OpenCopProperties getLatestDetails(UUID sourceId, String objectId) throws PersistenceException;

  Feature getLatestFeature(UUID sourceId, String objectId) throws PersistenceException;
}
