// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.runtimefeature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RuntimeFeatureHistoryJpaRepository
    extends JpaRepository<RuntimeFeatureHistoryEntity, Long>,
        JpaSpecificationExecutor<RuntimeFeatureHistoryEntity> {}
