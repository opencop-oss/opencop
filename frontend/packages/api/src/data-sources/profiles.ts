// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { DynamicMapper, MappingResponse, PreviewMappingRequest } from "./mapper.model";

// --- Response types (mirrors Java records) ---

/** GET /v1/profile/mapping → MapperProfile[] */
export interface MapperProfile {
    id: string;
    name: string;
    description: string;
    editable?: boolean;
    version?: number;
    dynamicMapper: DynamicMapper;
    archived?: boolean;
    createdAt?: string | null;
    createdBy?: string | null;
    updatedAt?: string | null;
    updatedBy?: string | null;
    lastEventAt?: string | null;
}

/** GET /v1/admin/profile/mapping/{id} response with sample payload */
export interface MapperProfileFullMeta {
    profile: MapperProfile;
    /** Original filename of the sample payload (null for legacy profiles pre-dating payload storage) */
    filename: string | null;
    /** Gzip-compressed, base64-encoded sample payload bytes (null for legacy profiles) */
    payload: string | null;
}

/**
 * Mapper definition accepted by create/update endpoints. Defined by the writable fields rather
 * than by omission from {@link MapperProfile}, so a server-owned field added there (capability,
 * audit columns) does not silently become writable here too.
 */
export type MapperProfileRequest = Pick<
    MapperProfile,
    "id" | "name" | "description" | "editable" | "version" | "dynamicMapper" | "archived"
>;

/** POST /v1/admin/profile/mapping request body */
export interface MapperProfileFullMetaRequest {
    profile: MapperProfileRequest;
    filename: string | null;
    payload: string | null;
}

/** PATCH /v1/admin/profile/mapping request body — filename/payload optional when sample file is unchanged */
export type MapperProfilePatch = Omit<MapperProfileFullMetaRequest, "filename" | "payload"> & {
    filename?: string;
    payload?: string;
};

// --- Fetch functions ---

const PROFILE_MAPPING = "/profile/mapping";
const ADMIN_PROFILE_MAPPING = "/admin/profile/mapping";

export async function getMapperProfiles(options?: FetchOptions): Promise<MapperProfile[]> {
    return apiFetch<MapperProfile[]>(PROFILE_MAPPING, { signal: options?.signal });
}

/** GET /v1/profile/mapping?qualifier=EGRESS — mappers a writing connection can encode with. */
export async function getEgressMapperProfiles(options?: FetchOptions): Promise<MapperProfile[]> {
    return apiFetch<MapperProfile[]>(`${PROFILE_MAPPING}?qualifier=EGRESS`, {
        signal: options?.signal,
    });
}

/**
 * GET /v1/admin/profile/mapping (admin — returns all mappers including archived). Core lists only
 * ingress mappers unless asked, so this asks for both directions.
 */
export async function getMapperProfilesAdmin(options?: FetchOptions): Promise<MapperProfile[]> {
    return apiFetch<MapperProfile[]>(
        `${ADMIN_PROFILE_MAPPING}?qualifier=INGRESS&qualifier=EGRESS`,
        {
            signal: options?.signal,
        },
    );
}

/** DELETE /v1/admin/profile/mapping/{id} */
export async function deleteMapperProfile(id: string): Promise<void> {
    return apiFetch(`${ADMIN_PROFILE_MAPPING}/${id}`, { method: "DELETE" });
}

/** GET /v1/admin/profile/mapping/{mappingId} (admin — returns full meta + sample payload) */
export async function getMapperProfileFullMeta(
    id: string,
    options?: FetchOptions,
): Promise<MapperProfileFullMeta> {
    return apiFetch<MapperProfileFullMeta>(`${ADMIN_PROFILE_MAPPING}/${id}`, {
        signal: options?.signal,
    });
}

/** GET /v1/admin/profile/mapping/{id}/versions (admin — returns all versions, newest first) */
export async function getMapperProfileVersions(
    id: string,
    options?: FetchOptions,
): Promise<MapperProfile[]> {
    return apiFetch<MapperProfile[]>(`${ADMIN_PROFILE_MAPPING}/${id}/versions`, {
        signal: options?.signal,
    });
}

/** PATCH /v1/admin/profile/mapping */
export async function updateMapperProfile(meta: MapperProfilePatch): Promise<void> {
    await apiFetch<void>(ADMIN_PROFILE_MAPPING, {
        method: "PATCH",
        body: JSON.stringify(meta),
    });
}

/** POST /v1/admin/profile/mapping → 201 Created */
export async function createMapperProfile(meta: MapperProfileFullMetaRequest): Promise<void> {
    await apiFetch<void>(ADMIN_PROFILE_MAPPING, {
        method: "POST",
        body: JSON.stringify(meta),
    });
}

/** POST /v1/profile/mapping/preview → MappingResponse */
export async function previewMapper(
    request: PreviewMappingRequest,
    options?: FetchOptions,
): Promise<MappingResponse> {
    return apiFetch<MappingResponse>(`${PROFILE_MAPPING}/preview`, {
        method: "POST",
        body: JSON.stringify(request),
        signal: options?.signal,
    });
}
