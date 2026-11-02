// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { ESLint } from "eslint";

/**
 * Executable proof that the package-boundary rules are **guarantees**, not
 * documentation. Each case lints a synthetic file at a real path and asserts the
 * rule fires (or deliberately does not) — so loosening `eslint.config.js` fails
 * here rather than being discovered when premium code turns up in the OSS bundle.
 *
 * These run ESLint in-process against the repo's flat config; nothing is written
 * to disk, so the paths need not exist.
 */

const eslint = new ESLint();

/** Rule ids reported for `code` as if it lived at `filePath`. */
async function rulesFiredFor(filePath: string, code: string): Promise<string[]> {
    const [result] = await eslint.lintText(code, { filePath, warnIgnored: false });
    return (result?.messages ?? []).map((m) => m.ruleId ?? "(fatal)");
}

const APP_FEATURE = "packages/app/src/features/map/synthetic-boundary-case.ts";
const SHARED_PACKAGE = "packages/layers/src/synthetic-boundary-case.ts";
const PLUGIN_PACKAGE = "packages/symbology-plugin/src/synthetic-boundary-case.ts";

describe("no production code imports the plugin SDK", () => {
    const importSdk = `import { createTestHost } from "@opencop/plugin-sdk/testing";\nexport const c = createTestHost;\n`;

    it.each([
        APP_FEATURE,
        "packages/app/src/features/plugin-manager/plugin-services.ts",
        SHARED_PACKAGE,
        PLUGIN_PACKAGE,
    ])("rejects %s importing it", async (file) => {
        expect(await rulesFiredFor(file, importSdk)).toContain("no-restricted-imports");
        expect(
            await rulesFiredFor(
                file,
                `import type { PluginContext } from "@opencop/plugin-sdk";\nexport type T = PluginContext;\n`,
            ),
        ).toContain("no-restricted-imports");
    });

    it.each([
        "packages/app/src/features/map/some-other.test.tsx",
        "packages/app/src/test/some-helper.ts",
        "packages/symbology-plugin/src/synthetic-boundary-case.test.ts",
    ])("lets %s, a test or the test harness, import it", async (file) => {
        expect(await rulesFiredFor(file, importSdk)).not.toContain("no-restricted-imports");
    });

    it("still allows an app feature the contracts and the plugin protocol", async () => {
        const fired = await rulesFiredFor(
            APP_FEATURE,
            `import type { ToolDescriptor } from "@opencop/tools";\nimport type { PluginContext } from "@opencop/contributions/plugin";\nexport type T = [ToolDescriptor, PluginContext];\n`,
        );
        expect(fired).not.toContain("no-restricted-imports");
    });
});

describe("host and shared code may not statically import plugin or premium packages", () => {
    it.each([
        ["@opencop/symbology-plugin", APP_FEATURE],
        ["@opencop/anything-plugin", APP_FEATURE],
        ["@opencop/premium-anything", APP_FEATURE],
        ["@opencop/symbology-plugin", SHARED_PACKAGE],
        // An app test is app code for this rule: only the one installer helper is exempt.
        ["@opencop/symbology-plugin", "packages/app/src/features/watchbox/synthetic.test.tsx"],
    ])("rejects %s from %s", async (specifier, filePath) => {
        const fired = await rulesFiredFor(filePath, `import "${specifier}";\n`);
        expect(fired).toContain("no-restricted-imports");
    });

    it("lets the test plugin installer import the plugin it installs, and keeps the app's other bans", async () => {
        const installer = "packages/app/src/test/plugins.ts";
        expect(
            await rulesFiredFor(installer, `import "@opencop/symbology-plugin";\n`),
        ).not.toContain("no-restricted-imports");
        expect(await rulesFiredFor(installer, `import "~/features/map";\n`)).toContain(
            "no-restricted-imports",
        );
    });
    it("lets the static plugin table import a plugin, and no other plugin-manager file", async () => {
        const importPlugin = `import "@opencop/symbology-plugin";\n`;
        expect(
            await rulesFiredFor(
                "packages/app/src/features/plugin-manager/static-plugins.ts",
                importPlugin,
            ),
        ).not.toContain("no-restricted-imports");
        expect(
            await rulesFiredFor(
                "packages/app/src/features/plugin-manager/plugin-loader.ts",
                importPlugin,
            ),
        ).toContain("no-restricted-imports");
    });
});

describe("plugins may not import host source", () => {
    // Depth is what matters, so it is covered explicitly: `group` is matched
    // gitignore-style rather than with minimatch, so both a bare `~` and `~/*`
    // already reach arbitrary depth. These cases pin that behaviour rather than a
    // past defect — a future switch to a `/`-bounded matcher would break the deep
    // cases here first.
    it.each(["~", "~/features", "~/features/map/thing", "~/features/map/deeply/nested"])(
        "rejects a plugin importing %s",
        async (specifier) => {
            const fired = await rulesFiredFor(PLUGIN_PACKAGE, `import "${specifier}";\n`);
            expect(fired).toContain("no-restricted-imports");
        },
    );

    it("rejects a plugin statically importing another plugin", async () => {
        const fired = await rulesFiredFor(PLUGIN_PACKAGE, `import "@opencop/other-plugin";\n`);
        expect(fired).toContain("no-restricted-imports");
    });

    // A relative path escapes into host source without spelling `~`. The
    // extensionless spelling is the load-bearing case: the compiler accepts it
    // under bundler resolution, and `boundaries` cannot resolve it — this
    // specifier rule is the ONLY mechanism that catches it.
    it.each([
        "../../app/src/features/contributions/tool-registry.ts",
        "../../app/src/features/toolbar/tool-registry",
    ])("rejects a plugin importing host source relatively via %s", async (specifier) => {
        const fired = await rulesFiredFor(PLUGIN_PACKAGE, `import "${specifier}";\n`);
        expect(fired).toContain("no-restricted-imports");
    });
});

// The same guarantee one layer down. A leaf is what makes a subject's move into a
// plugin a re-registration rather than a rewrite — both sides consume the same
// declaration — and that only holds while the leaf itself is host-free. The
// `boundaries` rule cannot enforce it (it does not resolve the `~` alias, pinned
// below), so without the specifier rule a leaf importing `~/features/...` lints
// clean. These cases fail with that config block removed.
describe("leaf and shared packages may not import host source", () => {
    it.each([
        "packages/layers/src/element-renderer.ts",
        "packages/tools/src/tool.ts",
        "packages/elements/src/element.ts",
        "packages/ui/src/inputs/OcSelect.tsx",
    ])("rejects %s importing host source", async (filePath) => {
        const fired = await rulesFiredFor(filePath, `import "~/features/map/thing";\n`);
        expect(fired).toContain("no-restricted-imports");
    });

    // The rule must not over-fire: a leaf depending on another leaf is the whole
    // point of the layering, and is how a plugin and a native subject end up
    // consuming the same declaration.
    it("still allows a leaf importing another leaf package", async () => {
        const fired = await rulesFiredFor(
            "packages/layers/src/element-renderer.ts",
            `import type { Element } from "@opencop/elements";\nexport type P = Element;\n`,
        );
        expect(fired).not.toContain("no-restricted-imports");
    });
});

describe("the MIL-STD stack is reachable only through the @opencop/symbology barrel", () => {
    // Every path that re-declares `no-restricted-imports` gets its own case: flat
    // config replaces a rule's options wholesale, so a block that forgets to
    // restate these un-bans them for everything it matches.
    it.each([
        ["@armyc2.c5isr.renderer/mil-sym-ts-web", SHARED_PACKAGE],
        ["milsymbol", SHARED_PACKAGE],
        ["@armyc2.c5isr.renderer/mil-sym-ts-web", APP_FEATURE],
        ["milsymbol", APP_FEATURE],
        ["milsymbol", PLUGIN_PACKAGE],
    ])("rejects a direct %s import from %s", async (specifier, filePath) => {
        const fired = await rulesFiredFor(filePath, `import "${specifier}";\n`);
        expect(fired).toContain("no-restricted-imports");
    });
});

describe("the interaction session shape and derivations stay pure", () => {
    const SESSION_FILE = "packages/app/src/features/map/interaction-runtime/session/session.ts";
    const DERIVE_FILE =
        "packages/app/src/features/map/interaction-runtime/arbitration/derive/synthetic-case.ts";
    const SESSION_DERIVE_FILE =
        "packages/app/src/features/map/interaction-runtime/session/derive/synthetic-case.ts";

    it.each([SESSION_FILE, DERIVE_FILE, SESSION_DERIVE_FILE])(
        "rejects an app-alias import from %s",
        async (file) => {
            const fired = await rulesFiredFor(
                file,
                `import { useMapStore } from "~/features/map/core/map.store";\nexport const t = useMapStore;\n`,
            );
            expect(fired).toContain("no-restricted-imports");
        },
    );

    it.each([SESSION_FILE, DERIVE_FILE])(
        "rejects a non-leaf @opencop package from %s",
        async (file) => {
            const fired = await rulesFiredFor(
                file,
                `import { Registry } from "@opencop/contributions/host";\nexport const t = Registry;\n`,
            );
            expect(fired).toContain("no-restricted-imports");
        },
    );

    it.each([SESSION_FILE, DERIVE_FILE])("rejects framework imports from %s", async (file) => {
        const fired = await rulesFiredFor(file, `import "zustand";\n`);
        expect(fired).toContain("no-restricted-imports");
    });

    it("allows the leaf packages and the sibling session shape", async () => {
        const fired = await rulesFiredFor(
            DERIVE_FILE,
            `import type { Element } from "@opencop/elements";\n` +
                `import { resolveClaims, type ToolDescriptor } from "@opencop/tools";\n` +
                `import type { Session } from "../../session/session";\n` +
                `export type T = [Element, ToolDescriptor, Session, typeof resolveClaims];\n`,
        );
        expect(fired).not.toContain("no-restricted-imports");
    });
});

describe("only commands write the interaction session", () => {
    const COMMANDS = "packages/app/src/features/map/interaction-runtime/session/commands.ts";
    const TEST_HELPER = "packages/app/src/test/interaction.ts";
    const RUNTIME_FILE =
        "packages/app/src/features/map/interaction-runtime/arbitration/synthetic-case.ts";
    const APP_TEST = "packages/app/src/features/map/synthetic-case.test.tsx";

    const aliasedWriter = `import { setSession } from "~/features/map/interaction-runtime/session/interaction.store";\nexport const w = setSession;\n`;
    const relativeWriter = `import { bumpRev } from "../session/interaction.store";\nexport const w = bumpRev;\n`;
    const readers =
        `import { getInteractionState, subscribeInteraction, useInteraction } from "~/features/map/interaction-runtime/session/interaction.store";\n` +
        `export const r = [getInteractionState, subscribeInteraction, useInteraction];\n`;

    it("rejects an app feature importing a writer by alias", async () => {
        expect(await rulesFiredFor(APP_FEATURE, aliasedWriter)).toContain("no-restricted-syntax");
    });

    it("rejects a runtime module importing a writer relatively", async () => {
        expect(await rulesFiredFor(RUNTIME_FILE, relativeWriter)).toContain("no-restricted-syntax");
    });

    it("rejects an app test importing a writer — tests go through ~/test/interaction", async () => {
        expect(await rulesFiredFor(APP_TEST, aliasedWriter)).toContain("no-restricted-syntax");
    });

    it("allows commands.ts and the test helper", async () => {
        expect(await rulesFiredFor(COMMANDS, aliasedWriter)).not.toContain("no-restricted-syntax");
        expect(await rulesFiredFor(TEST_HELPER, aliasedWriter)).not.toContain(
            "no-restricted-syntax",
        );
    });

    it("leaves the readers alone everywhere", async () => {
        expect(await rulesFiredFor(APP_FEATURE, readers)).not.toContain("no-restricted-syntax");
        expect(await rulesFiredFor(APP_TEST, readers)).not.toContain("no-restricted-syntax");
    });
});

/**
 * `boundaries/element-types` is set to `error`, but it classifies imports by
 * resolving the specifier to a file and silently skips whatever it cannot resolve.
 * No `import/resolver` is configured and no TypeScript path-alias resolver is
 * installed, so it resolves **no** `@opencop/*` or `~/*` specifier — every one of
 * its cross-package rules is advisory in practice, which is why the guarantees
 * above are specifier-based instead.
 *
 * This test pins that gap deliberately. If it starts failing, a resolver has been
 * configured and the `boundaries` rules became real: delete this test, and then
 * decide which of the specifier-based rules above are now redundant.
 */
describe("boundaries/* is weak for aliases, live for resolvable relative imports", () => {
    it("does not report boundaries/element-types for an aliased cross-boundary import", async () => {
        const fired = await rulesFiredFor(
            SHARED_PACKAGE,
            `import { thing } from "~/features/map/thing";\nexport const t = thing;\n`,
        );
        expect(fired).not.toContain("boundaries/element-types");
    });

    // Weak, not inert: a relative import with an explicit `.ts` extension DOES
    // resolve, and the element-types rule errors on it. This pins the fact that
    // deleting eslint-plugin-boundaries would lose a live guard — if this case
    // ever stops firing, the plugin really has gone inert and that decision
    // should be revisited.
    it("reports boundaries/element-types for a relative .ts cross-package import", async () => {
        const fired = await rulesFiredFor(
            PLUGIN_PACKAGE,
            `import "../../app/src/features/contributions/tool-registry.ts";\n`,
        );
        expect(fired).toContain("boundaries/element-types");
    });
});

describe("the interaction runtime imports no feature", () => {
    // Infrastructure, not a leaf feature: the runtime declares what it requires
    // in `interaction-runtime/contract.ts` and the host supplies it through
    // `MapManager.bindInteraction`. Features import the runtime; the runtime
    // imports leaf packages and `~/keyboard.util`, nothing under `~/features`.
    const SESSION_MODULE =
        "packages/app/src/features/map/interaction-runtime/session/synthetic-case.ts";
    const ARBITRATION_MODULE =
        "packages/app/src/features/map/interaction-runtime/arbitration/synthetic-case.ts";
    const UMBRELLA_TOP = "packages/app/src/features/map/interaction-runtime/synthetic-case.ts";
    const RUNTIME_TEST =
        "packages/app/src/features/map/interaction-runtime/session/synthetic-case.test.ts";

    const featureImport = `import { useMapStore } from "~/features/map/core/map.store";\nexport const t = useMapStore;\n`;
    const registryImport = `import { getTool } from "~/features/contributions/tool-registry";\nexport const t = getTool;\n`;
    const keyboardUtil = `import { isOverlayOpen } from "~/keyboard.util";\nexport const t = isOverlayOpen;\n`;

    it.each([SESSION_MODULE, ARBITRATION_MODULE, UMBRELLA_TOP])(
        "rejects a feature import from %s",
        async (file) => {
            expect(await rulesFiredFor(file, featureImport)).toContain("no-restricted-imports");
            expect(await rulesFiredFor(file, registryImport)).toContain("no-restricted-imports");
        },
    );

    it("allows ~/keyboard.util, which is not a feature", async () => {
        expect(await rulesFiredFor(ARBITRATION_MODULE, keyboardUtil)).not.toContain(
            "no-restricted-imports",
        );
    });

    it("leaves the runtime's own tests alone — they build their fakes from features", async () => {
        expect(await rulesFiredFor(RUNTIME_TEST, featureImport)).not.toContain(
            "no-restricted-imports",
        );
    });
});

describe("the interaction runtime owns every input source", () => {
    // The router is the app's one keydown listener; the runner binds every
    // contended pointer handler through the sources the manager hands it. A
    // feature that wants a key or a click registers — it does not listen.
    const FEATURE = "packages/app/src/features/watchbox/synthetic-case.tsx";
    const ROUTER =
        "packages/app/src/features/map/interaction-runtime/arbitration/synthetic-case.ts";
    const SESSION_MODULE =
        "packages/app/src/features/map/interaction-runtime/session/synthetic-case.ts";
    const MANAGER = "packages/app/src/features/map/MapManager.ts";
    const FEATURE_TEST = "packages/app/src/features/watchbox/synthetic-case.test.tsx";
    const HARNESS = "packages/app/src/test/synthetic-harness.ts";

    const documentKeydown = `document.addEventListener("keydown", () => {}, true);\n`;
    const windowKeydown = `window.addEventListener("keydown", () => {});\n`;
    const documentKeyup = `document.addEventListener("keyup", () => {});\n`;
    const pointerOnMap = `declare const m: { onMap(e: string, l: () => void): void };\nm.onMap("mousemove", () => {});\n`;
    const cameraOnMap = `declare const m: { onMap(e: string, l: () => void): void };\nm.onMap("moveend", () => {});\n`;
    const deckPick = `declare const m: { onDeck(e: string, l: () => void): void };\nm.onDeck("onClick", () => {});\n`;
    const documentBinding = `declare const m: { onDocument(e: string, l: () => void): void };\nm.onDocument("mouseup", () => {});\n`;

    it("rejects a document or window keydown listener in a feature", async () => {
        expect(await rulesFiredFor(FEATURE, documentKeydown)).toContain("no-restricted-syntax");
        expect(await rulesFiredFor(FEATURE, windowKeydown)).toContain("no-restricted-syntax");
    });

    it("allows the router's own keydown listener, and any other key", async () => {
        expect(await rulesFiredFor(ROUTER, documentKeydown)).not.toContain("no-restricted-syntax");
        expect(await rulesFiredFor(FEATURE, documentKeyup)).not.toContain("no-restricted-syntax");
    });

    it("rejects a keydown listener even inside the runtime's session module — it is arbitration's alone", async () => {
        expect(await rulesFiredFor(SESSION_MODULE, documentKeydown)).toContain(
            "no-restricted-syntax",
        );
    });

    it("rejects a contended onMap event, a deck pick and a document binding in a feature", async () => {
        expect(await rulesFiredFor(FEATURE, pointerOnMap)).toContain("no-restricted-syntax");
        expect(await rulesFiredFor(FEATURE, deckPick)).toContain("no-restricted-syntax");
        expect(await rulesFiredFor(FEATURE, documentBinding)).toContain("no-restricted-syntax");
    });

    it("allows a camera event anywhere", async () => {
        expect(await rulesFiredFor(FEATURE, cameraOnMap)).not.toContain("no-restricted-syntax");
    });

    it("allows the pointer bindings inside the runtime, in the manager that is the source, and in tests", async () => {
        for (const file of [SESSION_MODULE, ROUTER, MANAGER, FEATURE_TEST, HARNESS]) {
            expect(await rulesFiredFor(file, pointerOnMap), file).not.toContain(
                "no-restricted-syntax",
            );
            expect(await rulesFiredFor(file, deckPick), file).not.toContain("no-restricted-syntax");
        }
    });
});

// A native plugin is compiled into the app but held to a plugin's boundary: it
// reaches the host only through its contribution context and the `@opencop/*`
// packages, and only the composition root names it. These cases fail with either
// config block removed.
describe("the host and its native plugins may not import each other", () => {
    const RULE = "@typescript-eslint/no-restricted-imports";
    const HOST_ROOT_FILE = "packages/app/src/synthetic-boundary-case.tsx";
    const PLUGIN_ROOT_FILE = "packages/app/src/plugins/example/synthetic-boundary-case.ts";
    const PLUGIN_NESTED_FILE = "packages/app/src/plugins/example/editors/synthetic-case.tsx";
    const PLUGIN_DEEP_FILE = "packages/app/src/plugins/example/a/b/c/d/e/synthetic-case.ts";

    it.each([
        [APP_FEATURE, "~/plugins/example/install-example"],
        [APP_FEATURE, "../../plugins/example/model"],
        [HOST_ROOT_FILE, "./plugins/example/install-example"],
    ])("rejects host file %s importing %s", async (file, specifier) => {
        expect(await rulesFiredFor(file, `import "${specifier}";\n`)).toContain(RULE);
    });

    it("allows the host the plugin manager, which is not a native plugin", async () => {
        expect(
            await rulesFiredFor(
                APP_FEATURE,
                `import "~/features/plugin-manager/plugin-services";\n`,
            ),
        ).not.toContain(RULE);
    });

    it("exempts the composition root and host tests, and no other host file", async () => {
        const importPlugin = `import "~/plugins/example/install-example";\n`;
        for (const file of [
            "packages/app/src/features/contributions/install-built-in-subjects.ts",
            "packages/app/src/features/map/some-other.test.tsx",
        ]) {
            expect(await rulesFiredFor(file, importPlugin), file).not.toContain(RULE);
        }
        expect(
            await rulesFiredFor(
                "packages/app/src/features/contributions/some-other.ts",
                importPlugin,
            ),
        ).toContain(RULE);
    });

    it.each([
        [PLUGIN_ROOT_FILE, "~"],
        [PLUGIN_ROOT_FILE, "~/features/map/core/map.store"],
        [PLUGIN_ROOT_FILE, "~/queryClient"],
        [PLUGIN_ROOT_FILE, "~/plugins/example/model"],
        [PLUGIN_ROOT_FILE, "../other/model"],
        [PLUGIN_ROOT_FILE, "../../features/map/core/map.store"],
        [PLUGIN_ROOT_FILE, "../../queryClient"],
        [PLUGIN_NESTED_FILE, "../../other/model"],
        [PLUGIN_NESTED_FILE, "../../../queryClient"],
        [PLUGIN_NESTED_FILE, "../../../views/MapView"],
        [PLUGIN_DEEP_FILE, "../../../../../../../queryClient"],
    ])("rejects native-plugin file %s importing %s", async (file, specifier) => {
        expect(await rulesFiredFor(file, `import "${specifier}";\n`)).toContain(RULE);
    });

    it("lets a native plugin's tests reach the host's registries and test harness, and only its tests", async () => {
        const code = `import "~/test";\nimport "~/features/contributions/details-registry";\n`;
        expect(
            await rulesFiredFor("packages/app/src/plugins/example/editors/thing.test.tsx", code),
        ).not.toContain(RULE);
        expect(await rulesFiredFor(PLUGIN_NESTED_FILE, code)).toContain(RULE);
    });

    it("allows a native plugin its own files, however deep, and the @opencop packages", async () => {
        const packages = `import "@opencop/contributions";\nimport "@opencop/tools";\n`;
        expect(
            await rulesFiredFor(
                PLUGIN_ROOT_FILE,
                `import "./model";\nimport "./editors/x";\n${packages}`,
            ),
        ).not.toContain(RULE);
        expect(
            await rulesFiredFor(
                PLUGIN_NESTED_FILE,
                `import "./x";\nimport "../model";\n${packages}`,
            ),
        ).not.toContain(RULE);
        expect(
            await rulesFiredFor(
                "packages/app/src/plugins/example/a/b/c/synthetic-case.ts",
                `import "../../../model";\n`,
            ),
        ).not.toContain(RULE);
    });
});

// `@opencop/utils` is the one package every other package may import, so it must
// import none of them. These cases fail with its config block removed.
describe("@opencop/utils imports none of our packages, no React and no i18n instance", () => {
    const RULE = "@typescript-eslint/no-restricted-imports";
    const UTILS_FILE = "packages/utils/src/formatting/synthetic-boundary-case.ts";

    it.each([
        "@opencop/elements",
        "@opencop/layers",
        "react",
        "react-dom/client",
        "i18next",
        "react-i18next",
    ])("rejects %s", async (specifier) => {
        expect(await rulesFiredFor(UTILS_FILE, `import "${specifier}";\n`)).toContain(RULE);
    });

    it("rejects host source", async () => {
        expect(await rulesFiredFor(UTILS_FILE, `import "~/queryClient";\n`)).toContain(
            "no-restricted-imports",
        );
    });

    it("allows third-party libraries, its own modules, and a type-only i18next import", async () => {
        const code =
            `import { length } from "@turf/turf";\nimport { MILS_PER_DEGREE } from "../units/angle";\n` +
            `import type { TFunction } from "i18next";\nexport const x = [length, MILS_PER_DEGREE];\nexport type T = TFunction;\n`;
        expect(await rulesFiredFor(UTILS_FILE, code)).not.toContain(RULE);
    });

    it("lets its own tests import other packages", async () => {
        expect(
            await rulesFiredFor(
                "packages/utils/src/formatting/synthetic.test.ts",
                `import "@opencop/elements";\n`,
            ),
        ).not.toContain(RULE);
    });
});

// Filter rules are generic: every field, with its path and its symbol rules,
// arrives from the provider. These cases fail with its block removed.
describe("filter rules in @opencop/ui know no field", () => {
    const FILTER_FILE = "packages/ui/src/data/FilterRules/synthetic-boundary-case.ts";

    it("rejects importing @opencop/symbology", async () => {
        const fired = await rulesFiredFor(
            FILTER_FILE,
            `import { LegacyC } from "@opencop/symbology";\nexport const x = LegacyC;\n`,
        );
        expect(fired).toContain("@typescript-eslint/no-restricted-imports");
    });

    it("rejects naming an element property path", async () => {
        const fired = await rulesFiredFor(
            FILTER_FILE,
            `export const path = "properties.opencop.displayName";\n`,
        );
        expect(fired).toContain("no-restricted-syntax");
    });

    it("allows the generic predicate language", async () => {
        const fired = await rulesFiredFor(
            FILTER_FILE,
            `import { v4 } from "uuid";\nexport const id = v4();\n`,
        );
        expect(fired).not.toContain("@typescript-eslint/no-restricted-imports");
        expect(fired).not.toContain("no-restricted-syntax");
    });
});

// Nearly every package imports `@opencop/elements`, so it imports only the lowest
// tier. These cases fail with its block removed.
describe("@opencop/elements imports only utils among our packages", () => {
    const RULE = "@typescript-eslint/no-restricted-imports";
    const ELEMENTS_FILE = "packages/elements/src/types/synthetic-boundary-case.ts";

    it.each(["@opencop/layers", "@opencop/tools", "@opencop/ui", "@opencop/utils-extra"])(
        "rejects %s",
        async (specifier) => {
            expect(await rulesFiredFor(ELEMENTS_FILE, `import "${specifier}";\n`)).toContain(RULE);
        },
    );

    it("allows utils", async () => {
        const code = `import "@opencop/utils";\n`;
        expect(await rulesFiredFor(ELEMENTS_FILE, code)).not.toContain(RULE);
    });
});

// The host names no element type: each type's subject contributes what used to
// be a branch in host code. These cases fail with the rule switched off.
describe("the host names no element type", () => {
    const RULE = "opencop/no-element-type-branch";
    const HOST_FILE = "packages/app/src/features/events/synthetic-boundary-case.ts";

    it.each([
        ["an equality test", `export const f = (t: string) => t === "polygon";\n`],
        ["an inequality test", `export const f = (t: string) => "symbol" !== t;\n`],
        [
            "a case label",
            `export const f = (t: string) => { switch (t) { case "arrow": return 1; } return 0; };\n`,
        ],
        [
            "a type guard",
            `declare const Angle: { is(e: unknown): boolean };\nexport const f = (e: unknown) => Angle.is(e);\n`,
        ],
        [
            "a layer-spec rebuild by type",
            `declare const m: { updateLayerSpec(t: string): void };\nm.updateLayerSpec("graphic");\n`,
        ],
        ["a media-icon equality test", `export const f = (t: string) => t === "media-icon";\n`],
        [
            "a media-icon type guard",
            `declare const MediaIcon: { is(e: unknown): boolean };\nexport const f = (e: unknown) => MediaIcon.is(e);\n`,
        ],
        ["a list of types", `export const TYPES = ["line", "polygon"];\n`],
        ["a subject tool's id", `export const TOOL = "pointTool";\n`],
    ])("rejects %s in host code", async (_, code) => {
        expect(await rulesFiredFor(HOST_FILE, code)).toContain(RULE);
    });

    it("allows the same in a native plugin and in host tests", async () => {
        const code = `export const f = (t: string) => t === "polygon";\nexport const TOOL = "pointTool";\n`;
        for (const file of [
            "packages/app/src/plugins/example/thing.ts",
            "packages/app/src/features/events/thing.test.ts",
        ]) {
            expect(await rulesFiredFor(file, code), file).not.toContain(RULE);
        }
    });

    it("does not flag a single type name that is not a branch, such as a field name", async () => {
        const code = `export const field = { name: "polygon" };\nexport const t = "symbol";\n`;
        expect(await rulesFiredFor(HOST_FILE, code)).not.toContain(RULE);
    });
});
