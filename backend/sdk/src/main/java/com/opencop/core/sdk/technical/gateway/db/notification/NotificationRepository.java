// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.notification;

import com.opencop.core.sdk.model.notification.content.NotificationStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
   update NotificationEntity n
   set n.readAt = CURRENT_TIMESTAMP
   where n.id = :id
     and n.targetUserId = :userId
     and n.readAt is null
  """)
  int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

  @Modifying
  @Query(
      """
         update NotificationEntity n
         set n.status = :status
         where n.id = :id
         """)
  int updateStatus(@Param("id") UUID id, @Param("status") NotificationStatus status);

  Page<NotificationEntity> findByTargetUserIdOrderByCreatedAtDesc(
      UUID targetUserId, Pageable pageable);

  Page<NotificationEntity> findByTargetUserIdAndReadAtNullOrderByCreatedAtDesc(
      UUID targetUserId, Pageable pageable);
}
