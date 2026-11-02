// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.technical.model.kml;

public record KmlStyle(
    String strokeColor, // "#rrggbbaa"
    Double strokeWidth,
    String fillColor, // "#rrggbbaa"
    String iconHref) {}
