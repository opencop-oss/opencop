// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createExport, type CreateExportRequest } from "@opencop/api";
import { mutationOptions, type QueryClient } from "@tanstack/react-query";
import { auditQueries } from "../admin/audit/audit.queries";

export const exportMutations = {
    /**
     * An export writes an `EXPORTED` audit row per resource it drew from, so an open History
     * section has to be told. Invalidated on settle rather than success: Core can commit the row
     * before the response reaches this browser, so a transport failure leaves the outcome unknown
     * (see `docs/architecture/relational-data-patterns.md`). The cost is one admin read.
     *
     * The client is required, not optional: an optional one silently made this a no-op at every
     * call site.
     */
    create: (queryClient: QueryClient) =>
        mutationOptions({
            mutationKey: ["exports", "create"],
            mutationFn: (request: CreateExportRequest) => createExport(request),
            onSettled: () => queryClient.invalidateQueries({ queryKey: auditQueries.all }),
        }),
};
