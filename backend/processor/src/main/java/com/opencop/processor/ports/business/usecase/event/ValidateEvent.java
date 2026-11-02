// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.usecase.event;

import com.opencop.processor.business.model.event.EventEnvelope;
import java.util.List;

/**
 * Validates a batch of events and republishes the ones that survive.
 *
 * <p>Takes envelopes rather than bare events so metadata attached upstream — feature provenance,
 * today — reaches the next stage. Implementations must re-emit an envelope's metadata unchanged on
 * republish, and must not re-emit it for an event they reject.
 */
public interface ValidateEvent<I> {

  void validate(List<EventEnvelope<I>> input);
}
