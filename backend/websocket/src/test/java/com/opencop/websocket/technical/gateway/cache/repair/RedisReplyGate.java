// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.gateway.cache.repair;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Opaque TCP forwarding; only this test client's downstream delivery can be held. */
final class RedisReplyGate implements AutoCloseable {
  private enum Delivery {
    OPEN,
    HOLD_ALL,
    HOLD_NEW
  }

  private final ServerSocket listener;
  private final String upstreamHost;
  private final int upstreamPort;
  private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
  private final java.util.concurrent.ExecutorService workers =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("redis-reply-gate-", 0).factory());
  private final AtomicInteger accepted = new AtomicInteger();
  private final AtomicReference<Throwable> failure = new AtomicReference<>();
  private Delivery delivery = Delivery.OPEN;
  private volatile boolean closed;

  RedisReplyGate(String host, int port) throws IOException {
    upstreamHost = host;
    upstreamPort = port;
    listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    workers.submit(this::accept);
  }

  int port() {
    return listener.getLocalPort();
  }

  int accepted() {
    return accepted.get();
  }

  Throwable failure() {
    return failure.get();
  }

  synchronized void holdReplies() {
    delivery = Delivery.HOLD_ALL;
  }

  synchronized void holdNewConnections() {
    delivery = Delivery.HOLD_NEW;
  }

  synchronized void release() {
    delivery = Delivery.OPEN;
    notifyAll();
  }

  private void accept() {
    while (!closed) {
      try {
        var downstream = listener.accept();
        sockets.add(downstream);
        var upstream = new Socket();
        sockets.add(upstream);
        upstream.connect(new InetSocketAddress(upstreamHost, upstreamPort), 5_000);
        downstream.setTcpNoDelay(true);
        upstream.setTcpNoDelay(true);
        boolean freshHeld;
        synchronized (this) {
          freshHeld = delivery == Delivery.HOLD_NEW;
        }
        accepted.incrementAndGet();
        workers.submit(() -> forward(downstream, upstream, false, freshHeld));
        workers.submit(() -> forward(upstream, downstream, true, freshHeld));
      } catch (IOException exception) {
        if (!closed) failure.compareAndSet(null, exception);
      }
    }
  }

  private void forward(Socket from, Socket to, boolean reply, boolean freshHeld) {
    try {
      var input = from.getInputStream();
      var output = to.getOutputStream();
      var buffer = new byte[8_192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (reply) {
          synchronized (this) {
            while (!closed
                && (delivery == Delivery.HOLD_ALL || (freshHeld && delivery == Delivery.HOLD_NEW)))
              wait();
          }
        }
        if (closed) return;
        output.write(buffer, 0, count);
        output.flush();
      }
    } catch (IOException ignored) {
      // The client owns disconnect/reconnect; either direction may observe its close first.
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      closeSocket(from);
      closeSocket(to);
    }
  }

  private void closeSocket(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    sockets.remove(socket);
  }

  @Override
  public void close() throws Exception {
    closed = true;
    release();
    listener.close();
    sockets.forEach(this::closeSocket);
    workers.shutdownNow();
    if (!workers.awaitTermination(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS))
      throw new IllegalStateException("Redis reply gate workers did not terminate");
  }
}
