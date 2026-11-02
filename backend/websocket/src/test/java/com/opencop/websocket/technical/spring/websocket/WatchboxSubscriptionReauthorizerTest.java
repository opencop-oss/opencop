// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.technical.spring.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.captor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.opencop.websocket.ports.business.usecase.watchbox.WatchboxSubscribeAuthorizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import opencop.core.proto.websocket.SocketProtos.SocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

class WatchboxSubscriptionReauthorizerTest {

  private static final UUID WATCHBOX_ID = UUID.randomUUID();
  private static final String DESTINATION = "/topic/watchbox/" + WATCHBOX_ID;
  private static final Duration INTERVAL = Duration.ofSeconds(60);

  /** Settable test clock: the reauthorizer's freshness decisions are pure functions of this. */
  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now =
        new AtomicReference<>(Instant.parse("2026-08-18T12:00:00Z"));

    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException();
    }

    void advance(Duration duration) {
      now.updateAndGet(current -> current.plus(duration));
    }
  }

  private static final class TestTicker implements LongSupplier {
    private final AtomicLong now = new AtomicLong();
    private final AtomicReference<CountDownLatch> nextReadSignal = new AtomicReference<>();

    @Override
    public long getAsLong() {
      var signal = nextReadSignal.getAndSet(null);
      if (signal != null) {
        signal.countDown();
      }
      return now.get();
    }

    void set(long value) {
      now.set(value);
    }

    void signalNextRead(CountDownLatch signal) {
      nextReadSignal.set(signal);
    }
  }

  private final WsSessionRegistry registry = mock(WsSessionRegistry.class);
  private final WatchboxSubscribeAuthorizer authorizer = mock(WatchboxSubscribeAuthorizer.class);
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MutableClock clock = new MutableClock();
  private final TestTicker ticker = new TestTicker();
  private final WatchboxSubscriptionReauthorizer reauthorizer =
      new WatchboxSubscriptionReauthorizer(
          registry, authorizer, meterRegistry, clock, INTERVAL, ticker);

  private final UserSession authorized = mock(UserSession.class);
  private final UserSession revoked = mock(UserSession.class);
  private final UUID authorizedUser = UUID.randomUUID();
  private final UUID revokedUser = UUID.randomUUID();

  @BeforeEach
  void stubSessions() {
    when(authorized.getUserId()).thenReturn(authorizedUser);
    when(revoked.getUserId()).thenReturn(revokedUser);
  }

  @Test
  void firstDeliverySweepsTheAudienceAndRevokesOnlyFailingSessions() {
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized, revoked));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID)).thenReturn(true);
    when(authorizer.userCanSubscribe(revokedUser, WATCHBOX_ID)).thenReturn(false);
    when(registry.revoke(DESTINATION, revoked)).thenReturn(true);

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verify(registry).revoke(DESTINATION, revoked);
    verify(registry, never()).revoke(DESTINATION, authorized);
    assertThat(revocationCount()).isEqualTo(1.0);
  }

  @Test
  void withinTheIntervalTheCheckIsSkipped() {
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID)).thenReturn(true);
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    clock.advance(INTERVAL.minusSeconds(1));
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
  }

  @Test
  void pastTheIntervalTheAudienceIsSweptAgain() {
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID)).thenReturn(true);
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    clock.advance(INTERVAL);
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verify(authorizer, times(2)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
  }

  @Test
  void revocationNotifiesTheClientWithTheAdmissionNackShape() throws Exception {
    var ws = mock(WebSocketSession.class);
    when(ws.isOpen()).thenReturn(true);
    when(revoked.getWebsocketSession()).thenReturn(ws);
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(revoked));
    when(authorizer.userCanSubscribe(revokedUser, WATCHBOX_ID)).thenReturn(false);
    when(registry.revoke(DESTINATION, revoked)).thenReturn(true);

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    ArgumentCaptor<BinaryMessage> sent = captor();
    verify(ws).sendMessage(sent.capture());
    var nack = SocketMessage.parseFrom(sent.getValue().getPayload());
    assertThat(nack.getType()).isEqualTo(SocketMessage.Type.ERROR);
    assertThat(nack.getDestination()).isEmpty();
    assertThat(nack.getHeadersMap())
        .containsEntry("code", "SUBSCRIPTION_REVOKED")
        .containsEntry("operation", "SUBSCRIBE")
        .containsEntry("rejected-destination", DESTINATION);
  }

  @Test
  void authorizerErrorDeliversThenRetriesAfterTheBackoffNotPerEvent() {
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenThrow(new IllegalStateException("connection pool exhausted"))
        .thenReturn(true);

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verify(registry, never()).revoke(DESTINATION, authorized);
    assertThat(errorCount()).isEqualTo(1.0);
    assertThat(revocationCount()).isEqualTo(0.0);

    // The interval clock did not stamp, but the very next event must not pay another doomed
    // database wait either: attempts are paced by the backoff.
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);

    clock.advance(WatchboxSubscriptionReauthorizer.UNANSWERABLE_RETRY_BACKOFF);
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    verify(authorizer, times(2)).userCanSubscribe(authorizedUser, WATCHBOX_ID);

    // The successful attempt stamped the interval clock and cleared the backoff state.
    clock.advance(Duration.ofSeconds(1));
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    verify(authorizer, times(2)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
  }

  @Test
  void aDenialBeforeTheErrorStillAppliesAndTheSweepRetriesAfterTheBackoff() {
    // Ordered so the denial is evaluated before the error short-circuits the sweep.
    when(registry.getDestinationSessions(DESTINATION))
        .thenReturn(new LinkedHashSet<>(List.of(revoked, authorized)));
    when(authorizer.userCanSubscribe(revokedUser, WATCHBOX_ID)).thenReturn(false);
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenThrow(new IllegalStateException("connection pool exhausted"))
        .thenReturn(true);
    when(registry.revoke(DESTINATION, revoked)).thenReturn(true);

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    // The real denial was acted on even though a later check failed...
    verify(registry).revoke(DESTINATION, revoked);
    // ...and the incomplete sweep repeats, paced by the backoff rather than per event.
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
    clock.advance(WatchboxSubscriptionReauthorizer.UNANSWERABLE_RETRY_BACKOFF);
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    verify(authorizer, times(2)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
  }

  @Test
  void anUnanswerableCheckShortCircuitsTheRestOfTheSweep() {
    // Ordered so the error comes first: the sibling must not pay a doomed check of its own.
    when(registry.getDestinationSessions(DESTINATION))
        .thenReturn(new LinkedHashSet<>(List.of(authorized, revoked)));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenThrow(new IllegalStateException("connection pool exhausted"));

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verify(authorizer, never()).userCanSubscribe(revokedUser, WATCHBOX_ID);
    verify(registry, never()).revoke(DESTINATION, revoked);
    assertThat(errorCount()).isEqualTo(1.0);
  }

  @Test
  void destinationsAreIndependent() {
    var otherWatchbox = UUID.randomUUID();
    var otherDestination = "/topic/watchbox/" + otherWatchbox;
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(registry.getDestinationSessions(otherDestination)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID)).thenReturn(true);
    when(authorizer.userCanSubscribe(authorizedUser, otherWatchbox)).thenReturn(true);

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    reauthorizer.beforeDeliver(otherWatchbox, otherDestination);

    verify(authorizer).userCanSubscribe(authorizedUser, WATCHBOX_ID);
    verify(authorizer).userCanSubscribe(authorizedUser, otherWatchbox);
  }

  @Test
  void emptyDestinationStampsWithoutAuthorizerCalls() {
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of());

    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);
    clock.advance(Duration.ofSeconds(1));
    reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION);

    verifyNoInteractions(authorizer);
  }

  @Test
  void staleCallbacksShareOneLeaderAndBothDeliverOnlyAfterRevocation() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerEnteredWait = new CountDownLatch(1);
    var revocationCompleted = new AtomicReference<>(false);
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(revoked));
    when(authorizer.userCanSubscribe(revokedUser, WATCHBOX_ID))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return false;
            });
    when(registry.revoke(DESTINATION, revoked))
        .thenAnswer(
            ignored -> {
              revocationCompleted.set(true);
              return true;
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> leader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.signalNextRead(followerEnteredWait);
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> follower =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(followerEnteredWait.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(follower.isDone()).isFalse();
      release.countDown();

      assertThat(leader.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      assertThat(revocationCompleted.get()).isTrue();
      assertThat(follower.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      assertThat(revocationCompleted.get()).isTrue();
      verify(registry).revoke(DESTINATION, revoked);
      verify(authorizer, times(1)).userCanSubscribe(revokedUser, WATCHBOX_ID);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void overdueFollowersDropWithoutReplacingTheBlockedLeader() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return true;
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> leader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.set(WatchboxSubscriptionReauthorizer.IN_FLIGHT_FOLLOWER_GRACE.toNanos());

      assertThat(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DROP);
      clock.advance(WatchboxSubscriptionReauthorizer.UNANSWERABLE_RETRY_BACKOFF.plusSeconds(1));
      assertThat(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DROP);
      verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
      assertThat(errorCount()).isEqualTo(1.0);

      release.countDown();
      assertThat(leader.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      clock.advance(INTERVAL);
      assertThat(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      verify(authorizer, times(2)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void interruptedFollowerDropsAndRetainsItsInterruptStatus() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerFinished = new CountDownLatch(1);
    var followerEnteredWait = new CountDownLatch(1);
    var followerDecision = new AtomicReference<WatchboxSubscriptionReauthorizer.DeliveryDecision>();
    var followerInterrupted = new AtomicReference<Boolean>();
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return true;
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> leader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.signalNextRead(followerEnteredWait);
      Thread follower =
          new Thread(
              () -> {
                followerDecision.set(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
                followerInterrupted.set(Thread.currentThread().isInterrupted());
                followerFinished.countDown();
              });
      follower.start();
      assertThat(followerEnteredWait.await(5, TimeUnit.SECONDS)).isTrue();
      follower.interrupt();
      assertThat(followerFinished.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(followerDecision.get())
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DROP);
      assertThat(followerInterrupted.get()).isTrue();
      verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);

      release.countDown();
      leader.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void completedAuthorizerErrorReleasesWaitingFollowerAsDeliverAndPacesRetry() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerEnteredWait = new CountDownLatch(1);
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              throw new IllegalStateException("database unavailable");
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> leader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.signalNextRead(followerEnteredWait);
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> follower =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));

      assertThat(followerEnteredWait.await(5, TimeUnit.SECONDS)).isTrue();

      release.countDown();
      assertThat(leader.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      assertThat(follower.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      assertThat(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      verify(authorizer, times(1)).userCanSubscribe(authorizedUser, WATCHBOX_ID);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void unexpectedLeaderAbortReleasesFollowersAsDropAndAllowsRetry() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var followerEnteredWait = new CountDownLatch(1);
    when(registry.getDestinationSessions(DESTINATION))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              throw new IllegalStateException("registry unavailable");
            })
        .thenReturn(Set.of());

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> leader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ticker.signalNextRead(followerEnteredWait);
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> follower =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));

      assertThat(followerEnteredWait.await(5, TimeUnit.SECONDS)).isTrue();

      release.countDown();
      assertThat(follower.get(5, TimeUnit.SECONDS))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DROP);
      assertThatThrownBy(() -> leader.get(5, TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void anotherDestinationAuthorizesWhileTheFirstLeaderIsBlocked() throws Exception {
    var otherWatchbox = UUID.randomUUID();
    var otherDestination = "/topic/watchbox/" + otherWatchbox;
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(registry.getDestinationSessions(DESTINATION)).thenReturn(Set.of(authorized));
    when(registry.getDestinationSessions(otherDestination)).thenReturn(Set.of(authorized));
    when(authorizer.userCanSubscribe(authorizedUser, WATCHBOX_ID))
        .thenAnswer(
            ignored -> {
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              return true;
            });
    when(authorizer.userCanSubscribe(authorizedUser, otherWatchbox)).thenReturn(true);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<WatchboxSubscriptionReauthorizer.DeliveryDecision> blockedLeader =
          executor.submit(() -> reauthorizer.beforeDeliver(WATCHBOX_ID, DESTINATION));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(reauthorizer.beforeDeliver(otherWatchbox, otherDestination))
          .isEqualTo(WatchboxSubscriptionReauthorizer.DeliveryDecision.DELIVER);
      verify(authorizer).userCanSubscribe(authorizedUser, otherWatchbox);

      release.countDown();
      blockedLeader.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  private double revocationCount() {
    var counter =
        meterRegistry
            .find(WatchboxSubscriptionReauthorizer.REVOCATIONS_METRIC)
            .tag("reason", "REAUTH_DENIED")
            .counter();
    return counter == null ? 0.0 : counter.count();
  }

  private double errorCount() {
    var counter = meterRegistry.find(WatchboxSubscriptionReauthorizer.ERRORS_METRIC).counter();
    return counter == null ? 0.0 : counter.count();
  }
}
