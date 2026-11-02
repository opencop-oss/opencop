// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.opencop.core.api.general.business.model.connection.create.ConnectionCreationResult;
import com.opencop.core.api.general.business.model.connection.create.CreationReceipt;
import com.opencop.core.sdk.model.connection.ConnectionInfo;
import com.opencop.core.sdk.ports.gateway.kafka.KafkaCredentials;
import org.junit.jupiter.api.Test;

class ConnectionCreatedResponseMapperTest {

  private final ConnectionInfo connection = mock(ConnectionInfo.class);

  @Test
  void answersEachTransportWithItsOwnResponse() {
    var credentials = mock(KafkaCredentials.class);

    assertThat(ConnectionCreatedResponseMapper.toResponse(result(new CreationReceipt.Udp())))
        .isEqualTo(new ConnectionCreatedResponse.Udp(connection));
    assertThat(ConnectionCreatedResponseMapper.toResponse(result(new CreationReceipt.Rtsp())))
        .isEqualTo(new ConnectionCreatedResponse.Rtsp(connection));
    assertThat(
            ConnectionCreatedResponseMapper.toResponse(
                result(new CreationReceipt.Kafka(credentials))))
        .isEqualTo(new ConnectionCreatedResponse.Kafka(connection, credentials));
  }

  private ConnectionCreationResult result(CreationReceipt receipt) {
    return new ConnectionCreationResult(connection, receipt);
  }
}
