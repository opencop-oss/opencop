// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.connection.material.rtsp;

import org.geojson.GeoJsonObject;

/**
 * The stream an RTSP connection reads, and where its camera stands.
 *
 * @param coordinates the camera's position, or null when not supplied
 */
public record RtspEndpointMaterial(String url, GeoJsonObject coordinates) {}
