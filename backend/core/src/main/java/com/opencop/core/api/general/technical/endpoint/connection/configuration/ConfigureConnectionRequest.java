// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.connection.configuration;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * A change to what a running connection applies without reconnecting. Every field is optional and
 * an absent one leaves the connection as it is; the access fields change together. Unlike {@code
 * settings}, an access field sent as {@code null} is absent: none of them has a value to clear.
 *
 * @param settings kept as JSON so an absent field, which changes nothing, stays apart from a {@code
 *     null} one, which clears the overrides
 */
@Schema(
    name = "ConfigureConnectionRequest",
    description =
        "Changes the access mode together with the mappers it reads and writes with, and the"
            + " integration settings overrides. Omit all three access fields to leave access"
            + " unchanged; an access field sent as null counts as omitted. Send any of them with a"
            + " value and `access` is required, and a mapper left out is none, exactly as when"
            + " creating the connection.")
record ConfigureConnectionRequest(
    @Schema(
            description = "Access mode: R, W or RW",
            allowableValues = {"R", "W", "RW"})
        String access,
    @Schema(description = "Ingress mapper the connection reads with; required when it reads")
        UUID mapperId,
    @Schema(description = "Egress mapper the connection writes with; required when it writes")
        UUID egressMapperId,
    @Schema(
            description =
                "Integration settings overrides, keyed by section then field. Omit to keep the"
                    + " current overrides; an object replaces them all; null or {} clears them, so"
                    + " the deployment defaults apply. Validated as on creation.",
            // OpenAPI 3.1 states nullability as a type: `type` and `nullable` are ignored there.
            types = {"object", "null"})
        JsonNode settings) {}
