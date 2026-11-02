// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export const isProd = import.meta.env.MODE === "production";

export const AUTH_ENDPOINTS = {
    me: "/v1/me",
    logout: "/v1/logout",
    /** OAuth2 authorization endpoint - initiates OIDC login flow */
    login: "/api/oauth2/authorization/oidc",
};

export const WEBSOCKET_URL = import.meta.env.VITE_WEBSOCKET_URL;

if (!WEBSOCKET_URL) {
    throw new Error("VITE_WEBSOCKET_URL environment variable is required but not set.");
}
