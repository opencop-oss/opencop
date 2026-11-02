// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.gateway.db.integrationcatalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationConnectionSchemaJpaRepository
    extends JpaRepository<IntegrationConnectionSchemaEntity, IntegrationConnectionSchemaId> {}
