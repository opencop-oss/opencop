// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.userdirectory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserDirectoryEntryRepository
    extends JpaRepository<UserDirectoryEntryEntity, UUID>,
        JpaSpecificationExecutor<UserDirectoryEntryEntity> {}
