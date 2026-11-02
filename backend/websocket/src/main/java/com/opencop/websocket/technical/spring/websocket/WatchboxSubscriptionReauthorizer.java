// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.handler.SessionLimitExceededException;

/**
 * Bounds the admission-time-only subscribe window (see {@code WatchboxSubscribeAccessPolicy}) with
 * periodic re-authorization on the delivery path: before an alert is broadcast to a watchbox
 * destination, if that destination's audience has not passed a full check within the configured
 * interval, every registered session is re-run through the subscribe authorizer first, and sessions
 * that no longer pass are revoked before the event delivers. Both drift vectors — the watchbox's
 * layer composition edited under its stable id, and a subscriber's layer role revoked mid-session —
 * are corrected within one interval of the next delivered alert. The trigger is deliberately time,
 * not an edit signal: a previous design keyed on {@code configEpoch} and silently missed layer
 * additions, because that epoch is the detection tracking-reset generation and pure additions never
 * bump it.
 *
 * <p>A quiet watchbox delivers nothing through its stale registrations, so no delivery means no
 * leak and no check. A session admitted concurrently with a sweep can miss that sweep and is caught
 * within one interval. One per-destination state object is retained for every watchbox that ever
 * alerted through this instance — bounded by the watchbox population, so it is deliberately never
 * pruned.
 *
 * <p>Threading: called from the Redis listener threads (one per message). Concurrent sweeps of one
 * destination share a single in-flight check. Followers wait only until that check's common
 * monotonic deadline, then drop their individual alert rather than broadcasting before the
 * authoritative result is known. An unanswered check preserves the completed-error last-good
 * behavior and its retry backoff (see {@link #beforeDeliver}).
 */
public class WatchboxSubscriptionReauthorizer {

  static final String REVOCATIONS_METRIC = "opencop.websocket.subscriptions.revoked";
  static final String ERRORS_METRIC = "opencop.websocket.subscriptions.reauth.errors";
  static final String REVOKED_CODE = "SUBSCRIPTION_REVOKED";

  /**
   * Retry pacing while a destination's check is unanswerable; see the catch in {@link
   * #beforeDeliver}. Resilience detail rather than operator policy, so a constant instead of
   * configuration (same reasoning as the retained-retry tracker's log summary interval).
   */
  static final Duration UNANSWERABLE_RETRY_BACKOFF = Duration.ofSeconds(5);

  static final Duration IN_FLIGHT_FOLLOWER_GRACE = Duration.ofSeconds(1);

  private static final Logger log = LoggerFactory.getLogger(WatchboxSubscriptionReauthorizer.class);

  private final WsSessionRegistry registry;
  private final WatchboxSubscribeAuthorizer authorizer;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Duration interval;
  private final LongSupplier ticker;
  private final ConcurrentHashMap<String, DestinationState> states = new ConcurrentHashMap<>();

  public WatchboxSubscriptionReauthorizer(
      WsSessionRegistry registry,
      WatchboxSubscribeAuthorizer authorizer,
      MeterRegistry meterRegistry,
      Clock clock,
      Duration interval,
      LongSupplier ticker) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.interval = Objects.requireNonNull(interval, "interval");
    this.ticker = Objects.requireNonNull(ticker, "ticker");
    if (interval.isZero() || interval.isNegative()) {
      throw new IllegalArgumentException("interval must be positive");
    }
  }

  /**
   * Re-authorizes {@code destination}'s sessions if its last full check is older than the interval;
   * see class doc. Runs synchronously on the delivery thread: the first post-drift event is the
   * leak, so it is the one that must wait for the check.
   */
  public DeliveryDecision beforeDeliver(UUID watchboxId, String destination) {
    var now = clock.instant();
    var state = states.computeIfAbsent(destination, ignored -> new DestinationState());
    InFlightAttempt attempt;
    boolean leader;
    synchronized (state) {
      if (state.inFlightAttempt != null) {
        attempt = state.inFlightAttempt;
        leader = false;
      } else if (state.lastFullCheck != null && state.lastFullCheck.plus(interval).isAfter(now)) {
        return DeliveryDecision.DELIVER;
      } else if (state.nextAttemptEligibleAt != null && state.nextAttemptEligibleAt.isAfter(now)) {
        // A completed attempt was unanswerable; preserve the accepted last-good policy while its
        // retry pacing prevents another database wait per event.
        return DeliveryDecision.DELIVER;
      } else {
        attempt = new InFlightAttempt(now, ticker.getAsLong());
        state.inFlightAttempt = attempt;
        leader = true;
      }
    }
    return leader
        ? leadAuthorizationAttempt(watchboxId, destination, state, attempt)
        : awaitAuthorizationAttempt(destination, attempt);
  }

  public enum DeliveryDecision {
    DELIVER,
    DROP
  }

  private DeliveryDecision leadAuthorizationAttempt(
      UUID watchboxId, String destination, DestinationState state, InFlightAttempt attempt) {
    var everyAnswerArrived = true;
    try {
      for (UserSession session : registry.getDestinationSessions(destination)) {
        boolean allowed;
        try {
          allowed = authorizer.userCanSubscribe(session.getUserId(), watchboxId);
        } catch (RuntimeException exception) {
          // Deliberately deliver on an unanswerable check, and leave the interval clock unstamped.
          // This audience passed a real authorization within the last interval; treating a
          // transient failure (e.g. a DB blip) as deny-all would mass-revoke authorized
          // subscribers and, with the interval then stamped, freeze them out silently.
          //
          // Shared fate bounds the common outage: alert production publishes inside the
          // processor's Postgres claim transaction, so when Postgres itself is down, new events
          // stop within seconds too. The uncovered case is a failure local to THIS app's Postgres
          // access (its pool exhausted, a partial partition): events keep flowing, and sessions
          // revoked since their last good check keep receiving them until the path heals — a
          // sustained non-zero errors counter is the operator tripwire, and the first delivery
          // after heal re-checks and revokes. Even so degraded, this stays strictly tighter than
          // every other destination family, which never re-checks after admission at all.
          //
          // The rest of the sweep is skipped: its checks would each stall on the same broken path,
          // and the completed-attempt backoff already schedules the whole-destination retry.
          everyAnswerArrived = false;
          reportProblem(
              attempt,
              "Watchbox re-authorization unanswerable on {}; delivering on the last good check,"
                  + " next attempt in {}: {}",
              destination,
              UNANSWERABLE_RETRY_BACKOFF,
              exception.toString());
          break;
        }
        if (!allowed) {
          revoke(session, destination);
        }
      }
      synchronized (state) {
        if (everyAnswerArrived) {
          state.lastFullCheck = attempt.startedAt;
          state.nextAttemptEligibleAt = null;
        } else {
          state.nextAttemptEligibleAt = attempt.startedAt.plus(UNANSWERABLE_RETRY_BACKOFF);
        }
      }
      completeAndClear(state, attempt, DeliveryDecision.DELIVER);
      return DeliveryDecision.DELIVER;
    } catch (RuntimeException | Error exception) {
      completeAndClear(state, attempt, DeliveryDecision.DROP);
      throw exception;
    }
  }

  private DeliveryDecision awaitAuthorizationAttempt(String destination, InFlightAttempt attempt) {
    var completedDecision = attempt.completion.getNow(null);
    if (completedDecision != null) {
      return completedDecision;
    }
    long remainingNanos = attempt.deadlineNanos - ticker.getAsLong();
    if (remainingNanos <= 0) {
      reportOverdue(destination, attempt);
      return DeliveryDecision.DROP;
    }
    try {
      return attempt.completion.get(remainingNanos, TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      reportOverdue(destination, attempt);
      return DeliveryDecision.DROP;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return DeliveryDecision.DROP;
    } catch (ExecutionException exception) {
      return DeliveryDecision.DROP;
    }
  }

  private void completeAndClear(
      DestinationState state, InFlightAttempt attempt, DeliveryDecision decision) {
    attempt.completion.complete(decision);
    synchronized (state) {
      if (state.inFlightAttempt == attempt) {
        state.inFlightAttempt = null;
      }
    }
  }

  private void reportOverdue(String destination, InFlightAttempt attempt) {
    reportProblem(
        attempt,
        "Watchbox re-authorization on {} exceeded the common {} follower grace; dropping alert",
        destination,
        IN_FLIGHT_FOLLOWER_GRACE);
  }

  private void reportProblem(InFlightAttempt attempt, String message, Object... arguments) {
    if (!attempt.problemReported.compareAndSet(false, true)) {
      return;
    }
    meterRegistry.counter(ERRORS_METRIC).increment();
    log.warn(message, arguments);
  }

  private static final class DestinationState {
    private Instant lastFullCheck;
    private Instant nextAttemptEligibleAt;
    private InFlightAttempt inFlightAttempt;
  }

  private static final class InFlightAttempt {
    private final Instant startedAt;
    private final long deadlineNanos;
    private final CompletableFuture<DeliveryDecision> completion = new CompletableFuture<>();
    private final AtomicBoolean problemReported = new AtomicBoolean();

    private InFlightAttempt(Instant startedAt, long startedNanos) {
      this.startedAt = startedAt;
      this.deadlineNanos = startedNanos + IN_FLIGHT_FOLLOWER_GRACE.toNanos();
    }
  }

  private void revoke(UserSession session, String destination) {
    if (!registry.revoke(destination, session)) {
      return;
    }
    meterRegistry.counter(REVOCATIONS_METRIC, "reason", "REAUTH_DENIED").increment();
    log.warn(
        "Revoked subscription of user {} to {}: no longer authorized",
        session.getUserId(),
        destination);
    notifyRevoked(session, destination);
  }

  // Same nack shape as admission rejections, so clients handle one error-frame contract. Send
  // failures are only logged: the revocation itself already happened, and a socket that cannot
  // receive frames is on its way out of the lifecycle machinery anyway.
  private void notifyRevoked(UserSession session, String destination) {
    var ws = session.getWebsocketSession();
    if (ws == null || !ws.isOpen()) {
      return;
    }
    try {
      ws.sendMessage(
          UserErrorFrames.errorFrame(REVOKED_CODE, destination, "Subscription access revoked"));
    } catch (SessionLimitExceededException | IOException exception) {
      log.warn(
          "Failed to notify session {} of revocation from {}: {}",
          ws.getId(),
          destination,
          exception.toString());
    }
  }
}
