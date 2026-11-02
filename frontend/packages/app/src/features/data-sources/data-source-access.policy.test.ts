// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AccessMode, ConnectionSetup, TransportType } from "@opencop/api";
import type { TFunction } from "i18next";
import { describe, expect, it } from "vitest";
import {
    getAccessModeOptions,
    getAccessModePresentation,
    getCapabilityCheckPresentation,
    getAccessIncompatibilityNotice,
    isAccessModeIncompatibleByTransport,
    resolveCapabilityCheck,
    type CapabilityCheckInput,
} from "./data-source-access.policy";

const translations: Record<string, string> = {
    "dataSources.accessRead": "Read",
    "dataSources.accessReadDescription":
        "Ingests data from this source only. Never pushes data to it.",
    "dataSources.accessWrite": "Write",
    "dataSources.accessWriteDescription":
        "Lets you manually push data to this source only. Never ingests data from it.",
    "dataSources.accessReadWrite": "Read & Write",
    "dataSources.accessReadWriteDescription":
        "Ingests data from this source, and lets you manually push data to it.",
};
const t = ((key: string) => translations[key] ?? key) as unknown as TFunction;

describe("getAccessModeOptions", () => {
    it("offers every access mode, in listing order", () => {
        expect(getAccessModeOptions(t)).toEqual([
            {
                value: "R",
                label: "Read",
                description: "Ingests data from this source only. Never pushes data to it.",
            },
            {
                value: "W",
                label: "Write",
                description:
                    "Lets you manually push data to this source only. Never ingests data from it.",
            },
            {
                value: "RW",
                label: "Read & Write",
                description:
                    "Ingests data from this source, and lets you manually push data to it.",
            },
        ]);
    });
});

describe("getAccessModePresentation", () => {
    it.each([
        ["R", "Read"],
        ["W", "Write"],
        ["RW", "Read & Write"],
    ] as const)("presents exact stored %s values as %s", (access, label) => {
        expect(getAccessModePresentation(access, t).label).toBe(label);
    });
});

describe("isAccessModeIncompatibleByTransport", () => {
    const transports: TransportType[] = ["TCP", "UDP", "KAFKA", "RTSP"];
    const accessModes: AccessMode[] = ["R", "W", "RW"];

    it.each(
        transports.flatMap((transportType) =>
            accessModes.map((access) => ({ transportType, access })),
        ),
    )("classifies $transportType $access from summary data", ({ transportType, access }) => {
        expect(isAccessModeIncompatibleByTransport(transportType, access)).toBe(
            transportType !== "TCP" && access !== "R",
        );
    });
});

describe("getAccessIncompatibilityNotice", () => {
    it("names a stored mode its transport doesn't allow", () => {
        expect(getAccessIncompatibilityNotice({ transportType: "UDP", access: "W" }, t)).toBe(
            "dataSources.accessTransportIncompatible",
        );
    });

    it.each<AccessMode>(["R", "W", "RW"])("has nothing to say about TCP %s", (access) => {
        expect(getAccessIncompatibilityNotice({ transportType: "TCP", access }, t)).toBeUndefined();
    });
});

describe("resolveCapabilityCheck", () => {
    const TCP_WRITER: ConnectionSetup = {
        kind: "TCP",
        integration: { id: "tak", schemaVersion: 1 },
        extractorId: "tcp-client",
        values: {},
        mapperId: "mapper-1",
        egressMapperId: "mapper-2",
        framing: null,
    };
    const base: CapabilityCheckInput = { transportType: "TCP", access: "RW", setup: TCP_WRITER };

    it.each<[string, Partial<CapabilityCheckInput>, ReturnType<typeof resolveCapabilityCheck>]>([
        ["needs no check off TCP", { transportType: "UDP" }, undefined],
        ["needs no check for Read", { access: "R" }, undefined],
        ["is proven by the stored egress mapper", {}, undefined],
        ["is proven for Write too", { access: "W" }, undefined],
        ["does not apply without a setup", { setup: null }, "notApplicable"],
        [
            "does not apply to a setup with no egress mapper on record",
            { setup: { ...TCP_WRITER, egressMapperId: null } },
            "notApplicable",
        ],
        ["has nothing to report while the setup loads", { setup: undefined }, undefined],
        [
            "is unverified when the setup never loaded",
            { setup: undefined, loadFailed: true },
            "unverified",
        ],
    ])("%s", (_, override, expected) => {
        expect(resolveCapabilityCheck({ ...base, ...override })).toBe(expected);
    });
});

describe("getCapabilityCheckPresentation", () => {
    it.each([
        ["unverified", "warning", true],
        ["notApplicable", "muted", false],
    ] as const)("presents %s as %s, retryable: %s", (check, tone, retryable) => {
        expect(getCapabilityCheckPresentation(check, t)).toMatchObject({ tone, retryable });
    });
});
