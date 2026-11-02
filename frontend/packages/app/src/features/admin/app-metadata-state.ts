// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { RuntimeFeatureAvailability } from "@opencop/api";

export type VersionComparison = "match" | "mismatch" | "missing-metadata";

export interface BuildIdentityInput {
    revision?: string;
    version?: string;
}

export interface BuildIdentityDisplay {
    revision?: string;
    version?: string;
}

export function isRuntimeFeatureEnabled(
    features: readonly RuntimeFeatureAvailability[] | undefined,
    name: string,
): boolean {
    return features?.some((feature) => feature.name === name && feature.enabled) ?? false;
}

export type BuildFooterDisplayState =
    | {
          status: "match";
          serverRevision?: string;
          version: string;
          webRevision?: string;
      }
    | {
          server: BuildIdentityDisplay;
          status: "mismatch" | "missing-metadata";
          web: BuildIdentityDisplay;
      };

export function compareVersions(
    web: string | undefined,
    server: string | undefined,
): VersionComparison {
    const webVersion = normalizeMetadataValue(web);
    const serverVersion = normalizeMetadataValue(server);

    if (!webVersion || !serverVersion) {
        return "missing-metadata";
    }

    return webVersion === serverVersion ? "match" : "mismatch";
}

export function getBuildFooterDisplayState(
    web: BuildIdentityInput,
    server: BuildIdentityInput,
): BuildFooterDisplayState {
    const webDisplay = toBuildIdentityDisplay(web);
    const serverDisplay = toBuildIdentityDisplay(server);
    const versionComparison = compareVersions(web.version, server.version);

    if (versionComparison === "match") {
        return {
            status: "match",
            version: webDisplay.version!,
            webRevision: webDisplay.revision,
            serverRevision: serverDisplay.revision,
        };
    }

    return {
        status: versionComparison,
        web: webDisplay,
        server: serverDisplay,
    };
}

export function formatBuildIdentity(identity: BuildIdentityDisplay, unknownLabel: string): string {
    const parts = [identity.version, identity.revision].filter(Boolean);
    return parts.length > 0 ? parts.join(" ") : unknownLabel;
}

export function normalizeMetadataValue(value: string | undefined): string | undefined {
    if (!value || value.trim() === "" || value === "unknown") {
        return undefined;
    }

    return value;
}

function toBuildIdentityDisplay(identity: BuildIdentityInput): BuildIdentityDisplay {
    const display: BuildIdentityDisplay = {};
    const version = formatDisplayVersion(identity.version);
    const revision = normalizeMetadataValue(identity.revision)?.slice(0, 9);

    if (version) {
        display.version = version;
    }

    if (revision) {
        display.revision = revision;
    }

    return display;
}

function formatDisplayVersion(value: string | undefined): string | undefined {
    const version = normalizeMetadataValue(value);
    return version ? `v${version}` : undefined;
}
