// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A forward-geocoding request as the caller sent it, before validation.
 *
 * <p>Every request parameter is the raw text the caller sent, or {@code null} when it was omitted,
 * so the use case alone decides what is valid.
 *
 * @param internalUserId the requesting user
 * @param text the raw search text; may be missing or blank
 * @param limit the raw maximum number of places
 * @param lat the raw latitude of the focus point
 * @param lon the raw longitude of the focus point
 * @param lang the raw language tag the caller asked for
 * @param preferredLanguages the languages the caller's client prefers, most preferred first; empty
 *     when it states none
 */
public record SearchGeocoderCommand(
    UUID internalUserId,
    UUID integrationId,
    UUID geocoderId,
    String text,
    String limit,
    String lat,
    String lon,
    String lang,
    List<Locale> preferredLanguages) {}
