// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.map.tile;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "extensionType")
@JsonSubTypes({@JsonSubTypes.Type(value = WeatherTileExtension.class, name = "WEATHER")})
public sealed interface TileConfigExtension permits WeatherTileExtension {}
