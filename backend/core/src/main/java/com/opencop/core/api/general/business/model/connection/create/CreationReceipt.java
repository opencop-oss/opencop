// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.connection.create;

import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import java.util.Objects;

/**
 * What creating a connection hands back about it for its transport, beyond the connection itself.
 * Each transport has its own, so whatever one transport issues only once, such as Kafka's producer
 * credentials, travels to the requester without the generic creation knowing of it.
 */
public sealed interface CreationReceipt {

  /** A TCP connection issues nothing beyond the connection itself. */
  record Tcp() implements CreationReceipt {}

  /** A UDP connection issues nothing beyond the connection itself. */
  record Udp() implements CreationReceipt {}

  /** An RTSP connection issues nothing beyond the connection itself. */
  record Rtsp() implements CreationReceipt {}

  /**
   * A Kafka connection issues the credentials its producer publishes with. They exist in this
   * receipt only: the stored connection keeps the topic and user, never the password.
   *
   * @param producerCredentials the topic and producer credentials provisioned on the broker
   */
  record Kafka(KafkaCredentials producerCredentials) implements CreationReceipt {

    public Kafka {
      Objects.requireNonNull(producerCredentials, "producerCredentials");
    }
  }
}
