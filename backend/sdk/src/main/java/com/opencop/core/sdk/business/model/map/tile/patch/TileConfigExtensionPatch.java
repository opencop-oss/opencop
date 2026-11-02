// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile.patch;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "extensionType")
@JsonSubTypes({@JsonSubTypes.Type(value = WeatherTileExtensionPatch.class, name = "WEATHER")})
public sealed interface TileConfigExtensionPatch permits WeatherTileExtensionPatch {}
