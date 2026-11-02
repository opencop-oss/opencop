// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

package com.opencop.core.api.general.technical.endpoint.integrationcatalog;

import com.opencop.core.api.general.business.model.integrationcatalog.IntegrationCatalogEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** One connection schema version a client may choose between. */
@Schema(description = "A version of the connection contract an integration offers.")
public record IntegrationCatalogEntryResponse(
    @Schema(description = "Identifier of the integration, stable across its schema versions.")
        UUID integrationId,
    @Schema(
            description =
                "Connection schema version of the integration; with integrationId it identifies one"
                    + " version of the connection contract.")
        int schemaVersion,
    @Schema(description = "Display name of the integration offering this version.") String name,
    @Schema(description = "Display description of the integration offering this version.")
        String description,
    @Schema(description = "Translation key for `name`. Use `name` if there's no translation.")
        String nameKey,
    @Schema(
            description =
                "Translation key for `description`. Use `description` if there's no translation.")
        String descriptionKey,
    @Schema(
            description =
                """
                Whether a loaded plugin provides this version in the runtime answering this \
                request. False means the catalog records the version but this runtime does not \
                provide it, so it must not be offered for a new connection. It is still listed so \
                a client can name a version an existing connection already binds to.

                True means only that the version is provided. It does not promise that connection \
                setup can be described for it; ask the connection-setup resource for that.""")
        boolean available) {

  public static IntegrationCatalogEntryResponse from(IntegrationCatalogEntry entry) {
    return new IntegrationCatalogEntryResponse(
        entry.integrationId(),
        entry.schemaVersion(),
        entry.name(),
        entry.description(),
        IntegrationTextKeys.name(entry.integrationId()),
        IntegrationTextKeys.description(entry.integrationId()),
        entry.available());
  }
}
