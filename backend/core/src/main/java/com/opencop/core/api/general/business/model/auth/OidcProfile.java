// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.auth;

public record OidcProfile(
    String preferredUsername,
    String fullName,
    String givenName,
    String familyName,
    String email,
    Boolean emailVerified) {}
