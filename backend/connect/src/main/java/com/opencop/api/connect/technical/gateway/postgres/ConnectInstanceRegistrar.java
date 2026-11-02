// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.technical.gateway.postgres;

import com.opencop.api.connect.business.connector.ConnectionManager;
import com.opencop.core.sdk.model.connect.ConnectInstanceInfo;
import com.opencop.core.sdk.ports.gateway.api.ConnectWorkerHealthWriter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers this Connect instance in Postgres with a TTL-based heartbeat.
 *
 * <p>Writes a Postgres worker health row with instance metadata (startedAt, connectionCount). The
 * Postgres heartbeat expiry uses a 45-second TTL and is refreshed every 15 seconds.
 *
 * <p>Also piggybacks {@link ConnectionManager#refreshAllRuntimeStatus()} on the heartbeat to keep
 * idle runtime observations fresh.
 */
public class ConnectInstanceRegistrar {

  private static final Logger log = LoggerFactory.getLogger(ConnectInstanceRegistrar.class);

  // Edge case: TTL expiry during GC pause or transient PG slowness can make a healthy instance
  // appear dead. 60s TTL with 10s refresh gives a 6x safety margin before another instance would
  // consider this one dead and reassign its sources.
  private static final Duration INSTANCE_TTL = Duration.ofSeconds(60);
  private static final Duration HEARTBEAT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);
  private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

  private final ConnectionManager connectionManager;
  private final ConnectWorkerHealthWriter workerHealthWriter;
  private final String instanceId;
  private final UUID workerRunId;
  private final Instant startedAt;
  private final String registrationId;
  private final AtomicLong heartbeatSequence = new AtomicLong();

  private final ScheduledExecutorService heartbeat =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "ConnectInstanceRegistrar-heartbeat");
            t.setDaemon(true);
            return t;
          });

  public ConnectInstanceRegistrar(
      ConnectionManager connectionManager,
      ConnectWorkerHealthWriter workerHealthWriter,
      String instanceId) {
    this.connectionManager = connectionManager;
    this.workerHealthWriter = workerHealthWriter;
    this.instanceId = instanceId;
    this.workerRunId = UUID.randomUUID();
    this.startedAt = Instant.now();
    this.registrationId = workerRunId.toString();
  }

  @PostConstruct
  public void register() {
    log.info("Registering Connect instance {} in Postgres", instanceId);
    writeRegistration();
    heartbeat.scheduleAtFixedRate(
        this::heartbeatTick,
        HEARTBEAT_INTERVAL_SECONDS,
        HEARTBEAT_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  @PreDestroy
  public void deregister() {
    log.info("Deregistering Connect instance {} from Postgres", instanceId);
    heartbeat.shutdown();
    awaitHeartbeatTermination();
    try {
      // A future K8s preStop drain can build on this marker, then wait for Core reassignment
      // before pod exit.
      workerHealthWriter.markStopped(workerRunId);
    } catch (Exception e) {
      log.warn(
          "Failed to mark Connect worker {} stopped in Postgres: {}", instanceId, e.getMessage());
    }
  }

  private void awaitHeartbeatTermination() {
    try {
      if (!heartbeat.awaitTermination(
          HEARTBEAT_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        heartbeat.shutdownNow();
        log.warn(
            "Timed out waiting for Connect instance {} heartbeat to stop before deregistration",
            instanceId);
      }
    } catch (InterruptedException e) {
      heartbeat.shutdownNow();
      Thread.currentThread().interrupt();
      log.warn(
          "Interrupted while waiting for Connect instance {} heartbeat to stop before deregistration",
          instanceId);
    }
  }

  void heartbeatTick() {
    writeRegistration();
    refreshRuntimeStatus();
  }

  void writeRegistration() {
    var info =
        new ConnectInstanceInfo(
            instanceId,
            startedAt,
            connectionManager.getConnectionCount(),
            registrationId,
            heartbeatSequence.incrementAndGet());
    writePostgresHeartbeat(info);
  }

  private void refreshRuntimeStatus() {
    try {
      connectionManager.refreshAllRuntimeStatus();
    } catch (Exception e) {
      log.warn(
          "Failed to refresh runtime status during heartbeat for instance {}: {}",
          instanceId,
          e.getMessage());
    }
  }

  private void writePostgresHeartbeat(ConnectInstanceInfo info) {
    try {
      workerHealthWriter.writeHeartbeat(workerRunId, info, INSTANCE_TTL);
    } catch (Exception e) {
      log.warn(
          "Failed to write Connect worker {} heartbeat to Postgres: {}",
          instanceId,
          e.getMessage());
    }
  }
}
