// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.integration.builtin.business.policy.kafka;

import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputException;
import com.opencop.core.sdk.ports.integration.connection.input.ConnectionInputFailure;

/**
 * A Kafka producer's throttle is a whole, positive number of bytes per second. Rejecting anything
 * else here keeps a bad rate from reaching the broker after its topic and user are provisioned.
 */
public final class KafkaProducerByteRatePolicy {

  /** The declared field a Kafka producer reads its throttle from. */
  public static final String PRODUCER_BYTE_RATE_FIELD = "producerByteRate";

  public static final String INVALID_RATE = "INVALID_PRODUCER_BYTE_RATE";

  private KafkaProducerByteRatePolicy() {}

  /**
   * The throttle in bytes per second, or null when the request supplied none and the broker's
   * default applies.
   */
  public static Long producerByteRate(String supplied) throws ConnectionInputException {
    if (supplied == null || supplied.isBlank()) {
      return null;
    }
    try {
      var rate = Long.parseLong(supplied.strip());
      if (rate > 0) {
        return rate;
      }
    } catch (NumberFormatException _) {
      // Reported below, as for a rate that is not positive.
    }
    throw ConnectionInputException.rejecting(
        PRODUCER_BYTE_RATE_FIELD,
        ConnectionInputFailure.INVALID_INPUT,
        INVALID_RATE,
        "Producer byte rate must be a whole number greater than 0");
  }
}
