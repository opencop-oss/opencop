// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.business.model.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.GeocodedPlace;
import java.util.List;

/**
 * A geocoder's answer reduced to what clients may receive.
 *
 * @param attribution the credits of the returned places, each once, in the order they first appear
 * @param places the usable places, without duplicates, in provider order and within the limit
 * @param defects why each dropped place was unusable, one entry per dropped place
 * @param defaults the fields filled with a default in the returned places, one entry per field
 */
public record ScreenedAnswer(
    List<String> attribution,
    List<GeocodedPlace> places,
    List<PlaceDefect> defects,
    List<DefaultedField> defaults) {}
