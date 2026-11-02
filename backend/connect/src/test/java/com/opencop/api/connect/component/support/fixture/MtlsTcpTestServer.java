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
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/** Local mTLS TCP peer for component tests. */
public final class MtlsTcpTestServer implements AutoCloseable {

  private final SSLServerSocket serverSocket;
  private final ExecutorService acceptExecutor;
  private final CountDownLatch clientConnected = new CountDownLatch(1);

  private volatile SSLSocket clientSocket;
  private volatile InputStream input;
  private volatile OutputStream output;

  public MtlsTcpTestServer(
      InputStream serverKeystore,
      char[] serverPassword,
      InputStream truststore,
      char[] truststorePassword)
      throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        keyManagers(serverKeystore, serverPassword),
        trustManagers(truststore, truststorePassword),
        null);

    serverSocket =
        (SSLServerSocket)
            sslContext
                .getServerSocketFactory()
                .createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    serverSocket.setNeedClientAuth(true);

    acceptExecutor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "MtlsTcpTestServer-accept"));
    acceptExecutor.execute(this::acceptClient);
  }

  public int port() {
    return serverSocket.getLocalPort();
  }

  public SSLSocket awaitClient(Duration timeout) throws InterruptedException {
    if (!clientConnected.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      throw new AssertionError("Timed out waiting for mTLS TCP client within " + timeout);
    }
    return clientSocket;
  }

  public void send(byte[] payload, Duration timeout) throws Exception {
    awaitClient(timeout);
    output.write(payload);
    output.flush();
  }

  public byte[] awaitReceivedBytes(int expectedLength, Duration timeout) throws Exception {
    awaitClient(timeout);

    long deadlineNanos = System.nanoTime() + timeout.toNanos();
    var buffer = new ByteArrayOutputStream(expectedLength);

    while (buffer.size() < expectedLength && System.nanoTime() < deadlineNanos) {
      byte[] chunk = input.readNBytes(expectedLength - buffer.size());
      if (chunk.length == 0) {
        if (clientSocket == null || clientSocket.isClosed()) {
          break;
        }
        Thread.sleep(10);
        continue;
      }
      buffer.write(chunk);
    }

    if (buffer.size() != expectedLength) {
      throw new AssertionError(
          "Expected "
              + expectedLength
              + " bytes from mTLS TCP client but received "
              + buffer.size()
              + " within "
              + timeout);
    }
    return buffer.toByteArray();
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
      SSLSocket accepted = (SSLSocket) serverSocket.accept();
      accepted.setSoTimeout(200);
      accepted.startHandshake();
      clientSocket = accepted;
      input = new BufferedInputStream(accepted.getInputStream());
      output = new BufferedOutputStream(accepted.getOutputStream());
      clientConnected.countDown();
    } catch (Exception e) {
      if (!serverSocket.isClosed()) {
        throw new IllegalStateException("Failed to accept mTLS TCP client", e);
      }
    }
  }

  private static KeyManager[] keyManagers(InputStream keystore, char[] password) throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(keystore, password);
    KeyManagerFactory factory = KeyManagerFactory.getInstance("SunX509");
    factory.init(keyStore, password);
    return factory.getKeyManagers();
  }

  private static TrustManager[] trustManagers(InputStream truststore, char[] password)
      throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(truststore, password);
    TrustManagerFactory factory = TrustManagerFactory.getInstance("SunX509");
    factory.init(keyStore);
    return factory.getTrustManagers();
  }
}
