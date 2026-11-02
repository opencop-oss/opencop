// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";
import type { MediaRecord, ThumbnailRef } from "../events/events";
import type { MapCaptureResponse } from "../map-captures/map-captures";
import type { MapTile } from "../map/tiles";

const BASE = "/me/preferences";

export interface UserPreferences {
    username: string;
    locale: string;
    measurementSystem: "IMPERIAL" | "METRIC";
    temperatureUnit: "FAHRENHEIT" | "CELSIUS";
    timezone: string | null; // IANA timezone identifier (e.g., "America/New_York")
    zoomBehavior: "CENTER" | "MOUSE";
    avatarMediaId: string | null;
    avatar: {
        record: MediaRecord;
        url: string;
        thumbnails: ThumbnailRef[] | null;
    } | null;
    initialView: MapCaptureResponse | null; // Full map capture object (expanded by backend)
    defaultTile: MapTile | null; // Full tile object (expanded by backend)
}

export interface UserPreferencesPatch {
    username?: string;
    locale?: string;
    measurementSystem?: "IMPERIAL" | "METRIC";
    temperatureUnit?: "FAHRENHEIT" | "CELSIUS";
    timezone?: string | null; // IANA timezone identifier
    zoomBehavior?: "CENTER" | "MOUSE";
    initialView?: string | null; // Send UUID string, receive full object
    defaultTileId?: string | null; // Send UUID string, receive full object as defaultTile
}

export async function getPreferences(options?: FetchOptions): Promise<UserPreferences> {
    return apiFetch<UserPreferences>(BASE, { signal: options?.signal });
}

export async function patchPreferences(data: UserPreferencesPatch): Promise<UserPreferences> {
    return apiFetch<UserPreferences>(BASE, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

export async function uploadAvatar(
    file: File,
    thumbnails: File[] = [],
    options?: FetchOptions,
): Promise<UserPreferences> {
    const formData = new FormData();
    formData.append("original", file);

    for (const thumb of thumbnails) {
        formData.append("thumbnails", thumb);
    }

    return apiFetch<UserPreferences>("/me/avatar", {
        method: "POST",
        body: formData,
        signal: options?.signal,
    });
}

export async function deleteAvatar(options?: FetchOptions): Promise<UserPreferences> {
    return apiFetch<UserPreferences>("/me/avatar", {
        method: "DELETE",
        signal: options?.signal,
    });
}
