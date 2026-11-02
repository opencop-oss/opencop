// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.websocket.business.usecase.watchbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.websocket.ports.gateway.watchbox.WatchboxAccessGateway;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchboxSubscribeAccessPolicyTest {

  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID WATCHBOX_ID = UUID.fromString("00000000-0000-0000-0000-000000000b01");
  private static final UUID LAYER_A = UUID.fromString("00000000-0000-0000-0000-00000000a001");
  private static final UUID LAYER_B = UUID.fromString("00000000-0000-0000-0000-00000000a002");

  @Test
  void unknownOrArchivedWatchboxDeniesEvenAdministrators() {
    var gateway = new FakeGateway(Optional.empty(), true, Set.of());

    assertThat(new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isFalse();
  }

  @Test
  void emptyLayerSetDeniesEvenAdministrators() {
    var gateway = new FakeGateway(Optional.of(Set.of()), true, Set.of());

    assertThat(new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isFalse();
  }

  @Test
  void activeAdministratorSkipsPerLayerChecks() {
    var gateway = new FakeGateway(Optional.of(Set.of(LAYER_A, LAYER_B)), true, Set.of());

    assertThat(new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isTrue();
    assertThat(gateway.layerChecks).isEmpty();
  }

  @Test
  void memberOfEveryLayerIsAllowed() {
    var gateway =
        new FakeGateway(Optional.of(Set.of(LAYER_A, LAYER_B)), false, Set.of(LAYER_A, LAYER_B));

    assertThat(new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isTrue();
  }

  @Test
  void memberOfOnlySomeLayersIsDenied() {
    var gateway = new FakeGateway(Optional.of(Set.of(LAYER_A, LAYER_B)), false, Set.of(LAYER_A));

    assertThat(new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isFalse();
  }

  @Test
  void gatewayFailureWhileLoadingWatchboxPropagates() {
    var gateway = mock(WatchboxAccessGateway.class);
    var failure = new IllegalStateException("database unavailable");
    when(gateway.activeWatchboxLayerIds(WATCHBOX_ID)).thenThrow(failure);

    assertThatThrownBy(
            () -> new WatchboxSubscribeAccessPolicy(gateway).userCanSubscribe(USER_ID, WATCHBOX_ID))
        .isSameAs(failure);
  }

  private static final class FakeGateway implements WatchboxAccessGateway {

    private final Optional<Set<UUID>> layerIds;
    private final boolean administrator;
    private final Set<UUID> reachableLayers;
    private final List<UUID> layerChecks = new ArrayList<>();

    private FakeGateway(
        Optional<Set<UUID>> layerIds, boolean administrator, Set<UUID> reachableLayers) {
      this.layerIds = layerIds;
      this.administrator = administrator;
      this.reachableLayers = reachableLayers;
    }

    @Override
    public Optional<Set<UUID>> activeWatchboxLayerIds(UUID watchboxId) {
      return layerIds;
    }

    @Override
    public boolean userIsActiveAdministrator(UUID userId) {
      return administrator;
    }

    @Override
    public boolean userHasLayerAccess(UUID userId, UUID layerId) {
      layerChecks.add(layerId);
      return reachableLayers.contains(layerId);
    }
  }
}
