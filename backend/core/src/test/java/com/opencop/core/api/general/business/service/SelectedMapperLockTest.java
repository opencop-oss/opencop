// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.business.model.connection.access.ReadAccess;
import com.opencop.core.api.general.business.model.connection.access.ReadWriteAccess;
import com.opencop.core.api.general.business.model.connection.access.WriteAccess;
import com.opencop.core.api.general.ports.gateway.profile.MapperCatalogGateway;
import com.opencop.core.api.general.ports.usecase.connection.ConnectionManagementException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectedMapperLockTest {

  private static final UUID INGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
  private static final UUID EGRESS = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

  private final MapperCatalogGateway locks = mock(MapperCatalogGateway.class);
  private final SelectedMapperLock lock = new SelectedMapperLock(locks);

  @Test
  void locksEveryMapperTheAccessReadsAndWritesWith() {
    lock.hold(new ReadWriteAccess(INGRESS, EGRESS));
    lock.hold(new ReadAccess(INGRESS));
    lock.hold(new WriteAccess(EGRESS));

    verify(locks).lockAndFindArchived(List.of(INGRESS, EGRESS));
    verify(locks).lockAndFindArchived(List.of(INGRESS));
    verify(locks).lockAndFindArchived(List.of(EGRESS));
  }

  /** A mapper archived after its selection was validated still refuses the connection. */
  @Test
  void refusesTheConnectionWhenALockedMapperTurnsOutArchived() {
    when(locks.lockAndFindArchived(any())).thenReturn(Set.of(EGRESS));

    assertThatThrownBy(() -> lock.hold(new ReadWriteAccess(INGRESS, EGRESS)))
        .isInstanceOfSatisfying(
            ConnectionManagementException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("MAPPER_ARCHIVED"));
  }

  @Test
  void acceptsMappersThatAreStillUsable() {
    assertThatNoException().isThrownBy(() -> lock.hold(new ReadAccess(INGRESS)));
  }
}
