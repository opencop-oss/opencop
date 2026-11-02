// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.usecase.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencop.core.api.general.ports.gateway.CurrentAccountAccessCacheGateway;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccess;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDenialReason;
import com.opencop.core.sdk.business.model.account.CurrentAccountAccessDeniedException;
import com.opencop.core.sdk.business.policy.CurrentAccountAccessPolicy;
import com.opencop.core.sdk.ports.gateway.account.LockedCurrentAccountAccessGateway;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class CurrentAccountAccessTest {

  private static final UUID USER_ID = UUID.fromString("23914a75-a1a3-4f72-b55f-89a1ac856f47");
  private static final CurrentAccountAccess ACCESS =
      new CurrentAccountAccess(USER_ID, true, true, 3, 9);

  @Mock private LockedCurrentAccountAccessGateway postgresGateway;
  @Mock private CurrentAccountAccessCacheGateway cacheGateway;
  @Mock private PlatformTransactionManager transactionManager;

  private ResolveCurrentAccountAccessUsecase usecase;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(new SimpleTransactionStatus());
    usecase =
        new ResolveCurrentAccountAccessUsecase(
            postgresGateway, cacheGateway, new CurrentAccountAccessPolicy(), transactionManager);
  }

  @Test
  void cacheHitDoesNotQueryPostgresOrRewriteCache() {
    when(cacheGateway.findByInternalUserId(USER_ID)).thenReturn(Optional.of(ACCESS));

    assertEquals(ACCESS, usecase.resolve(USER_ID, 3));

    verify(postgresGateway, never()).findByInternalUserIdForCachePopulation(USER_ID);
    verify(cacheGateway, never()).populate(ACCESS);
    verify(transactionManager, never()).getTransaction(any());
  }

  @Test
  void cacheMissLoadsPostgresOnceAndPopulatesOnceInsideATransaction() {
    when(cacheGateway.findByInternalUserId(USER_ID)).thenReturn(Optional.empty());
    when(postgresGateway.findByInternalUserIdForCachePopulation(USER_ID))
        .thenReturn(Optional.of(ACCESS));

    assertEquals(ACCESS, usecase.resolve(USER_ID, 3));

    verify(transactionManager).getTransaction(any());
    verify(postgresGateway).findByInternalUserIdForCachePopulation(USER_ID);
    verify(cacheGateway).populate(ACCESS);
  }

  @Test
  void inactiveAccountCarriesBoundedDenialReason() {
    var inactiveAccess = new CurrentAccountAccess(USER_ID, false, true, 3, 9);
    when(cacheGateway.findByInternalUserId(USER_ID)).thenReturn(Optional.of(inactiveAccess));

    var exception =
        assertThrows(CurrentAccountAccessDeniedException.class, () -> usecase.resolve(USER_ID, 3));

    assertEquals(CurrentAccountAccessDenialReason.INACTIVE, exception.reason());
  }

  @Test
  void staleSessionCarriesBoundedDenialReason() {
    when(cacheGateway.findByInternalUserId(USER_ID)).thenReturn(Optional.of(ACCESS));

    var exception =
        assertThrows(CurrentAccountAccessDeniedException.class, () -> usecase.resolve(USER_ID, 2));

    assertEquals(CurrentAccountAccessDenialReason.STALE_GENERATION, exception.reason());
  }

  @Test
  void missingAccountCarriesBoundedDenialReason() {
    when(cacheGateway.findByInternalUserId(USER_ID)).thenReturn(Optional.empty());
    when(postgresGateway.findByInternalUserIdForCachePopulation(USER_ID))
        .thenReturn(Optional.empty());

    var exception =
        assertThrows(CurrentAccountAccessDeniedException.class, () -> usecase.resolve(USER_ID, 3));

    assertEquals(CurrentAccountAccessDenialReason.MISSING_ACCOUNT, exception.reason());
  }
}
