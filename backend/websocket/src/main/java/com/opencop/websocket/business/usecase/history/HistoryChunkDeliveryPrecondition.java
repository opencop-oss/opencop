// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.history;

import com.opencop.core.sdk.model.history.HistoryChunkStatusEvent;
import java.util.regex.Pattern;

public final class HistoryChunkDeliveryPrecondition {

  /**
   * Defensive guard for pub/sub-supplied payload keys. The full key shape produced by {@code
   * HistoricalCacheIdentity} is documented as:
   *
   * <pre>
   * opencop:history:payload:&lt;subjectType&gt;:&lt;uuid&gt;:&lt;resolution&gt;:&lt;planningVersion&gt;:&lt;samplingVersion&gt;:&lt;filtersFingerprint&gt;:&lt;chunkFromISO&gt;[:part:&lt;part-index&gt;]
   * </pre>
   *
   * The shape evolves over time, so this validator does not pin every component. It enforces the
   * required prefix and a safe-character allowlist before any Redis fetch or websocket send.
   */
  private static final Pattern PAYLOAD_KEY_PATTERN =
      Pattern.compile(
          "^opencop:history:payload:[A-Za-z0-9_]+:[0-9a-fA-F\\-]{36}:[A-Za-z0-9_:.\\-]+$");

  public HistoryChunkDeliveryPreconditionResult validate(HistoryChunkStatusEvent event) {
    if (!PAYLOAD_KEY_PATTERN.matcher(event.payloadKey()).matches()) {
      return HistoryChunkDeliveryPreconditionResult.failedResult(
          HistoryChunkDeliveryFailureReason.PAYLOAD_KEY_INVALID);
    }
    if (event.jobId() == null) {
      return HistoryChunkDeliveryPreconditionResult.failedResult(
          HistoryChunkDeliveryFailureReason.JOB_ID_MISSING);
    }
    return HistoryChunkDeliveryPreconditionResult.validResult();
  }
}
