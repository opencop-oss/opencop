// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    INTEGRATION_UNAVAILABLE,
    type ConnectionIntegrationRef,
    type ConnectionStateReason,
} from "@opencop/api";
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { useTranslation } from "react-i18next";
import { integrationQueries } from "~/features/data-sources/integrations.queries";
import { integrationDisplayName } from "~/features/data-sources/integration-text.util";

export interface ConnectionIntegration {
    /** Catalog name in the user's language, or `undefined` while loading or when the version isn't listed. */
    name: string | undefined;
    schemaVersion: number;
    integrationId: string;
    /**
     * `unavailable` when the runtime reported it or the catalog marks it so; `available` only once
     * the catalog confirms it; `unknown` while loading or when the catalog no longer lists it.
     */
    availability: "available" | "unavailable" | "unknown";
}

/**
 * Resolves a connection's integration reference against the whole catalog, unavailable versions
 * included. The reference itself is shown as stored and never rewritten, whatever the catalog says.
 */
export function useConnectionIntegration(
    integration: ConnectionIntegrationRef | null | undefined,
    stateReason: ConnectionStateReason | null | undefined,
): ConnectionIntegration | undefined {
    const { t } = useTranslation();
    const { data: catalog } = useQuery({
        ...integrationQueries.catalogAll(),
        enabled: !!integration,
    });
    if (!integration) return undefined;
    const entry = catalog?.find(
        (candidate) =>
            candidate.integrationId === integration.integrationId &&
            candidate.schemaVersion === integration.schemaVersion,
    );
    return {
        name: entry && integrationDisplayName(entry, t),
        integrationId: integration.integrationId,
        schemaVersion: integration.schemaVersion,
        availability:
            stateReason === INTEGRATION_UNAVAILABLE || entry?.available === false
                ? "unavailable"
                : entry?.available
                  ? "available"
                  : "unknown",
    };
}

/** The integration's catalog name, or its stored identity while the catalog does not name it. */
export function connectionIntegrationLabel(
    integration: ConnectionIntegration,
    t: TFunction,
): string {
    return (
        integration.name ??
        t("dataSources.integrationUnknown", {
            id: integration.integrationId,
            version: integration.schemaVersion,
        })
    );
}
