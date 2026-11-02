// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

import java.util.List;
import java.util.Optional;

/**
 * A provider-neutral place returned by a geocoder.
 *
 * <p>Fields are not validated on construction: the host validates every place a plugin returns and
 * drops the ones it cannot use.
 *
 * @param id the provider's stable reference for the place; never an identifier that changes when
 *     the provider re-imports its data
 * @param name the short display name
 * @param label the full display label, such as a name followed by its parent places
 * @param category the place's kind in the Overture Maps {@code basic_category} vocabulary, when the
 *     plugin maps one
 * @param attribution the credits the provider's terms or the place's data licence require clients
 *     to display wherever this place is shown, as plain text; empty when none are required, never
 *     null. A credit the provider requires for every result is repeated on each place: the host
 *     builds the answer-wide credits from the places it returns, so an answer without places
 *     carries none
 */
public record GeocodedPlace(
    String id,
    String name,
    String label,
    PlaceType placeType,
    Optional<String> category,
    GeoPoint position,
    Optional<BoundingBox> bbox,
    List<String> attribution) {}
