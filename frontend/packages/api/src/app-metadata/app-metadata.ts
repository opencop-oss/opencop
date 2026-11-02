// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";

export interface AppMetadata {
    appVersion?: string;
    features?: RuntimeFeatureAvailability[];
    admin?: {
        bff?: {
            revision?: string;
            branch?: string;
            buildDate?: string;
        };
        runtimeFeatures?: RuntimeFeature[];
    };
}

export interface RuntimeFeatureAvailability {
    name: string;
    enabled: boolean;
}

export type RuntimeFeatureAudience =
    | { mode: "NONE" }
    | { mode: "ALL" }
    | { mode: "USER_GROUPS"; userGroupIds: string[] };

export interface RuntimeFeature {
    name: string;
    consumerClass: "BACKEND_ENFORCED" | "FRONTEND_ONLY";
    audience: RuntimeFeatureAudience;
    revision: number;
    updatedAt: string;
}

export async function getAppMetadata(options?: FetchOptions): Promise<AppMetadata> {
    return apiFetch<AppMetadata>("/app-metadata", { signal: options?.signal });
}
