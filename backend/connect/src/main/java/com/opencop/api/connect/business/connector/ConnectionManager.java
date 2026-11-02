// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.api.connect.business.connector;

import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder;
import com.opencop.api.connect.ports.gateway.observability.AccessModeTransitionRecorder.Cause;
import com.opencop.api.connect.ports.gateway.observability.integration.IntegrationAvailabilityRecorder;
import com.opencop.api.connect.ports.gateway.socket.connector.ConnectorProvider;
import com.opencop.api.connect.ports.gateway.socket.connector.SocketConnector;
import com.opencop.core.sdk.business.model.integration.IntegrationConnectionSchemaReference;
import com.opencop.core.sdk.model.connection.add.TransportKind;
import com.opencop.core.sdk.model.datasource.AccessMode;
import com.opencop.core.sdk.model.datasource.RuntimeStatus;
import com.opencop.core.sdk.ports.gateway.api.SourceRuntimeObservationWriter;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorState;
import com.opencop.core.sdk.ports.gateway.socket.ConnectorStateReason;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages socket connections in memory.
 *
 * <p>Core owns desired-state orchestration. This class manages the runtime lifecycle of socket
 * connections: start, stop, send, and status observations.
 */
public final class ConnectionManager {

  private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);

  /** One slot per connection - desired access, connector, listeners, and runtime tracking. */
  private static final class Entry {
    final UUID id;
    final TransportKind transport;
    final AtomicReference<AccessMode> access;
    final SocketConnector connector;
    final List<ConnectorListener> externalListeners;

    /**
     * Arrival time and ordinal of the newest accepted message, published as one value.
     *
     * <p>They are a single reference rather than two atomics because a reader needs both halves of
     * the same message. Read separately, a heartbeat could pair a new ordinal with the previous
     * message's time; the resulting token is accepted by the ledger carrying the wrong instant, and
     * because a later heartbeat repeats that same token the trigger's {@code IS DISTINCT FROM}
     * guard never revisits it. The receipt is then lost until the next message arrives, permanently
     * if none does.
     *
     * @param atMillis epoch milliseconds of arrival; 0 when no message has been accepted
     * @param count accepted messages so far, giving each one a distinct identity
     */
    record Receipt(long atMillis, long count) {
      static final Receipt NONE = new Receipt(0L, 0L);

      Receipt next(long atMillis) {
        return new Receipt(atMillis, count + 1);
      }
    }

    final AtomicReference<Receipt> receipt = new AtomicReference<>(Receipt.NONE);

    /**
     * Distinguishes this connector session from an earlier one for the same source.
     *
     * <p>The counter above restarts at zero with every new entry, and a restart replaces the entry
     * while leaving the manager's {@code instanceId} and {@code runId} untouched — so without a
     * per-session value the first message after a restart would re-mint a token the activity ledger
     * has already recorded, and be discarded as a replay. See {@link #messageToken}.
     */
    final String sessionId = UUID.randomUUID().toString();

    final Instant connectorStartedAt;
    ConnectorListener managerListener;

    Entry(
        UUID id,
        TransportKind transport,
        AccessMode access,
        SocketConnector connector,
        List<ConnectorListener> externalListeners) {
      this.id = id;
      this.transport = transport;
      this.access = new AtomicReference<>(access);
      this.connector = connector;
      this.externalListeners = externalListeners;
      this.connectorStartedAt = Instant.now();
    }

    static Instant lastMessageAt(Receipt receipt) {
      return receipt.atMillis() == 0 ? null : Instant.ofEpochMilli(receipt.atMillis());
    }
  }

  private record IncompatibleRuntime(
      RuntimeStatus status, TransportKind transport, AccessMode access) {}

  private record UnavailableRuntime(
      RuntimeStatus status, IntegrationConnectionSchemaReference integration) {}

  // Refreshed by state changes and heartbeat ticks; stale dead-owner status should not linger.
  private static final Duration DEFAULT_RUNTIME_TTL = Duration.ofSeconds(90);

  private final Map<UUID, Entry> connectorsMap = new ConcurrentHashMap<>();
  private final Map<UUID, IncompatibleRuntime> incompatibleRuntimes = new ConcurrentHashMap<>();

  /**
   * FAILED observations of connections whose integration this runtime cannot run. Behavior is
   * registered once at startup, so these only clear when the desired connection changes, stops, or
   * moves to an instance that can run it.
   */
  private final Map<UUID, UnavailableRuntime> unavailableRuntimes = new ConcurrentHashMap<>();

  private final ConnectorProvider connectorProvider;
  private final String instanceId;

  /**
   * Distinguishes this JVM's run from earlier runs carrying the same {@code instanceId}. See {@link
   * #messageToken}.
   *
   * <p>The same shape {@code ConnectInstanceRegistrar} uses for worker health — stable instance id,
   * per-run id, monotonic sequence — for the same reason: an instance id is reusable across
   * restarts, so a counter alone cannot tell this run's fifth message from the previous run's.
   */
  private final String runId = UUID.randomUUID().toString();

  private final Duration runtimeTtl;

  /** Whether the last observation write failed, so only the onset of a run is reported at WARN. */
  private final AtomicBoolean runtimeObservationFailing = new AtomicBoolean(false);

  private final SourceRuntimeObservationWriter sourceRuntimeObservationWriter;
  private final AccessModeTransitionRecorder accessModeTransitionRecorder;
  private final IntegrationAvailabilityRecorder integrationAvailabilityRecorder;

  // Serializes control-plane lifecycle mutations only. The hot ingest path reads from
  // SocketConnector threads and calls listeners directly; it does not take this manager lock.
  // STOPPED observations stay inside this lock so they cannot race after a replacement
  // connector's newer CONNECTING/CONNECTED observation for the same source and instance.
  private final Object lock = new Object();

  public enum StartOutcome {
    STARTED,
    RESTARTED,
    ALREADY_ACTIVE,
    INCOMPATIBLE,
    /** Not started: this runtime registers no implementation for the connection's integration. */
    INTEGRATION_UNAVAILABLE
  }

  public ConnectionManager(
      ConnectorProvider connectorProvider,
      String instanceId,
      Duration runtimeTtl,
      SourceRuntimeObservationWriter sourceRuntimeObservationWriter,
      AccessModeTransitionRecorder accessModeTransitionRecorder,
      IntegrationAvailabilityRecorder integrationAvailabilityRecorder) {
    this.connectorProvider = connectorProvider;
    this.instanceId = instanceId;
    this.sourceRuntimeObservationWriter = sourceRuntimeObservationWriter;
    this.accessModeTransitionRecorder =
        Objects.requireNonNull(accessModeTransitionRecorder, "accessModeTransitionRecorder");
    this.integrationAvailabilityRecorder =
        Objects.requireNonNull(integrationAvailabilityRecorder, "integrationAvailabilityRecorder");
    this.runtimeTtl =
        runtimeTtl == null || runtimeTtl.isZero() || runtimeTtl.isNegative()
            ? DEFAULT_RUNTIME_TTL
            : runtimeTtl;
  }

  public String getInstanceId() {
    return instanceId;
  }

  /**
   * Atomically applies desired access to an existing TCP runtime without replacing its connector.
   */
  public boolean applyDesiredAccess(UUID sourceId, AccessMode desiredAccess) {
    synchronized (lock) {
      var entry = connectorsMap.get(sourceId);
      if (entry == null || entry.transport != TransportKind.TCP) {
        return false;
      }
      return applyDesiredAccess(entry, desiredAccess, Cause.DESIRED_STATE_RECONCILIATION);
    }
  }

  /**
   * Applies a source's desired integration settings to its loaded connector without replacing it:
   * the behavior plan is swapped when the effective settings differ from those it runs.
   *
   * @return whether the plan was swapped
   */
  public boolean applyDesiredSettings(ConnectorCreationContext creationContext) {
    Entry entry;
    synchronized (lock) {
      entry = connectorsMap.get(creationContext.sourceId());
    }
    // The swap announces over the network, so it runs outside the manager lock. A connector
    // stopped meanwhile refuses the swap.
    return entry != null && connectorProvider.applySettings(entry.connector, creationContext);
  }

  public void send(UUID sourceId, byte[] data) throws Exception {
    final var entry = connectorsMap.get(sourceId);
    if (entry == null)
      throw new IllegalArgumentException("Connection " + sourceId + " not loaded.");
    if (entry.transport == TransportKind.TCP && !entry.access.get().canWrite()) {
      throw new IllegalStateException(
          "Connection " + sourceId + " access mode does not allow outbound writes.");
    }
    entry.connector.send(data);
  }

  /**
   * Ensures the desired connection is locally loaded.
   *
   * <p>Missing and terminal local runtimes are started under the manager lock. NEW, CONNECTING, and
   * CONNECTED runtimes are left alone so duplicate START nudges stay idempotent.
   */
  public StartOutcome ensureStarted(
      ConnectorCreationContext creationContext,
      TransportKind transport,
      AccessMode access,
      ConnectorListener... extraListeners) {
    synchronized (lock) {
      var sourceId = creationContext.sourceId();
      incompatibleRuntimes.remove(sourceId);
      Entry existing = connectorsMap.get(sourceId);
      if (existing != null) {
        var state = existing.connector.state();
        if (!shouldRecover(state)) {
          if (existing.transport == TransportKind.TCP) {
            applyDesiredAccess(existing, access, Cause.DESIRED_STATE_RECONCILIATION);
          }
          log.info("Connection {} already loaded in {} state, leaving it active", sourceId, state);
          return StartOutcome.ALREADY_ACTIVE;
        }
        log.info("Connection {} in {} state, removing and restarting", sourceId, state);
        connectorsMap.remove(sourceId);
        stopManagedEntry(existing);
      }

      if (!connectorProvider.canRun(creationContext)) {
        markIntegrationUnavailable(creationContext, transport);
        return StartOutcome.INTEGRATION_UNAVAILABLE;
      }
      unavailableRuntimes.remove(sourceId);
      startNew(creationContext, transport, access, extraListeners);
      return existing == null ? StartOutcome.STARTED : StartOutcome.RESTARTED;
    }
  }

  /**
   * Reports the connection FAILED for an unavailable integration instead of running it without the
   * behavior it requires. Kept as a marker so heartbeats keep the observation fresh.
   *
   * <p>The onset is logged and counted once per selection: re-evaluating the same unavailable
   * schema version stays quiet, while a different one, or the first evaluation after a stop or a
   * successful start, is a new onset.
   */
  private void markIntegrationUnavailable(
      ConnectorCreationContext creationContext, TransportKind transport) {
    var sourceId = creationContext.sourceId();
    var integration = creationContext.integration();
    var status =
        RuntimeStatus.failed(sourceId, instanceId, ConnectorStateReason.INTEGRATION_UNAVAILABLE);
    var previous = unavailableRuntimes.put(sourceId, new UnavailableRuntime(status, integration));
    writeRuntimeObservation(status);
    if (isNewOnset(previous, integration)) {
      log.warn(
          "Connection {} not started: integration {} schema version {} has no implementation"
              + " registered on {}",
          sourceId,
          integration.integrationId(),
          integration.schemaVersion(),
          instanceId);
      try {
        integrationAvailabilityRecorder.recordUnavailable(transport);
      } catch (RuntimeException e) {
        log.warn("Failed to record unavailable integration metric for source {}", sourceId, e);
      }
    }
  }

  private static boolean isNewOnset(
      UnavailableRuntime previous, IntegrationConnectionSchemaReference integration) {
    return previous == null || !previous.integration().equals(integration);
  }

  /** Stops any active connector and retains an observable, non-active incompatibility marker. */
  public boolean ensureIncompatible(
      UUID sourceId, TransportKind transport, AccessMode desiredAccess) {
    synchronized (lock) {
      unavailableRuntimes.remove(sourceId);
      var active = connectorsMap.remove(sourceId);
      var previous = incompatibleRuntimes.get(sourceId);
      var previousAccess =
          active != null ? active.access.get() : previous == null ? null : previous.access();
      // Read once, so the time and token reported here describe the same message - and carry the
      // token across, because this entry is about to be dropped. A message accepted since the last
      // heartbeat exists only in memory; rebuilding the status without its token would strand that
      // receipt, and no later heartbeat can report it once the entry is gone.
      var activeReceipt = active == null ? null : active.receipt.get();
      var lastMessageAt =
          activeReceipt != null
              ? Entry.lastMessageAt(activeReceipt)
              : previous == null ? null : previous.status().lastMessageAt();
      var lastMessageToken =
          activeReceipt != null
              ? messageToken(active, activeReceipt)
              : previous == null ? null : previous.status().lastMessageToken();
      var connectorStartedAt =
          active != null
              ? active.connectorStartedAt
              : previous == null ? null : previous.status().connectorStartedAt();

      if (active != null) {
        detachManagerListener(active);
        safeStop(active.connector);
      }

      var status =
          new RuntimeStatus(
              sourceId,
              ConnectorState.INCOMPATIBLE,
              lastMessageAt,
              instanceId,
              connectorStartedAt,
              lastMessageToken);
      incompatibleRuntimes.put(sourceId, new IncompatibleRuntime(status, transport, desiredAccess));
      writeRuntimeObservation(status);

      var changed =
          active != null
              || previous == null
              || previous.transport() != transport
              || previous.access() != desiredAccess;
      if (changed) {
        recordIncompatibility(sourceId, transport, previousAccess, desiredAccess);
      }
      if (active != null) {
        notifyExternal(
            active, listener -> listener.onStateChange(sourceId, ConnectorState.INCOMPATIBLE));
      }
      return active != null;
    }
  }

  /** Stop and remove a loaded connection. No-op if missing. */
  public boolean ensureStopped(UUID id) {
    synchronized (lock) {
      Entry entry = connectorsMap.remove(id);
      var incompatible = incompatibleRuntimes.remove(id);
      var unavailable = unavailableRuntimes.remove(id);
      if (entry == null && incompatible == null && unavailable == null) {
        return false;
      }
      if (entry != null) {
        stopManagedEntry(entry);
      } else if (incompatible != null) {
        // Carries the token for the same reason ensureIncompatible does: this rebuilds a status
        // from a detached one, and dropping the token here would strand a receipt that the
        // incompatible status was still holding.
        var status = incompatible.status();
        writeRuntimeObservation(
            new RuntimeStatus(
                status.id(),
                ConnectorState.STOPPED,
                status.lastMessageAt(),
                instanceId,
                status.connectorStartedAt(),
                status.lastMessageToken()));
      } else {
        // Only an unavailable-integration marker remained; nothing ran, so nothing else to stop.
        writeRuntimeObservation(new RuntimeStatus(id, ConnectorState.STOPPED, null, instanceId));
      }
      return true;
    }
  }

  /** Stop and remove a loaded connection. No-op if missing. */
  public void remove(UUID id) {
    ensureStopped(id);
  }

  private static void detachManagerListener(Entry entry) {
    if (entry.managerListener == null) {
      return;
    }
    entry.connector.removeListener(entry.managerListener);
  }

  /** Check if a connection is currently loaded in memory. */
  public boolean isLoaded(UUID id) {
    return connectorsMap.containsKey(id);
  }

  private static void safeStop(SocketConnector socketConnector) {
    if (socketConnector == null) {
      return;
    }
    try {
      socketConnector.stop();
    } catch (Throwable _) {
    }
  }

  private void stopManagedEntry(Entry entry) {
    detachManagerListener(entry);
    writeStateObservation(entry, ConnectorState.STOPPED);
    safeStop(entry.connector);
    notifyExternal(entry, listener -> listener.onStateChange(entry.id, ConnectorState.STOPPED));
  }

  private void notifyExternal(Entry entry, Consumer<ConnectorListener> notification) {
    for (var listener : entry.externalListeners) {
      try {
        notification.accept(listener);
      } catch (RuntimeException e) {
        log.error("Connector listener {} failed", listener.getClass().getName(), e);
      }
    }
  }

  private Entry startNew(
      ConnectorCreationContext creationContext,
      TransportKind transport,
      AccessMode access,
      ConnectorListener... extraListeners) {
    var sourceId = creationContext.sourceId();
    var connector = connectorProvider.create(creationContext);
    var externalListeners =
        extraListeners == null
            ? List.<ConnectorListener>of()
            : List.copyOf(Arrays.asList(extraListeners));
    var entry = new Entry(sourceId, transport, access, connector, externalListeners);
    entry.managerListener = new ManagerListener(entry);

    connector.addListener(entry.managerListener);

    connectorsMap.put(sourceId, entry);
    try {
      connector.start();
      if (transport == TransportKind.TCP) {
        recordAccessTransition(entry, null, access, Cause.RUNTIME_START);
      }
    } catch (RuntimeException | Error t) {
      connectorsMap.remove(sourceId, entry);
      detachManagerListener(entry);
      safeStop(connector);
      throw t;
    }
    return entry;
  }

  private boolean applyDesiredAccess(Entry entry, AccessMode desiredAccess, Cause cause) {
    var newAccess = Objects.requireNonNull(desiredAccess, "desiredAccess");
    var previousAccess = entry.access.getAndSet(newAccess);
    if (previousAccess == newAccess) {
      return false;
    }
    recordAccessTransition(entry, previousAccess, newAccess, cause);
    return true;
  }

  private void recordAccessTransition(
      Entry entry, AccessMode previousAccess, AccessMode newAccess, Cause cause) {
    var previousLabel = previousAccess == null ? "NONE" : previousAccess.getAccess();
    log.atInfo()
        .addKeyValue("source", entry.id)
        .addKeyValue("transport", entry.transport)
        .addKeyValue("oldMode", previousLabel)
        .addKeyValue("newMode", newAccess.getAccess())
        .addKeyValue("cause", cause.label())
        .log("Runtime access mode transition");
    try {
      accessModeTransitionRecorder.record(entry.transport, previousAccess, newAccess, cause);
    } catch (RuntimeException e) {
      log.warn("Failed to record access mode transition metric for source {}", entry.id, e);
    }
  }

  private void recordIncompatibility(
      UUID sourceId, TransportKind transport, AccessMode previousAccess, AccessMode desiredAccess) {
    var previousLabel = previousAccess == null ? "NONE" : previousAccess.getAccess();
    log.atWarn()
        .addKeyValue("source", sourceId)
        .addKeyValue("transport", transport)
        .addKeyValue("oldMode", previousLabel)
        .addKeyValue("newMode", desiredAccess.getAccess())
        .addKeyValue("cause", "incompatible_transport_access")
        .log("Runtime access mode is incompatible with socket transport");
    try {
      accessModeTransitionRecorder.recordIncompatibility(transport, desiredAccess);
    } catch (RuntimeException e) {
      log.warn("Failed to record access mode incompatibility metric for source {}", sourceId, e);
    }
  }

  private boolean shouldRecover(ConnectorState state) {
    return state == ConnectorState.STOPPED
        || state == ConnectorState.FAILED
        || state == ConnectorState.DISCONNECTED;
  }

  /** Tracks runtime state and lastMessageAt. */
  private class ManagerListener implements ConnectorListener {
    private final Entry entry;

    ManagerListener(Entry entry) {
      this.entry = entry;
    }

    @Override
    public void onMessage(UUID sourceId, byte[] data) {
      if (entry.transport == TransportKind.TCP && !entry.access.get().canRead()) {
        return;
      }
      // One short-lived record is the entire per-message cost of activity tracking; the token
      // string is composed only when a status is reported. Note this sits after the write-only
      // TCP early return above, so outbound-only sources record no activity - pre-existing
      // behavior, deliberately unchanged.
      long arrivedAt = System.currentTimeMillis();
      entry.receipt.updateAndGet(previous -> previous.next(arrivedAt));
      notifyExternal(entry, listener -> listener.onMessage(sourceId, data));
    }

    @Override
    public void onStateChange(UUID sourceId, ConnectorState state) {
      log.info("Connection {} state changed to {}", sourceId, state);
      writeStateObservation(entry, state);
      notifyExternal(entry, listener -> listener.onStateChange(sourceId, state));
    }

    @Override
    public void onError(UUID sourceId, Throwable error) {
      notifyExternal(entry, listener -> listener.onError(sourceId, error));
    }

    @Override
    public void onRestartRequested(UUID sourceId, Throwable reason) {
      notifyExternal(entry, listener -> listener.onRestartRequested(sourceId, reason));
    }
  }

  /**
   * Writes state + instanceId to the runtime observation store immediately. Best-effort, non-fatal.
   */
  private void writeStateObservation(Entry entry, ConnectorState state) {
    writeRuntimeObservation(runtimeStatusFor(entry, state));
  }

  /**
   * The reported status of an entry, including the identity of its newest inbound message.
   *
   * <p>The receipt is read once and both halves are derived from that one value, so the reported
   * time and token always describe the same message. Reading them separately would let a message
   * landing mid-call pair a new token with the previous arrival time.
   */
  private RuntimeStatus runtimeStatusFor(Entry entry, ConnectorState state) {
    var receipt = entry.receipt.get();
    return new RuntimeStatus(
        entry.id,
        state,
        Entry.lastMessageAt(receipt),
        instanceId,
        entry.connectorStartedAt,
        messageToken(entry, receipt));
  }

  /**
   * Identity of the message a receipt describes, or null when none has arrived.
   *
   * <p>Composed on demand rather than stored, so the per-message path allocates only the receipt
   * itself — the string is built at most once per heartbeat.
   *
   * <p>{@code runId} and the entry's {@code sessionId} are what make this safe across restarts, at
   * two different scopes. {@code instanceId} is deliberately stable for a redeployed container or
   * an explicit {@code OPENCOP_INSTANCE_ID} (see {@code InstanceIdentity}), and the receipt's count
   * restarts at zero for every new entry — so either a process restart or a single source's restart
   * would otherwise re-mint tokens the activity ledger has already recorded. Those are discarded as
   * replays, freezing a source's reported activity until its counter passed the pre-restart value.
   * {@code runId} covers the process; {@code sessionId} covers a source restarted in place, which
   * is the far more frequent event of the two.
   */
  private String messageToken(Entry entry, Entry.Receipt receipt) {
    return receipt.count() == 0
        ? null
        : instanceId + ":" + runId + ":" + entry.sessionId + ":" + receipt.count();
  }

  /**
   * Get runtime status for a single connection.
   *
   * @return RuntimeStatus if loaded, empty if not in memory
   */
  public Optional<RuntimeStatus> getRuntime(UUID id) {
    synchronized (lock) {
      Entry entry = connectorsMap.get(id);
      if (entry == null) {
        var incompatible = incompatibleRuntimes.get(id);
        if (incompatible != null) {
          return Optional.of(incompatible.status());
        }
        return Optional.ofNullable(unavailableRuntimes.get(id)).map(UnavailableRuntime::status);
      }
      return Optional.of(runtimeStatusFor(entry, entry.connector.state()));
    }
  }

  /** Get runtime status for all loaded connections. */
  public List<RuntimeStatus> getAllRuntime() {
    synchronized (lock) {
      var statuses = new ArrayList<RuntimeStatus>();
      for (var entry : connectorsMap.values()) {
        statuses.add(runtimeStatusFor(entry, entry.connector.state()));
      }
      incompatibleRuntimes.values().stream()
          .map(IncompatibleRuntime::status)
          .forEach(statuses::add);
      unavailableRuntimes.values().stream().map(UnavailableRuntime::status).forEach(statuses::add);
      return List.copyOf(statuses);
    }
  }

  /** Returns the number of active connections currently loaded. */
  public int getConnectionCount() {
    return connectorsMap.size();
  }

  /**
   * Re-writes RuntimeStatus for every active connection.
   *
   * <p>Called on the instance heartbeat interval (15s) to keep idle connection observations fresh.
   * Without this, connections with no message activity would expire after the configured runtime
   * TTL.
   */
  public void refreshAllRuntimeStatus() {
    for (Entry entry : connectorsMap.values()) {
      writeRuntimeObservation(runtimeStatusFor(entry, entry.connector.state()));
    }
    incompatibleRuntimes.values().stream()
        .map(IncompatibleRuntime::status)
        .forEach(this::writeRuntimeObservation);
    unavailableRuntimes.forEach(this::refreshUnavailableRuntime);
  }

  /**
   * Re-writes one unavailable marker only while it is still current, under the lock that removes
   * it, so a heartbeat that captured it cannot report FAILED after a stop or start has replaced it.
   * Locked per marker, not per batch, so slow writes do not hold off lifecycle changes for long.
   */
  private void refreshUnavailableRuntime(UUID sourceId, UnavailableRuntime marker) {
    synchronized (lock) {
      if (unavailableRuntimes.get(sourceId) == marker) {
        writeRuntimeObservation(marker.status());
      }
    }
  }

  /**
   * Writes an observation, best-effort: a failure here must never interrupt ingestion.
   *
   * <p>Reports the onset of a failure run at WARN with the throwable, and every write after it at
   * DEBUG, so an outage costs one production line rather than one per source per heartbeat. The
   * flag is instance-wide rather than per source on purpose: the failures this catches — database
   * availability, permissions, a constraint inside the activity trigger this row now fires — are
   * instance-wide, so keying it by source would multiply one signal by the source count.
   */
  private void writeRuntimeObservation(RuntimeStatus status) {
    if (sourceRuntimeObservationWriter == null) {
      return;
    }
    try {
      sourceRuntimeObservationWriter.writeRuntime(status, runtimeTtl);
      if (runtimeObservationFailing.compareAndSet(true, false)) {
        log.info("Runtime observation writes recovered, most recently for {}", status.id());
      }
    } catch (Exception e) {
      if (runtimeObservationFailing.compareAndSet(false, true)) {
        log.warn(
            "Runtime observation write failed for {}. Connection status and source activity will"
                + " report stale until this recovers; ingestion is unaffected.",
            status.id(),
            e);
      } else {
        log.debug(
            "Runtime observation write still failing for {}: {}", status.id(), e.getMessage());
      }
    }
  }
}
