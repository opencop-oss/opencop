// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.profile.mapper;

import com.opencop.core.sdk.business.model.profile.MapperProfileFullMeta;

/** HTTP request envelope for creating or updating a mapper profile. */
public record MapperProfileFullMetaRequest(
    MapperProfileRequest profile, String filename, byte[] payload) {

  MapperProfileFullMeta toBusinessModel() {
    return new MapperProfileFullMeta(profile.toBusinessModel(), filename, payload);
  }
}
