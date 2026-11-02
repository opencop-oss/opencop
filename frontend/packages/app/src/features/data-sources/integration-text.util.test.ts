// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import type { ExtractorField } from "@opencop/api";
import { i18n } from "~/i18n/config";
import en from "~/locales/en/translation.json";
import fr from "~/locales/fr/translation.json";
import tw from "~/locales/tw/translation.json";
import { descriptorFieldLabel } from "./connection-setup/connection-setup.util";
import {
    extractorDisplayLabel,
    integrationDisplayName,
    integrationText,
} from "./integration-text.util";

const NATIVE = "integrations.5b96caf4-20dd-4a8a-8f1b-09fb594265be";
const PLAIN_TCP = `${NATIVE}.extractors.5e8b2c47-1f3a-4d69-8b0e-6a4c9d2f7e15`;
const UDP_LISTENER = `${NATIVE}.extractors.3f1d7a52-8c4e-4b0a-9e6f-2d5b7c8a1e93`;

/** Every leaf path under a subtree, so locales can be compared key for key. */
function leafPaths(node: unknown, prefix = ""): string[] {
    if (typeof node !== "object" || node === null) return [prefix];
    return Object.entries(node).flatMap(([key, child]) =>
        leafPaths(child, prefix ? `${prefix}.${key}` : key),
    );
}

describe("integration text", () => {
    afterEach(async () => {
        await i18n.changeLanguage("en");
    });

    it("translates a key this build knows into the user's language", async () => {
        await i18n.changeLanguage("fr");

        expect(integrationDisplayName({ name: "Native", nameKey: `${NATIVE}.name` }, i18n.t)).toBe(
            "Natif",
        );
        expect(
            extractorDisplayLabel({ label: "Plain TCP", labelKey: `${PLAIN_TCP}.label` }, i18n.t),
        ).toBe("TCP simple");
    });

    it("shows Core's text for a key this build has no translation for", async () => {
        await i18n.changeLanguage("fr");

        expect(
            integrationText("Signed bundle", "integrations.unknown.extractors.x.label", i18n.t),
        ).toBe("Signed bundle");
    });

    it("shows Core's text in English, which carries no integration entries", () => {
        expect(
            extractorDisplayLabel({ label: "Plain TCP", labelKey: `${PLAIN_TCP}.label` }, i18n.t),
        ).toBe("Plain TCP");
        expect(en).not.toHaveProperty("integrations");
    });

    it("shows Core's text for a key that names a group of keys", async () => {
        await i18n.changeLanguage("fr");

        expect(integrationText("Core label", `${UDP_LISTENER}.fields`, i18n.t)).toBe("Core label");
    });

    it("shows Core's text as-is, without expanding $t()", async () => {
        await i18n.changeLanguage("fr");

        expect(
            integrationText("See $t(" + `${NATIVE}.name` + ")", `${NATIVE}.missing`, i18n.t),
        ).toBe(`See $t(${NATIVE}.name)`);
    });

    it("shows Core's text when Core sends no key", async () => {
        await i18n.changeLanguage("fr");

        expect(integrationDisplayName({ name: "Writer" }, i18n.t)).toBe("Writer");
    });

    describe("field labels", () => {
        const listenPort: ExtractorField = {
            name: "listenPort",
            label: "Listen port",
            labelKey: `${UDP_LISTENER}.fields.listenPort`,
            required: true,
            kind: "TEXT",
        };

        it("translates a field by its own key", async () => {
            await i18n.changeLanguage("fr");

            expect(descriptorFieldLabel(listenPort, i18n.t)).toBe("Port d'écoute");
        });

        it("prefers the standard field translation over the field's own key", async () => {
            await i18n.changeLanguage("fr");

            expect(
                descriptorFieldLabel(
                    {
                        name: "host",
                        label: "Host",
                        labelKey: `${PLAIN_TCP}.fields.host`,
                        required: true,
                        kind: "TEXT",
                        standardKey: "HOST",
                    },
                    i18n.t,
                ),
            ).toBe("Hôte");
        });

        // i18next returns a function or object for these names instead of falling back.
        it.each(["constructor", "toString", "__proto__"])(
            "shows Core's label for a field named %s",
            async (name) => {
                await i18n.changeLanguage("fr");
                const key = `${UDP_LISTENER}.fields.${name}`;
                expect(
                    typeof i18n.t(key as never, { defaultValue: "x", returnObjects: true }),
                ).not.toBe("string");

                expect(
                    descriptorFieldLabel(
                        {
                            name,
                            label: "Core label",
                            labelKey: key,
                            required: true,
                            kind: "TEXT",
                        },
                        i18n.t,
                    ),
                ).toBe("Core label");
            },
        );
    });

    // i18n:extract never sees these keys, so nothing else catches a missing one.
    it("has the same integration keys in fr and tw", () => {
        expect(fr.integrations).toBeDefined();
        expect(leafPaths(tw.integrations).sort()).toEqual(leafPaths(fr.integrations).sort());
    });
});
