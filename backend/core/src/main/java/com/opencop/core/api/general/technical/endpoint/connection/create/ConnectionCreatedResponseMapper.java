// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationResult;
import com.opencop.core.api.general.business.model.connection.create.CreationReceipt;

/** Translates a creation's result into the v2 answer of its transport. */
final class ConnectionCreatedResponseMapper {

  private ConnectionCreatedResponseMapper() {}

  static ConnectionCreatedResponse toResponse(ConnectionCreationResult result) {
    var connection = result.connection();
    return switch (result.receipt()) {
      case CreationReceipt.Tcp _ -> new ConnectionCreatedResponse.Tcp(connection);
      case CreationReceipt.Udp _ -> new ConnectionCreatedResponse.Udp(connection);
      case CreationReceipt.Rtsp _ -> new ConnectionCreatedResponse.Rtsp(connection);
      case CreationReceipt.Kafka kafka ->
          new ConnectionCreatedResponse.Kafka(connection, kafka.producerCredentials());
    };
  }
}
