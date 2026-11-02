// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch, type FetchOptions } from "../client";

export type BannerKind = "HEADER" | "FOOTER" | "MODAL" | "SYSTEM";
export type BannerFormat = "PLAIN" | "MARKDOWN" | "HTML";

export interface BannerTranslation {
    id: string;
    bannerId: string;
    locale: string;
    content: string;
    createdAt: string;
    updatedAt: string;
}

export interface Banner {
    id: string;
    name: string;
    kind: BannerKind;
    enabled: boolean;
    startsAt: string;
    endsAt: string;
    color: string;
    textColor?: string;
    format: BannerFormat;
    dismissible: boolean;
    linkUrl: string;
    priority: number;
    createdAt: string;
    updatedAt: string;
    translations: BannerTranslation[];
}

export interface BannerCreate {
    name: string;
    kind: BannerKind;
    enabled: boolean;
    startsAt: string;
    endsAt: string;
    color: string;
    textColor?: string;
    format: BannerFormat;
    dismissible: boolean;
    linkUrl?: string;
    priority: number;
    translations: BannerTranslationCreate[];
}

export interface BannerPatch {
    name?: string;
    enabled?: boolean;
    startsAt?: string;
    endsAt?: string;
    color?: string;
    textColor?: string;
    format?: BannerFormat;
    dismissible?: boolean;
    linkUrl?: string;
    priority?: number;
}

export interface BannerTranslationCreate {
    locale: string;
    content: string;
}

export interface BannerTranslationPatch {
    content: string;
}

/** Sentinel ISO date used when a banner has no expiration. */
export const BANNER_NO_EXPIRY_DATE = "9999-12-31T23:59:59.999Z";

const BANNERS = "/banners";
const ADMIN_BANNERS = "/admin/banners";

export async function getBanners(options?: FetchOptions): Promise<Banner[]> {
    return apiFetch<Banner[]>(BANNERS, { signal: options?.signal });
}

export async function getBanner(id: string, options?: FetchOptions): Promise<Banner> {
    return apiFetch<Banner>(`${BANNERS}/${id}`, { signal: options?.signal });
}

export async function createBanner(data: BannerCreate): Promise<Banner> {
    return apiFetch<Banner>(ADMIN_BANNERS, {
        method: "POST",
        body: JSON.stringify(data),
    });
}

export async function patchBanner(id: string, data: BannerPatch): Promise<Banner> {
    return apiFetch<Banner>(`${ADMIN_BANNERS}/${id}`, {
        method: "PATCH",
        body: JSON.stringify(data),
    });
}

export async function deleteBanner(id: string): Promise<void> {
    return apiFetch<void>(`${ADMIN_BANNERS}/${id}`, {
        method: "DELETE",
    });
}

export async function getBannerTranslations(
    bannerId: string,
    options?: FetchOptions,
): Promise<BannerTranslation[]> {
    return apiFetch<BannerTranslation[]>(`${BANNERS}/${bannerId}/translations`, {
        signal: options?.signal,
    });
}

export async function createBannerTranslation(
    bannerId: string,
    data: BannerTranslationCreate,
): Promise<BannerTranslation> {
    return apiFetch<BannerTranslation>(`${ADMIN_BANNERS}/${bannerId}/translations`, {
        method: "POST",
        body: JSON.stringify(data),
    });
}

export async function patchBannerTranslation(
    bannerId: string,
    translationId: string,
    data: BannerTranslationPatch,
): Promise<BannerTranslation> {
    return apiFetch<BannerTranslation>(
        `${ADMIN_BANNERS}/${bannerId}/translations/${translationId}`,
        {
            method: "PATCH",
            body: JSON.stringify(data),
        },
    );
}

export async function deleteBannerTranslation(
    bannerId: string,
    translationId: string,
): Promise<void> {
    return apiFetch<void>(`${ADMIN_BANNERS}/${bannerId}/translations/${translationId}`, {
        method: "DELETE",
    });
}
