// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.component.support.fixture;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Local TCP peer for component tests that need a real socket server. */
public final class TcpTestServer implements AutoCloseable {

  private final ServerSocket serverSocket;
  private final ExecutorService acceptExecutor;
  private final AtomicInteger acceptedClients = new AtomicInteger();
  private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();

  private volatile Socket clientSocket;
  private volatile InputStream input;
  private volatile OutputStream output;
  private volatile Throwable acceptFailure;

  public TcpTestServer() throws IOException {
    serverSocket = new ServerSocket();
    serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
    acceptExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "TcpTestServer-accept"));
    acceptExecutor.execute(this::acceptClient);
  }

  public int port() {
    return serverSocket.getLocalPort();
  }

  public Socket awaitClient(Duration timeout) throws InterruptedException {
    return awaitClientCount(1, timeout);
  }

  public Socket awaitClientCount(int expectedClientCount, Duration timeout)
      throws InterruptedException {
    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      assertHealthy();
      if (acceptedClients.get() >= expectedClientCount && clientSocket != null) {
        return clientSocket;
      }
      Thread.sleep(20);
    }
    assertHealthy();
    if (acceptedClients.get() < expectedClientCount) {
      throw new AssertionError("Timed out waiting for TCP client within " + timeout);
    }
    return clientSocket;
  }

  public void send(byte[] payload, Duration timeout) throws IOException, InterruptedException {
    awaitClient(timeout);
    output.write(payload);
    output.flush();
  }

  public byte[] awaitReceivedBytes(int expectedLength, Duration timeout)
      throws IOException, InterruptedException {
    awaitClient(timeout);

    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    var buffer = new ByteArrayOutputStream(expectedLength);

    while (buffer.size() < expectedLength && System.nanoTime() < deadlineNanos) {
      try {
        byte[] chunk = input.readNBytes(expectedLength - buffer.size());
        if (chunk.length == 0) {
          if (clientSocket == null || clientSocket.isClosed()) {
            break;
          }
          Thread.sleep(10);
          continue;
        }
        buffer.write(chunk);
      } catch (SocketTimeoutException _) {
      }
    }

    if (buffer.size() != expectedLength) {
      throw new AssertionError(
          "Expected "
              + expectedLength
              + " bytes from TCP client but received "
              + buffer.size()
              + " within "
              + timeout);
    }
    return buffer.toByteArray();
  }

  public void assertNoReceivedBytes(Duration timeout) throws IOException, InterruptedException {
    awaitClient(timeout);

    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadlineNanos) {
      try {
        int received = input.read();
        if (received >= 0) {
          throw new AssertionError("Expected no bytes from TCP client within " + timeout);
        }
        return;
      } catch (SocketTimeoutException _) {
      }
    }
  }

  public void awaitClientDisconnected(int clientIndex, Duration timeout)
      throws IOException, InterruptedException {
    ClientConnection client = awaitClientSession(clientIndex, timeout);
    long deadlineNanos = System.nanoTime() + timeout.toNanos();

    while (System.nanoTime() < deadlineNanos) {
      assertHealthy();
      if (client.socket().isClosed()) {
        return;
      }
      try {
        int read = client.input().read();
        if (read == -1) {
          return;
        }
      } catch (SocketTimeoutException _) {
      }
      Thread.sleep(10);
    }

    throw new AssertionError(
        "Timed out waiting for TCP client " + clientIndex + " to disconnect within " + timeout);
  }

  public void closeClientConnection() throws IOException, InterruptedException {
    awaitClient(Duration.ofSeconds(1));
    clientSocket.close();
  }

  @Override
  public void close() {
    try {
      if (clientSocket != null) {
        clientSocket.close();
      }
    } catch (IOException _) {
    }
    try {
      serverSocket.close();
    } catch (IOException _) {
    }
    acceptExecutor.shutdownNow();
  }

  private void acceptClient() {
    try {
      while (!serverSocket.isClosed()) {
        Socket accepted = serverSocket.accept();
        accepted.setSoTimeout(200);
        clientSocket = accepted;
        input = new BufferedInputStream(accepted.getInputStream());
        output = new BufferedOutputStream(accepted.getOutputStream());
        clients.add(new ClientConnection(accepted, input, output));
        acceptedClients.incrementAndGet();
      }
    } catch (IOException e) {
      if (!serverSocket.isClosed()) {
        acceptFailure = e;
      }
    }
  }

  private ClientConnection awaitClientSession(int clientIndex, Duration timeout)
      throws InterruptedException {
    awaitClientCount(clientIndex + 1, timeout);
    return clients.get(clientIndex);
  }

  private void assertHealthy() {
    if (acceptFailure != null) {
      throw new AssertionError("TCP test server failed while accepting clients", acceptFailure);
    }
  }

  private record ClientConnection(Socket socket, InputStream input, OutputStream output) {}
}
