// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.model.media;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = MediaAttributes.Image.class, name = "IMAGE")})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface MediaAttributes {
  record Image(Integer widthPx, Integer heightPx) implements MediaAttributes {}
}
