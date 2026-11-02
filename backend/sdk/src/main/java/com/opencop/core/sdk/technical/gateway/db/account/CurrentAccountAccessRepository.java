// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CurrentAccountAccessRepository
    extends Repository<CurrentAccountAccessEntity, UUID> {

  @Query(
      """
      select account
      from CurrentAccountAccessEntity account
      where account.internalUserId = :internalUserId
      """)
  Optional<CurrentAccountAccessEntity> findByInternalUserId(
      @Param("internalUserId") UUID internalUserId);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      """
      select account
      from CurrentAccountAccessEntity account
      where account.internalUserId = :internalUserId
      """)
  Optional<CurrentAccountAccessEntity> findByInternalUserIdForCachePopulation(
      @Param("internalUserId") UUID internalUserId);
}
