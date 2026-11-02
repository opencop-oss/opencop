// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.scim.group;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternalGroupRepository extends JpaRepository<InternalGroupEntity, UUID> {

  Optional<InternalGroupEntity> findByIdpGroupId(String idpUserId);

  Page<InternalGroupEntity> findByName(String name, Pageable pageable);

  boolean existsByIdpGroupId(String idpUserId);

  Page<InternalGroupEntity> findByNameIn(Collection<String> names, Pageable pageable);

  Page<InternalGroupEntity> findByIdpGroupIdIn(Collection<String> ids, Pageable pageable);
}
