// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getIntegrationConnectionSetup, getIntegrations, type TransportType } from "@opencop/api";

export const integrationQueries = {
    all: ["integrations"] as const,

    catalog: (protocol: TransportType) =>
        queryOptions({
            queryKey: [...integrationQueries.all, "catalog", protocol] as const,
            queryFn: ({ signal }) => getIntegrations(protocol, { signal }),
        }),
    /** The whole catalog, unavailable versions included, for naming an existing connection's integration. */
    catalogAll: () =>
        queryOptions({
            queryKey: [...integrationQueries.all, "catalog", "all"] as const,
            queryFn: ({ signal }) => getIntegrations(undefined, { signal }),
        }),
    connectionSetup: (integrationId: string, schemaVersion: number) =>
        queryOptions({
            queryKey: [
                ...integrationQueries.all,
                "connection-setup",
                integrationId,
                schemaVersion,
            ] as const,
            queryFn: ({ signal }) =>
                getIntegrationConnectionSetup(integrationId, schemaVersion, { signal }),
            enabled: !!integrationId,
        }),
};
