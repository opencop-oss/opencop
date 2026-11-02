// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import java.util.List;
import java.util.UUID;

/**
 * The places a geocoder found, with the credits of the returned places, each once, in the order
 * they first appear.
 */
public record GeocodingSearchResult(
    UUID integrationId, UUID geocoderId, List<String> attribution, List<GeocodedPlace> places) {}
