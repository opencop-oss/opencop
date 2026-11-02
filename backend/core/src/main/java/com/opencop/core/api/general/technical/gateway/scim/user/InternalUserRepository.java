// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.user;

import com.opencop.core.api.general.technical.gateway.db.user.AccountMutationProjection;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InternalUserRepository
    extends JpaRepository<InternalUserEntity, UUID>, JpaSpecificationExecutor<InternalUserEntity> {

  /** Locks a user before applying a SCIM replace or patch mutation. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from InternalUserEntity u where u.id = :internalUserId")
  Optional<InternalUserEntity> findByIdForScimMutation(
      @Param("internalUserId") UUID internalUserId);

  /** Locks the account row without auto-flushing pending entity changes. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
  @Query("select u from InternalUserEntity u where u.id = :internalUserId")
  Optional<InternalUserEntity> lockAccountMutationRowWithoutAutoFlush(
      @Param("internalUserId") UUID internalUserId);

  /** Reads the mutation state after the account row is locked, without auto-flushing changes. */
  @QueryHints(@QueryHint(name = HibernateHints.HINT_FLUSH_MODE, value = "COMMIT"))
  @Query(
      """
      select u.id as internalUserId,
             u.email as email,
             u.active as active,
             u.administrator as administrator,
             u.sessionGeneration as sessionGeneration,
             u.accountAccessRevision as accountRevision,
             u.deletedAt as deletedAt
      from InternalUserEntity u
      where u.id = :internalUserId
      """)
  Optional<AccountMutationProjection> findLockedAccountMutationStateWithoutAutoFlush(
      @Param("internalUserId") UUID internalUserId);

  Optional<InternalUserEntity> findByUsername(String username);

  Optional<InternalUserEntity> findByIdpIssuerAndIdpUserId(String issuer, String idpUserId);

  Optional<InternalUserEntity> findByIdpIssuerAndIdpUsername(String issuer, String username);

  Optional<InternalUserEntity> findByIdpIssuerAndEmail(String issuer, String email);

  Page<InternalUserEntity> findByIdpIssuerAndIdpUsername(
      String issuer, String idpUsername, Pageable pageable);

  Page<InternalUserEntity> findByIdpIssuerAndIdpUserId(
      String issuer, String idpUserId, Pageable pageable);

  Page<InternalUserEntity> findByIdpIssuerAndIdpUsernameIn(
      String issuer, Collection<String> usernames, Pageable pageable);

  Page<InternalUserEntity> findByIdpIssuerAndIdpUserIdIn(
      String issuer, Collection<String> externalIds, Pageable pageable);

  Page<InternalUserEntity> findByIdpIssuerAndIdIn(
      String issuer, Collection<UUID> ids, Pageable pageable);
}
