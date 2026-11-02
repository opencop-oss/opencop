// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.processor.business.policy.watchbox;

import com.opencop.processor.business.model.watchbox.WatchboxScopeKey;
import com.opencop.processor.technical.observability.ProcessorMetrics;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory guard capping distinct-object cardinality per (watchbox, epoch, source) scope before
 * observations reach the transition engine.
 *
 * <p>Counting is deliberately per JVM so admission stays off the database hot path: with keyed
 * partitioning and N processor instances the aggregate pre-trip bound is at most N x cap per scope,
 * and a restart resets this instance's progress toward the cap. The trip itself is neither per-JVM
 * nor transient: the engine persists a DEGRADED status row in the same batch transaction, every
 * instance's runtime snapshot joins that row, and handlers screen the frozen scope from then on — a
 * tripped scope stays tripped across restarts and replicas within the current config epoch until
 * operators act. Starting a new config epoch deliberately starts a new churn scope. State-table
 * growth is separately bounded by retention GC regardless of breaker behavior.
 */
public final class WatchboxChurnBreaker implements WatchboxAdmissionGate {

  static final String MAX_RETAINED_ACTIVE_GAUGE =
      "opencop.watchbox.state.breaker.max_retained_active";
  static final String TRACKED_OBJECTS_TOTAL_GAUGE =
      "opencop.watchbox.state.breaker.tracked_objects_total";
  static final String TRACKED_SCOPES_GAUGE = "opencop.watchbox.state.breaker.tracked_scopes";

  private final ConcurrentHashMap<WatchboxScopeKey, ScopeState> scopes = new ConcurrentHashMap<>();
  private final boolean enabled;
  private final long configuredCap;
  private final Duration retention;
  private final AtomicLong maxRetainedActive = new AtomicLong();
  private final AtomicLong trackedObjectsTotal = new AtomicLong();
  private final AtomicLong trackedScopes = new AtomicLong();

  public WatchboxChurnBreaker(boolean enabled, long cap, Duration retention) {
    this(enabled, cap, retention, ProcessorMetrics.noop());
  }

  public WatchboxChurnBreaker(
      boolean enabled, long cap, Duration retention, ProcessorMetrics metrics) {
    if (cap < 1) {
      throw new IllegalArgumentException("cap must be at least one");
    }
    if (retention == null || retention.isZero() || retention.isNegative()) {
      throw new IllegalArgumentException("retention must be positive");
    }
    this.enabled = enabled;
    this.configuredCap = cap;
    this.retention = retention;
    metrics.registerGauge(MAX_RETAINED_ACTIVE_GAUGE, Tags.empty(), maxRetainedActive);
    metrics.registerGauge(TRACKED_OBJECTS_TOTAL_GAUGE, Tags.empty(), trackedObjectsTotal);
    metrics.registerGauge(TRACKED_SCOPES_GAUGE, Tags.empty(), trackedScopes);
  }

  @Override
  public AdmissionBatch begin() {
    return new BreakerAdmissionBatch();
  }

  @Override
  public Admission admit(
      AdmissionBatch batch, WatchboxScopeKey scope, String objectId, Instant observedAt) {
    if (!enabled) {
      return Admission.ADMITTED;
    }
    var admissionBatch = requireBatch(batch);
    admissionBatch.assertOpen();
    while (true) {
      var state = scopes.get(scope);
      if (state == null) {
        var candidate = new ScopeState();
        var existing = scopes.putIfAbsent(scope, candidate);
        state = existing == null ? candidate : existing;
        if (existing == null) {
          trackedScopes.incrementAndGet();
        }
      }
      synchronized (state) {
        if (state.removed) {
          // A concurrent release/prune emptied and removed this scope between our map
          // lookup and monitor entry; staging into the orphan would lose the membership.
          continue;
        }
        state.touch(observedAt);
        accountRemoved(state.pruneMembers(observedAt.minus(retention)));
        if (state.tripped || state.stagedTrip != null) {
          return Admission.FROZEN;
        }
        var member = state.members.get(objectId);
        if (member != null) {
          member.stage(admissionBatch, observedAt);
          admissionBatch.touch(scope);
          return Admission.ADMITTED;
        }
        if (state.members.size() < configuredCap) {
          var admitted = new MemberState();
          admitted.stage(admissionBatch, observedAt);
          state.members.put(objectId, admitted);
          state.memberCount = state.members.size();
          trackedObjectsTotal.incrementAndGet();
          maxRetainedActive.accumulateAndGet(state.memberCount, Math::max);
          admissionBatch.touch(scope);
          return Admission.ADMITTED;
        }
        // Pending memberships from concurrent batches count toward this safety cap. A committed
        // trip can therefore remain if a peer batch later rolls back; the threshold deliberately
        // favors bounded state over an exact count of transaction-confirmed members.
        state.stagedTrip = admissionBatch;
        admissionBatch.touch(scope);
        return Admission.TRIPPED;
      }
    }
  }

  @Override
  public void confirm(AdmissionBatch batch) {
    if (!enabled) {
      return;
    }
    var admissionBatch = requireBatch(batch);
    admissionBatch.close();
    for (var scope : admissionBatch.touchedScopes()) {
      var state = scopes.get(scope);
      if (state == null) {
        continue;
      }
      synchronized (state) {
        accountRemoved(state.confirm(admissionBatch));
      }
    }
  }

  @Override
  public void release(AdmissionBatch batch) {
    if (!enabled) {
      return;
    }
    var admissionBatch = requireBatch(batch);
    admissionBatch.close();
    for (var scope : admissionBatch.touchedScopes()) {
      var state = scopes.get(scope);
      if (state == null) {
        continue;
      }
      synchronized (state) {
        accountRemoved(state.release(admissionBatch));
        if (!state.tripped && state.stagedTrip == null && state.members.isEmpty()) {
          removeScope(scope, state);
        }
      }
    }
  }

  @Override
  public long cap() {
    return configuredCap;
  }

  public int pruneIdleScopes(Instant olderThan) {
    if (!enabled) {
      return 0;
    }
    var removed = 0;
    for (var entry : scopes.entrySet()) {
      var scope = entry.getKey();
      var state = entry.getValue();
      synchronized (state) {
        accountRemoved(state.pruneMembers(olderThan));
        // A scope untouched past the cutoff is removed even while tripped. Old-epoch scopes stop
        // being touched at an epoch bump (admission keys carry the new epoch), and a tripped
        // current-epoch scope goes quiet soon after its trip: WatchboxEventHandler drops frozen
        // targets from its runtime snapshot before observations are built, so admit() stops being
        // called once the snapshot joins the persisted DEGRADED row. That row remains the
        // authoritative screen — removing the in-memory scope is a scoped equivalent of the
        // restart this class already tolerates, and if traffic somehow reaches the exact scope
        // again this instance re-counts toward the cap while delivery stays screened.
        if ((state.isUntouchedSince(olderThan)
                || (!state.tripped && state.stagedTrip == null && state.members.isEmpty()))
            && removeScope(scope, state)) {
          removed++;
        }
      }
    }
    refreshMaxRetainedActive();
    return removed;
  }

  private void accountRemoved(int removed) {
    if (removed > 0) {
      trackedObjectsTotal.addAndGet(-removed);
      refreshMaxRetainedActive();
    }
  }

  private boolean removeScope(WatchboxScopeKey scope, ScopeState state) {
    if (scopes.remove(scope, state)) {
      // Both callers hold the scope monitor, so admit's removed re-check is race-free.
      state.removed = true;
      trackedScopes.decrementAndGet();
      refreshMaxRetainedActive();
      return true;
    }
    return false;
  }

  private void refreshMaxRetainedActive() {
    maxRetainedActive.set(
        scopes.values().stream().mapToLong(scope -> scope.memberCount).max().orElse(0L));
  }

  private static BreakerAdmissionBatch requireBatch(AdmissionBatch batch) {
    if (batch instanceof BreakerAdmissionBatch breakerBatch) {
      return breakerBatch;
    }
    throw new IllegalArgumentException("admission batch was not created by this breaker");
  }

  private static final class BreakerAdmissionBatch implements AdmissionBatch {
    private final Set<WatchboxScopeKey> touchedScopes = new HashSet<>();
    private final AtomicBoolean open = new AtomicBoolean(true);

    private void touch(WatchboxScopeKey scope) {
      touchedScopes.add(scope);
    }

    private Set<WatchboxScopeKey> touchedScopes() {
      return Set.copyOf(touchedScopes);
    }

    private void assertOpen() {
      if (!open.get()) {
        throw new IllegalStateException("admission batch is already closed");
      }
    }

    private void close() {
      if (!open.compareAndSet(true, false)) {
        throw new IllegalStateException("admission batch is already closed");
      }
    }
  }

  private static final class ScopeState {
    private final Map<String, MemberState> members = new HashMap<>();
    // Volatile mirror of members.size(): the gauge refresh reads it across all scopes without
    // taking each scope's monitor.
    private volatile int memberCount;
    private boolean tripped;
    private boolean removed;
    private BreakerAdmissionBatch stagedTrip;
    // Watermark of the scope's latest admit(), whatever the outcome — frozen and tripped returns
    // refresh it too. Never moves backwards, so out-of-order batch timestamps cannot re-age a
    // scope into prune eligibility.
    private Instant lastTouchedAt;

    private void touch(Instant observedAt) {
      if (lastTouchedAt == null || observedAt.isAfter(lastTouchedAt)) {
        lastTouchedAt = observedAt;
      }
    }

    private boolean isUntouchedSince(Instant cutoff) {
      return lastTouchedAt != null && lastTouchedAt.isBefore(cutoff);
    }

    private int pruneMembers(Instant olderThan) {
      var before = members.size();
      members.values().removeIf(member -> member.isOlderThan(olderThan));
      memberCount = members.size();
      return before - memberCount;
    }

    private int confirm(BreakerAdmissionBatch batch) {
      var before = members.size();
      members.values().forEach(member -> member.confirm(batch));
      members.values().removeIf(MemberState::isEmpty);
      memberCount = members.size();
      if (stagedTrip == batch) {
        tripped = true;
        stagedTrip = null;
      }
      return before - memberCount;
    }

    private int release(BreakerAdmissionBatch batch) {
      var before = members.size();
      members.values().forEach(member -> member.release(batch));
      members.values().removeIf(MemberState::isEmpty);
      memberCount = members.size();
      if (stagedTrip == batch) {
        stagedTrip = null;
      }
      return before - memberCount;
    }
  }

  private static final class MemberState {
    private final Map<BreakerAdmissionBatch, Instant> stagedRefreshes = new HashMap<>();
    private Instant confirmedAt;

    private void stage(BreakerAdmissionBatch batch, Instant observedAt) {
      stagedRefreshes.merge(
          batch,
          observedAt,
          (current, candidate) -> candidate.isAfter(current) ? candidate : current);
    }

    private void confirm(BreakerAdmissionBatch batch) {
      var stagedAt = stagedRefreshes.remove(batch);
      if (stagedAt != null && (confirmedAt == null || stagedAt.isAfter(confirmedAt))) {
        confirmedAt = stagedAt;
      }
    }

    private void release(BreakerAdmissionBatch batch) {
      stagedRefreshes.remove(batch);
    }

    private boolean isOlderThan(Instant cutoff) {
      var latest = confirmedAt;
      for (var stagedAt : stagedRefreshes.values()) {
        if (latest == null || stagedAt.isAfter(latest)) {
          latest = stagedAt;
        }
      }
      return latest != null && latest.isBefore(cutoff);
    }

    private boolean isEmpty() {
      return confirmedAt == null && stagedRefreshes.isEmpty();
    }
  }
}
