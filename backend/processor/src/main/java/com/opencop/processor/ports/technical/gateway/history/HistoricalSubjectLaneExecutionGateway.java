// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.ports.technical.gateway.history;

import com.opencop.processor.business.model.history.HistoricalSubjectExecutionMeta;

@FunctionalInterface
public interface HistoricalSubjectLaneExecutionGateway {

  void execute(HistoricalSubjectExecutionMeta laneMetadata, Runnable laneStarted);
}
