// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.gateway.db.media;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAttachmentJpaRepository extends JpaRepository<MediaAttachmentEntity, UUID> {}
