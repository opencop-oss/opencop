// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.connection;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConnectionIntegrationSettingsJpaRepository
    extends JpaRepository<ConnectionIntegrationSettingsEntity, UUID> {}
