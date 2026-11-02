// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.history;

import com.opencop.core.sdk.model.history.HistoricalJobStage;
import io.lettuce.core.RedisException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

@Component
public class HistoricalExecutionFailureClassifier {

  public HistoricalJobStage classify(Throwable exception) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = exception;
        current != null && visited.add(current);
        current = current.getCause()) {
      HistoricalJobStage stage =
          switch (current) {
            case InterruptedException _ -> HistoricalJobStage.EXECUTION_FAILED_INTERRUPTED;
            case TimeoutException _ -> HistoricalJobStage.EXECUTION_FAILED_TIMEOUT;
            case QueryTimeoutException _ -> HistoricalJobStage.EXECUTION_FAILED_TIMEOUT;
            case DataAccessException _ -> HistoricalJobStage.EXECUTION_FAILED_STORAGE;
            case RedisException _ -> HistoricalJobStage.EXECUTION_FAILED_STORAGE;
            default -> null;
          };
      if (stage != null) {
        return stage;
      }
    }
    return HistoricalJobStage.EXECUTION_FAILED_UNEXPECTED;
  }
}
