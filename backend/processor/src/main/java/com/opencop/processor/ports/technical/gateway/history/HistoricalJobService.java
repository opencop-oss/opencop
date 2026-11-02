// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.core.sdk.model.job.HistoricalTracksJobSubmission;

public interface HistoricalJobService {

  void accept(HistoricalTracksJobSubmission job);
}
