// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { AUTH_ENDPOINTS } from "~/config";

/**
 * Pages that shouldn't be returned to after login.
 * Note: Backend also redirects to /access_denied but FE page doesn't exist yet.
 */
const AUTH_PAGES = ["/logged-out", "/login_success", "/login_failed"];

/**
 * Redirects to OAuth login, optionally returning to a specific URL after auth.
 *
 * @param returnUrl - URL to return to after login. Defaults to current location.
 *                    Pass `null` to explicitly skip the return URL.
 */
export function redirectToLogin(returnUrl?: string | null): void {
    const url = returnUrl === undefined ? getCurrentUrl() : returnUrl;

    const params = new URLSearchParams();
    if (url) params.set("redirect_uri", url);

    const loginUrl = params.toString() ? `${AUTH_ENDPOINTS.login}?${params}` : AUTH_ENDPOINTS.login;

    window.location.assign(loginUrl);
}

/**
 * Gets the current URL path with query string.
 * Returns null for auth-related pages that shouldn't be returned to.
 *
 * Note: Hash/fragment is excluded because HTTP redirects cannot contain fragments.
 * The backend strips them defensively, but we don't send them in the first place.
 */
export function getCurrentUrl(): string | null {
    const path = window.location.pathname;
    if (AUTH_PAGES.includes(path)) {
        return null;
    }
    return path + window.location.search;
}
