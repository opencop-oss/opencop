// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Frontend mirror of Core's access-mode rules. Only TCP may write (`W` / `RW`), and Core stores a
 * writing connection only with the egress mapper chosen at creation, so a source's setup proves
 * its write support. A metadata PATCH can neither make a connection writable nor change a writable
 * one's access (`ACCESS_CHANGE_UNSUPPORTED`). If those Core rules change, this module must change
 * with them. Core remains authoritative and fails closed on its own; this module exists so the UI
 * never offers a choice the backend would reject.
 */
import type { AccessMode, ConnectionSetup, TransportType } from "@opencop/api";
import { assertNever } from "assert-never";
import type { TFunction } from "i18next";
import { TRANSPORT_LABEL_KEYS } from "./data-sources.util";

/** Every access mode, in the order an access choice lists them. */
export const ALL_ACCESS_MODES = ["R", "W", "RW"] as const satisfies readonly AccessMode[];

const ACCESS_MODE_PRESENTATION = {
    R: {
        labelKey: "dataSources.accessRead",
        descriptionKey: "dataSources.accessReadDescription",
    },
    W: {
        labelKey: "dataSources.accessWrite",
        descriptionKey: "dataSources.accessWriteDescription",
    },
    RW: {
        labelKey: "dataSources.accessReadWrite",
        descriptionKey: "dataSources.accessReadWriteDescription",
    },
} as const satisfies Record<
    AccessMode,
    { labelKey: `dataSources.${string}`; descriptionKey: `dataSources.${string}` }
>;

export interface AccessModeOption {
    value: AccessMode;
    label: string;
    description: string;
}

export function getAccessModePresentation(access: AccessMode, t: TFunction): AccessModeOption {
    const presentation = ACCESS_MODE_PRESENTATION[access];
    return {
        value: access,
        label: t(presentation.labelKey),
        description: t(presentation.descriptionKey),
    };
}

export interface AccessModeSupportInput {
    transportType: TransportType;
    access: AccessMode;
}

export function isAccessModeIncompatibleByTransport(
    transportType: TransportType,
    access: AccessMode,
): boolean {
    return transportType !== "TCP" && access !== "R";
}

/** Message for a stored access mode its transport doesn't allow. Undefined when compatible. */
export function getAccessIncompatibilityNotice(
    { transportType, access }: AccessModeSupportInput,
    t: TFunction,
): string | undefined {
    if (!isAccessModeIncompatibleByTransport(transportType, access)) return undefined;
    return t("dataSources.accessTransportIncompatible", {
        access: getAccessModePresentation(access, t).label,
        transport: t(TRANSPORT_LABEL_KEYS[transportType]),
    });
}

/**
 * Every access mode as a choice. Whether a transport allows one is
 * {@link isAccessModeIncompatibleByTransport}'s to say.
 */
export function getAccessModeOptions(t: TFunction): AccessModeOption[] {
    return ALL_ACCESS_MODES.map((value) => getAccessModePresentation(value, t));
}

/**
 * Why a TCP source's write support can't be shown:
 * - `"unverified"`: the source's setup never loaded. Retrying can help.
 * - `"notApplicable"`: the source has no egress mapper on record (created before v2), so there is
 *   nothing to check.
 */
export type CapabilityCheck = "unverified" | "notApplicable";

export interface CapabilityCheckInput {
    transportType: TransportType;
    access: AccessMode;
    /** The source's setup: `null` when it has none, `undefined` while it is still loading. */
    setup: ConnectionSetup | null | undefined;
    /** The setup's request never loaded. A failed background refresh is not one. */
    loadFailed?: boolean;
}

/**
 * Whether a source's write support can be shown, and if not, why. Only TCP sources that write need
 * the check; their stored egress mapper proves it, so there is nothing to report once it is found.
 */
export function resolveCapabilityCheck({
    transportType,
    access,
    setup,
    loadFailed,
}: CapabilityCheckInput): CapabilityCheck | undefined {
    if (transportType !== "TCP" || access === "R") return undefined;
    if (setup === undefined) return loadFailed ? "unverified" : undefined;
    return setup?.kind === "TCP" && setup.egressMapperId != null ? undefined : "notApplicable";
}

export interface CapabilityCheckPresentation {
    message: string;
    /** `"warning"` for something to resolve; `"muted"` for a state with nothing to fix. */
    tone: "warning" | "muted";
    /** Whether retrying the requests can change the outcome. */
    retryable: boolean;
}

export function getCapabilityCheckPresentation(
    check: CapabilityCheck,
    t: TFunction,
): CapabilityCheckPresentation {
    switch (check) {
        case "unverified":
            return {
                message: t("dataSources.accessCapabilityUnverified"),
                tone: "warning",
                retryable: true,
            };
        case "notApplicable":
            return {
                message: t("dataSources.accessCapabilityNotApplicable"),
                tone: "muted",
                retryable: false,
            };
        default:
            return assertNever(check);
    }
}
