// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.media;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaJpaRepository
    extends JpaRepository<MediaEntity, UUID>, JpaSpecificationExecutor<MediaEntity> {

  List<MediaEntity> findByThumbnailOf(UUID thumbnailOf);

  boolean existsByBlobKeyAndIdNotIn(String blobKey, Collection<UUID> ids);

  // SELECT ... FOR UPDATE — prevents thumbnail-of-thumbnail chains without a DB trigger.
  // The row lock ensures no concurrent transaction can modify the parent's thumbnail_of
  // between our check and the insert.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM MediaEntity m WHERE m.id = :id")
  Optional<MediaEntity> findByIdForUpdate(@Param("id") UUID id);
}
