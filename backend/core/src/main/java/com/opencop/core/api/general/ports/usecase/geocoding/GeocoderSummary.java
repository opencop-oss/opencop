// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.ports.usecase.geocoding;

import com.opencop.core.sdk.business.model.integration.geocoding.GeocodingOperation;
import java.util.List;
import java.util.UUID;

/**
 * An available geocoder as clients discover it: the pair of ids that addresses it, its display
 * metadata and the operations it supports, in declaration order.
 */
public record GeocoderSummary(
    UUID integrationId,
    String integrationLabel,
    UUID geocoderId,
    String label,
    String description,
    List<GeocodingOperation> operations) {}
