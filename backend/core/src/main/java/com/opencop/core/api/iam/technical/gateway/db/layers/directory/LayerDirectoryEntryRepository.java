// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.iam.technical.gateway.db.layers.directory;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LayerDirectoryEntryRepository
    extends JpaRepository<LayerDirectoryEntity, UUID>,
        JpaSpecificationExecutor<LayerDirectoryEntity> {}
