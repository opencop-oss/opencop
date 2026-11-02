// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

/**
 * A WGS84 extent in decimal degrees, latitude first.
 *
 * <p>{@code minLon > maxLon} is a valid box that crosses the antimeridian.
 */
public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {}
