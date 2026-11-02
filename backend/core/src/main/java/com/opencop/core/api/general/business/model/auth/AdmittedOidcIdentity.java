// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

import java.time.Instant;
import java.util.UUID;

public record AdmittedOidcIdentity(
    UUID internalUserId,
    String issuer,
    String subject,
    String idpUserId,
    OidcProfile profile,
    long sessionGeneration,
    Instant authenticatedAt) {}
