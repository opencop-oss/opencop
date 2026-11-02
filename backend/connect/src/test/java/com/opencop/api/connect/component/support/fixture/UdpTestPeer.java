// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.fixture;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;

/** Local UDP peer for component tests that need a real datagram sender or receiver. */
public final class UdpTestPeer implements AutoCloseable {

  private final DatagramSocket socket;

  public UdpTestPeer() throws IOException {
    socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
    socket.setSoTimeout(200);
  }

  public int port() {
    return socket.getLocalPort();
  }

  public void send(int port, byte[] payload) throws IOException {
    DatagramPacket packet =
        new DatagramPacket(payload, payload.length, InetAddress.getByName("127.0.0.1"), port);
    socket.send(packet);
  }

  public byte[] awaitPacket(Duration timeout, int maxPacketSize) throws IOException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    byte[] buffer = new byte[maxPacketSize];

    while (System.nanoTime() < deadlineNanos) {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(packet);
        byte[] copy = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), 0, copy, 0, packet.getLength());
        return copy;
      } catch (SocketTimeoutException _) {
      }
    }

    throw new AssertionError("Timed out waiting for UDP packet within " + timeout);
  }

  public void assertNoPacket(Duration timeout, int maxPacketSize) throws IOException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    byte[] buffer = new byte[maxPacketSize];

    while (System.nanoTime() < deadlineNanos) {
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(packet);
        throw new AssertionError(
            "Expected no UDP packet within "
                + timeout
                + " but received "
                + packet.getLength()
                + " bytes");
      } catch (SocketTimeoutException _) {
      }
    }
  }

  @Override
  public void close() {
    socket.close();
  }
}
