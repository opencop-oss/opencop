// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.sdk.business.model.integration.geocoding;

import java.util.List;

/**
 * What a geocoder returned for one request.
 *
 * <p>Each place carries its own attribution; the host builds the answer-wide credits from the
 * places it returns.
 *
 * @param places the places in the provider's ranking order
 */
public record GeocodingAnswer(List<GeocodedPlace> places) {}
