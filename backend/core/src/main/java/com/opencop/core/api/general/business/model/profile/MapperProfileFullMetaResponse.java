// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.profile;

public record MapperProfileFullMetaResponse(
    MapperProfileResponse profile, String filename, byte[] payload) {}
