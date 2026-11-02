// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.event.business.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.opencop.core.api.event.ports.business.usecase.OpencopEventException;
import com.opencop.core.sdk.ports.gateway.iam.SourceAccessRepository;
import com.opencop.core.sdk.technical.gateway.db.iam.ServerRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SourceBroadcastPolicyTest {

  private static final UUID USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440111");
  private static final UUID SOURCE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440112");

  private SourceAccessRepository sourceAccessRepository;
  private SourceBroadcastPolicy policy;

  @BeforeEach
  void setUp() {
    sourceAccessRepository = mock(SourceAccessRepository.class);
    policy = new SourceBroadcastPolicy(sourceAccessRepository);
  }

  @Test
  void assertSourceMutator_whenAdmin_passes() {
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.ADMIN);

    assertDoesNotThrow(() -> policy.assertSourceMutator(USER_ID, SOURCE_ID));
  }

  @Test
  void assertSourceMutator_whenOwner_passes() {
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.OWNER);

    assertDoesNotThrow(() -> policy.assertSourceMutator(USER_ID, SOURCE_ID));
  }

  @Test
  void assertSourceMutator_whenViewer_throwsUnauthorized() {
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(ServerRole.VIEWER);

    var ex =
        assertThrows(
            OpencopEventException.class, () -> policy.assertSourceMutator(USER_ID, SOURCE_ID));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
  }

  @Test
  void assertSourceMutator_whenNoRole_throwsUnauthorized() {
    when(sourceAccessRepository.userRole(USER_ID, SOURCE_ID)).thenReturn(null);

    var ex =
        assertThrows(
            OpencopEventException.class, () -> policy.assertSourceMutator(USER_ID, SOURCE_ID));
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getHttpStatus());
  }
}
