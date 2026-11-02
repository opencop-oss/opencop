// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeEach, describe, expect, it } from "vitest";
import { useCustomAttributesStore } from "./custom-attributes.store";

const PERSIST_KEY = "opencop:watchbox-filter";

describe("custom attributes store", () => {
    beforeEach(() => {
        localStorage.clear();
    });

    // The key and its shape predate this store; a new name, version or shape would
    // lose every saved attribute.
    it("saves attributes under the watchbox-era key, in its original shape, at version 1", () => {
        useCustomAttributesStore.getState().actions.addAttribute("callsign");

        expect(JSON.parse(localStorage.getItem(PERSIST_KEY) ?? "null")).toEqual({
            state: {
                customFields: {
                    callsign: {
                        field: "callsign",
                        label: "callsign",
                        path: "properties.opencop.attributes.callsign",
                        custom: true,
                    },
                },
            },
            version: 1,
        });

        useCustomAttributesStore.getState().actions.removeAttribute("callsign");
    });

    it("reads back attributes saved in that shape", async () => {
        localStorage.setItem(
            PERSIST_KEY,
            JSON.stringify({
                state: {
                    customFields: {
                        hull: {
                            field: "hull",
                            label: "hull",
                            path: "properties.opencop.attributes.hull",
                            custom: true,
                        },
                    },
                },
                version: 1,
            }),
        );

        await useCustomAttributesStore.persist.rehydrate();

        expect(useCustomAttributesStore.getState().names).toEqual(["hull"]);
        useCustomAttributesStore.getState().actions.removeAttribute("hull");
    });

    it("drops a stored name the backend would refuse", async () => {
        const saved = (name: string) => ({
            field: name,
            label: name,
            path: `properties.opencop.attributes.${name}`,
            custom: true,
        });
        localStorage.setItem(
            PERSIST_KEY,
            JSON.stringify({
                state: { customFields: { hull: saved("hull"), "a.b": saved("a.b") } },
                version: 1,
            }),
        );

        await useCustomAttributesStore.persist.rehydrate();

        expect(useCustomAttributesStore.getState().names).toEqual(["hull"]);
        useCustomAttributesStore.getState().actions.removeAttribute("hull");
    });

    describe("persistence migration", () => {
        it("carries a custom field forward from a pre-rename v0 blob", async () => {
            // v0 persisted the whole `fields` map, built-ins included, under `fields`
            // rather than `customFields` — the shape `merge` reads today.
            localStorage.setItem(
                PERSIST_KEY,
                JSON.stringify({
                    state: {
                        fields: {
                            kind: { field: "kind", label: "Kind", path: "properties.opencop.kind" },
                            displayName: {
                                field: "displayName",
                                label: "Display Name",
                                path: "properties.opencop.displayName",
                            },
                            sidc: {
                                field: "sidc",
                                label: "SIDC",
                                path: "properties.opencop.element.symbol",
                            },
                            standardIdentity: {
                                field: "standardIdentity",
                                label: "Standard Identity",
                                path: "properties.opencop.element.symbol",
                            },
                            symbolSet: {
                                field: "symbolSet",
                                label: "Symbol Set",
                                path: "properties.opencop.element.symbol",
                            },
                            callsign: {
                                field: "callsign",
                                label: "callsign",
                                path: "properties.opencop.attributes.callsign",
                                custom: true,
                            },
                        },
                    },
                    version: 0,
                }),
            );

            await useCustomAttributesStore.persist.rehydrate();

            expect(useCustomAttributesStore.getState().names).toEqual(["callsign"]);
        });
    });
});
