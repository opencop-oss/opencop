// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";

/**
 * Static, declarative metadata a plugin exports. Everything except `apiVersion`
 * is displayable (admin view / plugin list); `apiVersion` is host-validated
 * against {@link SDK_API_VERSION} at load time.
 */
export interface PluginManifest {
    /**
     * Unique, stable owner id, `<namespace>.<name>`: lower-case words joined by
     * hyphens, with at least one dot (`acme.heatmap`). Namespaces the plugin's
     * contributions (`acme.heatmap:<contribution>`), so it cannot contain `:`. An id
     * held by one of the app's native plugins is refused at load.
     */
    readonly id: string;
    /** Human-readable display name. */
    readonly name: string;
    /** Plugin version (semver string). Displayed, not validated. */
    readonly version: string;
    /** SDK contract version the plugin was built against. Host-validated. */
    readonly apiVersion: number;
    readonly description?: string;
    readonly author?: string;
    /** Optional icon component shown in admin/list surfaces. */
    readonly icon?: ComponentType;
}
