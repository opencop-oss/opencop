// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_WEBSOCKET_URL: string
    readonly VITE_KEYCLOAK_URL: string
    readonly VITE_BUILD_SHA: string
    readonly VITE_APP_VERSION?: string
}
