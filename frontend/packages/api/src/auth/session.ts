// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { apiFetch } from "../client";

const CACHED_USER_KEY = "cachedUser";
const LAST_ACTIVITY_KEY = "lastActivity";

/** Cache TTL in milliseconds - matches React Query staleTime */
const CACHE_TTL_MS = 5 * 60 * 1000;

/** Activity threshold - if user was inactive longer than this, treat as new visitor on 401 */
const ACTIVITY_THRESHOLD_MS = 30 * 60 * 1000; // 30 minutes

function getStorage(): Storage | null {
    if (typeof window === "undefined") return null;
    return window.localStorage;
}

type CachedUserData = {
    user: AuthUser;
    cachedAt: number;
};

/**
 * User information returned by the /v1/me BFF endpoint.
 * Matches the backend MeResponse structure.
 *
 * Nullability analysis (from MeEndpoint.mapOIDCUser):
 *
 * | Field           | Nullable | Reason                                      |
 * |-----------------|----------|---------------------------------------------|
 * | authenticated   | No       | Always true when response is returned       |
 * | user            | No       | Always constructed                          |
 * | user.id         | No       | UUID from database                          |
 * | user.subject    | Yes      | getSubject() could return null              |
 * | user.username   | No       | Falls back to getName()                     |
 * | user.displayName| No       | Falls back through chain to getName()       |
 * | user.email      | Yes      | getEmail() could return null                |
 * | idp             | No       | Always constructed                          |
 * | idp.issuer      | Yes      | Explicit null check in backend              |
 * | authorities     | No       | toList() returns empty list, not null       |
 * | claims          | No       | subset() returns empty map, not null        |
 */
export type AuthUser = {
    authenticated: boolean;
    user: {
        id: string;
        subject: string | null;
        username: string;
        displayName: string;
        email: string | null;
    };
    idp: {
        issuer: string | null;
    };
    authorities: string[];
    claims: Record<string, unknown>;
};

/**
 * Fetches the current user's session from /api/v1/me.
 * Throws ApiError on failure (including 401 for unauthenticated).
 */
export async function fetchMe(): Promise<AuthUser> {
    return apiFetch<AuthUser>("/me");
}

/**
 * Ends all OpenCOP sessions for the authenticated user.
 *
 * The server confirms success with 204 No Content. The response body is intentionally ignored,
 * and the web auth feature owns the subsequent top-level navigation.
 */
export async function globalSignOut(): Promise<void> {
    return apiFetch("/bff/logout", {
        method: "POST",
        ignoreSuccessfulResponseBody: true,
    });
}

/** Query key for auth session - used by authLoader and AuthProvider */
export const AUTH_QUERY_KEY = ["auth", "session"] as const;

/** Cache user data in localStorage for instant load on new tabs */
export function cacheUser(user: AuthUser): void {
    const storage = getStorage();
    if (!storage) return;

    const data: CachedUserData = { user, cachedAt: Date.now() };
    storage.setItem(CACHED_USER_KEY, JSON.stringify(data));
}

/** Retrieve cached user data from localStorage if not expired */
export function getCachedUser(): AuthUser | undefined {
    const storage = getStorage();
    if (!storage) return undefined;

    const cached = storage.getItem(CACHED_USER_KEY);
    if (!cached) return undefined;
    try {
        const data = JSON.parse(cached) as CachedUserData;
        // Handle legacy cache format (no timestamp) - treat as expired
        if (!data.cachedAt) return undefined;
        // Check if cache has expired
        if (Date.now() - data.cachedAt > CACHE_TTL_MS) return undefined;
        return data.user;
    } catch {
        return undefined;
    }
}

/** Clear cached user data from localStorage */
export function clearCachedUser(): void {
    getStorage()?.removeItem(CACHED_USER_KEY);
}

/** Update last activity timestamp - called on successful API calls */
export function updateLastActivity(): void {
    getStorage()?.setItem(LAST_ACTIVITY_KEY, Date.now().toString());
}

/** Check if user was recently active (within threshold) */
export function wasRecentlyActive(): boolean {
    const lastActivity = getStorage()?.getItem(LAST_ACTIVITY_KEY);
    if (!lastActivity) return false;
    const elapsed = Date.now() - parseInt(lastActivity, 10);
    return elapsed < ACTIVITY_THRESHOLD_MS;
}

/** Clear last activity timestamp - called on logout */
export function clearLastActivity(): void {
    getStorage()?.removeItem(LAST_ACTIVITY_KEY);
}
