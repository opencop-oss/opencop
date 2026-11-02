// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.business.observability.mapping;

import com.opencop.core.sdk.ports.gateway.mapping.ingress.DecodedMappingResult;
import com.opencop.processor.business.model.mapping.RealtimeMappingAttempt;

public interface RealtimeMappingOutcomeObserver {

  void observe(RealtimeMappingAttempt attempt, DecodedMappingResult result);

  /** Records a decode attempt that threw before producing a {@link DecodedMappingResult}. */
  void observeThrown(RealtimeMappingAttempt attempt);
}
