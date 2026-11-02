// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.socket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.opencop.api.connect.business.connector.ConnectorListener;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UdpConnectorTest {

  @Test
  void testUdpConnectorReceivesData() throws Exception {
    UUID sourceId = UUID.randomUUID();
    int port = 12345;
    UdpConnector connector = new UdpConnector(sourceId, port);
    ConnectorListener listener = mock(ConnectorListener.class);
    connector.addListener(listener);

    connector.start();

    // Give it a moment to start the listener
    verify(listener, timeout(2000)).onStateChange(eq(sourceId), eq(ConnectorState.CONNECTED));

    // Send a UDP packet
    byte[] data = "Hello UDP".getBytes();
    try (DatagramSocket socket = new DatagramSocket()) {
      DatagramPacket packet =
          new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), port);
      socket.send(packet);
    }

    // Verify message received
    ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
    verify(listener, timeout(2000)).onMessage(eq(sourceId), captor.capture());
    assertArrayEquals(data, captor.getValue());

    connector.stop();
    verify(listener, timeout(2000)).onStateChange(eq(sourceId), eq(ConnectorState.STOPPED));
  }
}
