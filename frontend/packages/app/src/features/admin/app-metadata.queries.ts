// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { queryOptions } from "@tanstack/react-query";
import { getAppMetadata } from "@opencop/api";

export const appMetadataQueries = {
    detail: () =>
        queryOptions({
            queryKey: ["app-metadata"] as const,
            queryFn: ({ signal }) => getAppMetadata({ signal }),
            staleTime: 60_000,
        }),
};
