// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import eslintReact from "@eslint-react/eslint-plugin";
import js from "@eslint/js";
import queryPlugin from "@tanstack/eslint-plugin-query";
import eslintConfigPrettier from "eslint-config-prettier";
import boundaries from "eslint-plugin-boundaries";
import i18next from "eslint-plugin-i18next";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import { globalIgnores } from "eslint/config";
import globals from "globals";
import tseslint from "typescript-eslint";
import { globSync, readFileSync } from "node:fs";

/**
 * `@tanstack/react-virtual` is stubbed globally for tests (`resolve.alias` in
 * vitest.config.ts → packages/app/src/test/react-virtual-mock.ts), so a per-file
 * `vi.mock` both duplicates it and overrides it with a less faithful copy. Shared
 * between the app-test guard and the leaf-package guard below.
 */
const RESTRICT_VIRTUALIZER_MOCK = {
    selector:
        'CallExpression[callee.object.name="vi"][callee.property.name=/^(mock|doMock)$/] > Literal[value="@tanstack/react-virtual"]',
    message:
        "Don't mock @tanstack/react-virtual — the Vitest config aliases it to the shared stub. Use virtualizerMock from ~/test/react-virtual-mock to set the window or assert item counts.",
};

/**
 * E2E existence probes: `locator.isVisible({ timeout })` silently ignores its
 * timeout (the option is deprecated in Playwright), so the check returns
 * immediately and races the render — an existing row reads as missing, and the
 * spec creates a duplicate the server then rejects. This has bitten the
 * tile-source and layer setup specs. Bare `isVisible()` stays allowed: it makes
 * no promise to wait.
 */
const E2E_ISVISIBLE_IGNORES_TIMEOUT = {
    selector: 'CallExpression[callee.property.name="isVisible"][arguments.length>0]',
    message:
        "isVisible() ignores its `timeout` option, so it races the render. Use `locator.waitFor({ state: 'visible', timeout }).then(() => true).catch(() => false)` for an existence probe, or `expect(locator).toBeVisible({ timeout })` for an assertion.",
};

/**
 * The accessible names of the map toolbar's tools and the mapbar's panel
 * toggles, read from the English locales so a new tool or panel is covered the
 * moment it gets a label: the app's `mapbar.*` (less its overflow trigger), its
 * `toolbar.*Tool` plus the interact / lasso / measure modes, and every plugin's
 * `toolbar.*`.
 */
const OVERFLOW_BAR_LABELS = (() => {
    const readJson = (path) => JSON.parse(readFileSync(new URL(path, import.meta.url), "utf8"));
    const app = readJson("./packages/app/src/locales/en/translation.json");
    const mapbar = Object.entries(app.mapbar)
        .filter(([key]) => key !== "morePanels")
        .map(([, label]) => label);
    const tools = Object.entries(app.toolbar)
        .filter(([key]) => key.endsWith("Tool") || ["interact", "lasso", "measure"].includes(key))
        .map(([, label]) => label);
    const pluginTools = globSync("packages/*-plugin/src/locales/en.json", {
        cwd: new URL(".", import.meta.url),
    }).flatMap((path) => Object.values(readJson(`./${path}`).toolbar ?? {}));
    return [...new Set([...mapbar, ...tools, ...pluginTools])];
})();

/**
 * The map toolbar and the mapbar each fold the buttons they cannot fit behind a
 * "more" popover, and which ones fold shifts whenever a tool, plugin, panel or
 * feature flag is added — so a bare `getByRole("button", { name })` for one of
 * them passes until an unrelated change moves it, then fails. Specs and helpers
 * reach them through `map-helpers.ts`, which opens the overflow when needed.
 */
const E2E_OVERFLOW_BAR_BUTTON_BY_NAME = {
    selector: `CallExpression[callee.property.name="getByRole"][arguments.0.value="button"] > ObjectExpression > Property[key.name="name"][value.value=/^(${OVERFLOW_BAR_LABELS.map((label) => label.replace(/[.*+?^${}()|[\]\\/]/g, "\\$&")).join("|")})$/]`,
    message:
        "This toolbar tool or mapbar panel may be folded into its bar's overflow popover. Use selectTool / withToolButton / expectToolEnabled / expectToolDisabled or togglePanel / withPanelToggle from e2e/helpers/map-helpers.ts.",
};

/**
 * The interaction session store has two writers — `setSession` and `bumpRev`
 * — and they belong to `features/map/interaction-runtime/session/commands.ts` alone (tests reach them
 * through `~/test/interaction`). Every other module reads: `useInteraction`,
 * `getInteractionState`, `subscribeInteraction`.
 *
 * Encoded as `no-restricted-syntax` because the store is imported by relative
 * AND aliased specifiers, which `no-restricted-imports` cannot name together.
 * Shared between the app-source and app-test blocks because that rule, too,
 * replaces its options wholesale. Pinned by eslint.boundaries.test.ts.
 */
const INTERACTION_WRITERS_ARE_COMMANDS_ONLY = {
    selector:
        "ImportDeclaration[source.value=/interaction\\.store$/] > ImportSpecifier[imported.name=/^(setSession|bumpRev)$/]",
    message:
        "Only features/map/interaction-runtime/session/commands.ts writes the interaction session (tests: ~/test/interaction). " +
        "Read it through useInteraction / getInteractionState / subscribeInteraction.",
};

/**
 * The interaction runtime's keyboard router is the app's ONE `document` keydown
 * listener. A second listener is a party the Escape ladder cannot rank — the
 * events panel kept one, the router's rung silently preempted it, and only a
 * human pressing the key found out. A party that wants a key registers: an
 * Escape claimant (`registerEscapeClaimant`), a chord (`registerKeyBinding`),
 * or the descriptor's `shortcut`. Applies everywhere but `arbitration/`;
 * mirrored by `arbitration/escape-census.test.ts`; pinned by
 * eslint.boundaries.test.ts.
 */
const NO_KEYDOWN_LISTENER_OUTSIDE_ARBITRATION = {
    selector:
        'CallExpression[callee.object.name=/^(document|window)$/][callee.property.name="addEventListener"][arguments.0.value="keydown"]',
    message:
        "The interaction runtime's keyboard router is the one document keydown listener. " +
        "Register instead: ctx.registerEscapeClaimant for Escape, registerKeyBinding for a Ctrl/Cmd chord, " +
        "the descriptor's `shortcut` for a tool letter. See docs/best-practices/interaction-runtime.md.",
};

/**
 * A contended pointer event — a press, move, release, click, double-click or
 * right-click on the map, any deck pick, any capture-phase document event —
 * belongs to whichever slot owns the pointer, and the runner binds that
 * owner's set through the `InteractionSources` value `MapManager.bindInteraction`
 * hands the runtime. A subscription anywhere else is a party the runner cannot
 * arbitrate: bound under a live session, gating itself per event. The public
 * `MapManager.onMap` already takes camera events only and `onDeck` is private,
 * so this is the belt to that type's braces (`as any` and fake hosts pass a
 * type). Applies outside the runtime, the manager and the tests; mirrored by
 * `arbitration/pointer-census.test.ts`; pinned by eslint.boundaries.test.ts.
 */
const NO_CONTENDED_POINTER_SUBSCRIPTION_OUTSIDE_RUNTIME = [
    {
        selector:
            'CallExpression[callee.property.name="onMap"][arguments.0.value=/^(mousedown|mousemove|mouseup|click|dblclick|contextmenu)$/]',
        message:
            "A pointer event belongs to whichever slot owns the pointer; only the interaction runtime binds it. " +
            "Camera events (move, zoom, idle) are free; the pointer's position is MapManager.pointerLngLat(); " +
            "a click on something your feature draws is ctx.registerPointerClaimant. See docs/best-practices/interaction-runtime.md.",
    },
    {
        selector: "CallExpression[callee.property.name=/^(onDeck|onDocument)$/]",
        message:
            "Deck picks and capture-phase document events reach the interaction runtime alone, through MapManager.bindInteraction. " +
            "Hover is the store's to read; a click on something your feature draws is ctx.registerPointerClaimant. " +
            "See docs/best-practices/interaction-runtime.md.",
    },
];

/**
 * Reach the MIL-STD stack only through the `@opencop/symbology` barrel.
 *
 * Shared between config blocks because ESLint flat config replaces a rule's
 * options **wholesale** rather than merging them: a later block that re-declares
 * `no-restricted-imports` silently drops every restriction it does not restate.
 */
const MIL_STD_WRAPPER_ONLY = [
    {
        name: "@armyc2.c5isr.renderer/mil-sym-ts-web",
        message:
            "Reach mil-sym-ts-web only through the @opencop/symbology barrel, " +
            "or (inside the symbology package) packages/symbology/src/mil-sym-ts-wrapper. " +
            "Don't import the library directly.",
    },
    {
        name: "milsymbol",
        message:
            "Reach milsymbol only through the @opencop/symbology barrel, " +
            "or (inside the symbology package) packages/symbology/src/milsymbol-wrapper. " +
            "Don't import the library directly.",
    },
];

/**
 * Plugin/premium boundary (hard error): host and shared code must never statically
 * import a plugin or premium package — they load at runtime through the plugin
 * manager, which is what keeps premium code out of the OSS bundle. The one exception is
 * the app's temporary static plugin table ({@link STATIC_PLUGIN_TABLE}).
 */
const NO_STATIC_PLUGIN_IMPORTS = [
    {
        group: ["@opencop/*-plugin", "@opencop/premium-*"],
        message:
            "Do not statically import plugin/premium packages — they load at runtime " +
            "via the plugin manager (this is what keeps premium code out of the OSS bundle).",
    },
];

/**
 * No production code imports `@opencop/plugin-sdk`: it holds only tooling — the test
 * hosts (Vitest, Testing Library) and the Node-only checks — so an import outside a test
 * or a script would pull a test framework or Node built-ins into a bundle. The plugin
 * protocol and every contract live in `@opencop/contributions`. One rule for the whole
 * package, applied to the app, shared packages and plugins; tests and the app's test
 * harness are exempt.
 */
const NO_SDK_IN_PRODUCTION = [
    {
        group: ["@opencop/plugin-sdk", "@opencop/plugin-sdk/*"],
        message:
            "Only tests and scripts may import @opencop/plugin-sdk — it is test and CI " +
            "tooling. Take the plugin protocol and the contracts from @opencop/contributions.",
    },
];

/**
 * Barrel files are banned in app code (app-organization Golden Rule 5). Scoped to
 * `~/features/` because it is the only app directory of sub-directories, so a single
 * segment there names a feature's barrel rather than a module.
 */
const NO_APP_BARRELS = [
    {
        // `regex`, not `group`: group patterns use gitignore semantics and would also
        // match every module nested under a feature.
        regex: "^~/features/[^/]+$",
        message:
            "Barrel files are banned in app code. Import the concrete module " +
            "(`~/features/<feature>/<Module>`). See " +
            "docs/best-practices/app-organization/imports.md.",
    },
];

/**
 * Native plugins live in `packages/app/src/plugins/<subject>/`, a sibling of
 * `features/`. The host may not import one, and a native plugin may not import
 * the host: it reaches the host only through its contribution context and the
 * `@opencop/*` packages. Both rules use `@typescript-eslint/no-restricted-imports`
 * — a different rule id from the core `no-restricted-imports` the other blocks
 * re-declare — so no later block replaces their options wholesale. `regex`, not
 * `group`: gitignore-style groups miss deep relative escapes such as
 * `../../../queryClient`.
 *
 * A relative climb is legal only while it stays inside the plugin, so its limit
 * depends on how deep the importing file sits: one block per depth up to
 * `NATIVE_PLUGIN_EXACT_DEPTH`, and files deeper than that get that depth's limit,
 * which over-flags rather than lets an escape through.
 */
const COMPOSITION_ROOT = "packages/app/src/features/contributions/install-built-in-subjects.ts";
const HOST_MAY_NOT_IMPORT_A_NATIVE_PLUGIN = [
    {
        regex: "(^|/)plugins(/|$)",
        message:
            "The host may not import a native plugin: it contributes through its install(ctx). " +
            "Only the composition root names native plugins.",
    },
];
const NATIVE_PLUGIN_MAY_NOT_IMPORT_THE_HOST = [
    {
        regex: "^~(/|$)",
        message:
            "A native plugin may not import host source (`~`). Use @opencop/* packages and its contribution context.",
    },
];
const NATIVE_PLUGIN_EXACT_DEPTH = 4;
const NATIVE_PLUGIN_TESTS = "packages/app/src/plugins/**/*.test.{ts,tsx}";

/** The app test helper allowed to import a plugin, to install it through the real manager. */
const TEST_PLUGIN_INSTALLER = "packages/app/src/test/plugins.ts";

/**
 * The one production file allowed to import a plugin: the temporary id → module table the
 * plugin loader resolves an enablement through, until plugins load at run time from their
 * `entryUrl`. Delete it, and its exceptions here, then.
 */
const STATIC_PLUGIN_TABLE = "packages/app/src/features/plugin-manager/static-plugins.ts";

/**
 * The patterns for a native-plugin file `depth` folders below its plugin's root
 * (`plugins/<subject>/x.ts` is depth 0): climbing more than `depth` levels
 * leaves the plugin.
 */
function nativePluginPatterns(depth) {
    return [
        ...NATIVE_PLUGIN_MAY_NOT_IMPORT_THE_HOST,
        {
            regex: `^(\\.\\./){${depth + 1},}`,
            message:
                "A native plugin may not climb out of its own folder: that reaches the host or another plugin.",
        },
    ];
}

/**
 * The host names no element type: what a type looks like, how it is edited and
 * what it shows is its subject's contribution. Flags, in host code:
 * `=== / !==` or `case` on an element-type literal, a type guard
 * (`<Type>.is(…)`), `updateLayerSpec("<type>")`, an array of two or more
 * element-type literals, and a subject tool's id.
 */
const ELEMENT_TYPES = new Set([
    "symbol",
    "graphic",
    "point",
    "line",
    "arrow",
    "angle",
    "polygon",
    "circle",
    "rectangle",
    "ellipse",
    "media-icon",
]);
const ELEMENT_TYPE_GUARDS = new Set([
    "Symbol",
    "Graphic",
    "Point",
    "Line",
    "Arrow",
    "Angle",
    "Polygon",
    "Circle",
    "Rectangle",
    "Ellipse",
    "MediaIcon",
]);
const SUBJECT_TOOL_IDS = new Set([
    "pointTool",
    "lineTool",
    "arrowTool",
    "angleTool",
    "polygonTool",
    "circleTool",
    "rectangleTool",
    "symbolTool",
]);
const isTypeLiteral = (node) => node?.type === "Literal" && ELEMENT_TYPES.has(node.value);
const noElementTypeBranch = {
    meta: {
        type: "problem",
        messages: {
            branch: 'The host branches on element type "{{type}}". Contribute the behaviour from its subject (a renderer field, a details surface, a menu item, a tool\'s edits).',
            guard: "The host narrows by `{{guard}}.is`. Contribute the behaviour from the type's subject.",
            list: "The host lists element types. Derive the list from the registered contributions.",
            tool: 'The host names the subject tool "{{tool}}". Let the subject contribute the action.',
        },
        schema: [],
    },
    create(context) {
        return {
            BinaryExpression(node) {
                if (!["===", "!==", "==", "!="].includes(node.operator)) return;
                const literal = isTypeLiteral(node.left)
                    ? node.left
                    : isTypeLiteral(node.right)
                      ? node.right
                      : null;
                if (literal)
                    context.report({ node, messageId: "branch", data: { type: literal.value } });
            },
            SwitchCase(node) {
                if (isTypeLiteral(node.test))
                    context.report({ node, messageId: "branch", data: { type: node.test.value } });
            },
            CallExpression(node) {
                const callee = node.callee;
                if (callee.type !== "MemberExpression" || callee.property.type !== "Identifier")
                    return;
                if (
                    callee.property.name === "is" &&
                    callee.object.type === "Identifier" &&
                    ELEMENT_TYPE_GUARDS.has(callee.object.name)
                ) {
                    context.report({
                        node,
                        messageId: "guard",
                        data: { guard: callee.object.name },
                    });
                }
                if (
                    callee.property.name === "updateLayerSpec" &&
                    isTypeLiteral(node.arguments[0])
                ) {
                    context.report({
                        node,
                        messageId: "branch",
                        data: { type: node.arguments[0].value },
                    });
                }
            },
            ArrayExpression(node) {
                if (node.elements.filter(isTypeLiteral).length >= 2)
                    context.report({ node, messageId: "list" });
            },
            Literal(node) {
                if (SUBJECT_TOOL_IDS.has(node.value))
                    context.report({ node, messageId: "tool", data: { tool: node.value } });
            },
        };
    },
};
const opencopPlugin = { rules: { "no-element-type-branch": noElementTypeBranch } };

export default tseslint.config([
    // sidc.generated.ts is a 500KB+ static SIDC data table with no React/hooks. The React
    // Compiler pass bundled into eslint-plugin-react-hooks parses it anyway and, at
    // >500KB, trips Babel's "deoptimised styling" note. tsc covers the meaningful
    // checks (unused locals/params, types), so skip ESLint on it.
    globalIgnores(["dist", "packages/proto/src/ts", "packages/symbology/src/sidc.generated.ts"]),
    {
        files: ["**/*.{ts,tsx}"],
        extends: [
            js.configs.recommended,
            tseslint.configs.recommended,
            react.configs.flat.recommended,
            react.configs.flat["jsx-runtime"],
            reactHooks.configs.flat["recommended-latest"],
            reactRefresh.configs.vite,
            ...queryPlugin.configs["flat/recommended"],
            eslintReact.configs["recommended-typescript"],
            eslintReact.configs["disable-conflict-eslint-plugin-react"],
        ],
        languageOptions: {
            ecmaVersion: 2020,
            globals: globals.browser,
        },
        settings: {
            react: {
                version: "detect",
            },
        },
        plugins: { i18next },
        rules: {
            // Enforce hooks rules as errors
            "react-hooks/exhaustive-deps": "error",

            // Centralize logging through js-logger (`Logger.get("<category>")`)
            // instead of raw console. For an intentional, permanent console call
            // (e.g. a diagnostic that must bypass the logger), add an explicit
            // `// eslint-disable-line no-console -- <reason>`.
            "no-console": "error",

            // @eslint-react — React 19 context patterns
            "@eslint-react/no-context-provider": "error",
            "@eslint-react/no-use-context": "error",
            "@eslint-react/no-unstable-context-value": "error",
            "@eslint-react/prefer-use-state-lazy-initialization": "error",
            "@eslint-react/hooks-extra/no-direct-set-state-in-use-effect": "error",

            // Catch hardcoded strings that should use t()
            "i18next/no-literal-string": [
                "error",
                {
                    mode: "jsx-only",
                    words: {
                        exclude: [
                            "[0-9!-/:-@\\[-`{-~]+", // Default: ASCII punctuation, numbers, symbols
                            "[A-Z_-]+", // Default: UPPER_CASE constants
                            "[▸▾→←•×°—]+", // UI symbols: disclosure triangles, arrows, bullets, degree, em dash
                            /^[\p{Emoji}\s]+$/u, // Emoji (all Unicode emoji + surrounding whitespace)
                        ],
                    },
                    "jsx-attributes": {
                        include: [
                            // Standard HTML/ARIA
                            "placeholder",
                            "aria-label",
                            "title",
                            "alt",
                            // Dialog text props
                            "message",
                            "confirmLabel",
                            "cancelLabel",
                            "submitLabel",
                            "actionLabel",
                            "loadingText",
                            "secondaryText",
                            // Input/form text props
                            "label",
                            "labelEnd",
                            "emptyMessage",
                            // Display/navigation text props
                            "heading",
                            "content",
                            "tooltip",
                            "buttonLabel",
                            "subtitle",
                            // Table text props
                            "header",
                        ],
                    },
                    callees: {
                        exclude: [
                            // Defaults (specifying callees replaces built-in defaults)
                            "i18n(ext)?",
                            "t",
                            "require",
                            "addEventListener",
                            "removeEventListener",
                            "postMessage",
                            "getElementById",
                            "dispatch",
                            "commit",
                            "includes",
                            "indexOf",
                            "endsWith",
                            "startsWith",
                            // react-hook-form field registration
                            "register",
                            // Chakra UI transition helper
                            "getDefaultTransition",
                            // Luxon date formatting
                            "toFormat",
                        ],
                    },
                },
            ],
        },
    },
    // Package boundary enforcement
    {
        files: ["packages/**/*.{ts,tsx}"],
        plugins: { boundaries },
        settings: {
            "boundaries/elements": [
                { type: "app-root", pattern: "packages/app/src/*", mode: "file" },
                { type: "views", pattern: "packages/app/src/views/**" },
                { type: "layouts", pattern: "packages/app/src/layouts/**" },
                // The static plugin table, which alone may import a plugin package.
                { type: "plugin-loader", pattern: STATIC_PLUGIN_TABLE, mode: "file" },
                { type: "features", pattern: "packages/app/src/features/**" },
                // Plugin architecture element types — MUST precede the packages
                // catch-all (first match wins). `plugin` and `premium` are
                // runtime-loaded and unimportable by the host.
                { type: "plugin-sdk", pattern: "packages/plugin-sdk/**" },
                { type: "plugin", pattern: "packages/*-plugin/**" },
                { type: "premium", pattern: "packages/premium-*/**" },
                { type: "packages", pattern: "packages/!(app)/**" },
            ],
            "boundaries/ignore": ["**/*.test.{ts,tsx}", "**/*.spec.{ts,tsx}"],
        },
        rules: {
            "boundaries/entry-point": [
                "warn",
                {
                    default: "allow",
                    rules: [{ target: "packages", allow: "index.ts" }],
                },
            ],
            "boundaries/element-types": [
                "error",
                {
                    default: "disallow",
                    rules: [
                        // Packages cannot import from app
                        { from: "packages", allow: ["packages"] },
                        // Features describe their own behaviour with the **leaf**
                        // contracts (`@opencop/tools`, `@opencop/layers`), never with
                        // the plugin SDK: app code that needs the SDK to say what its
                        // own toolbar or renderers are has the dependency backwards.
                        {
                            from: "features",
                            allow: ["packages", "features", "plugin-loader"],
                        },
                        // Layouts can import from features and packages
                        { from: "layouts", allow: ["features", "packages"] },
                        // Views can import from packages
                        { from: "views", allow: ["packages"] },
                        // App root can import from everything EXCEPT the SDK — it
                        // mounts the plugin runtime, it does not describe plugins.
                        {
                            from: "app-root",
                            allow: ["app-root", "views", "layouts", "features", "packages"],
                        },
                        // SDK is standalone contracts; it may lean on shared packages.
                        { from: "plugin-sdk", allow: ["packages"] },
                        // The static table may import a plugin, until plugins load at run
                        // time; the import rule confines the exception to that file.
                        { from: "plugin-loader", allow: ["packages", "plugin", "premium"] },
                        // Plugins depend on shared packages only — never host source,
                        // features, or the SDK (test tooling, imported by tests alone).
                        { from: "plugin", allow: ["packages"] },
                        { from: "premium", allow: ["packages"] },
                    ],
                },
            ],
        },
    },
    // Symbology library seams: enforce wrapper-only access to mil-sym-ts-web
    // and milsymbol across ALL packages. The two wrapper folders are the only
    // permitted importers; every other consumer goes through the
    // @opencop/symbology barrel (SIDC / Validator / Renderer / Catalog /
    // Defaults). Reintroducing a direct import anywhere else fails CI — do not
    // add a per-file override; route the consumer through the wrapper (growing
    // a wrapper export if needed) instead.
    {
        files: ["packages/**/*.{ts,tsx}"],
        ignores: [
            "packages/symbology/src/mil-sym-ts-wrapper/**",
            "packages/symbology/src/milsymbol-wrapper/**",
            STATIC_PLUGIN_TABLE,
        ],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: NO_STATIC_PLUGIN_IMPORTS,
                },
            ],
        },
    },
    // Plugins must never import host source (`~`). Error-level so it is a
    // guarantee, not the advisory `boundaries` warning.
    {
        files: ["packages/*-plugin/**/*.{ts,tsx}", "packages/premium-*/**/*.{ts,tsx}"],
        ignores: ["**/*.test.{ts,tsx}"],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            // `group` is matched gitignore-style, not minimatch, so a
                            // bare `~` already covers every path beneath it and the
                            // second entry is belt-and-braces. `~/**` over `~/*`
                            // states the intent explicitly; both behave identically
                            // here (see eslint.boundaries.test.ts).
                            group: ["~", "~/**"],
                            message:
                                "Plugins must not import host source (~). Depend only on " +
                                "@opencop/contributions and shared @opencop/* packages.",
                        },
                        {
                            // A relative path escapes the plugin into host source
                            // without ever spelling `~`. Matching the specifier TEXT
                            // catches both spellings a violator could write: with a
                            // `.ts` extension (which `boundaries/element-types` also
                            // errors on) and extensionless — which NOTHING else
                            // catches: the boundaries resolver cannot resolve it,
                            // while the compiler accepts it under bundler
                            // resolution. Pinned by eslint.boundaries.test.ts.
                            group: ["**/app/src/**"],
                            message:
                                "Plugins must not import host source (relative escape " +
                                "into packages/app). Depend only on @opencop/contributions " +
                                "and shared @opencop/* packages.",
                        },
                        ...NO_STATIC_PLUGIN_IMPORTS,
                        ...NO_SDK_IN_PRODUCTION,
                    ],
                    paths: MIL_STD_WRAPPER_ONLY,
                },
            ],
        },
    },
    // A plugin's tests may use the SDK's test kit; the host-source bans still apply.
    {
        files: ["packages/*-plugin/**/*.test.{ts,tsx}", "packages/premium-*/**/*.test.{ts,tsx}"],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            // `group` is matched gitignore-style, not minimatch, so a
                            // bare `~` already covers every path beneath it and the
                            // second entry is belt-and-braces. `~/**` over `~/*`
                            // states the intent explicitly; both behave identically
                            // here (see eslint.boundaries.test.ts).
                            group: ["~", "~/**"],
                            message:
                                "Plugins must not import host source (~). Depend only on " +
                                "@opencop/contributions and shared @opencop/* packages.",
                        },
                        {
                            // A relative path escapes the plugin into host source
                            // without ever spelling `~`. Matching the specifier TEXT
                            // catches both spellings a violator could write: with a
                            // `.ts` extension (which `boundaries/element-types` also
                            // errors on) and extensionless — which NOTHING else
                            // catches: the boundaries resolver cannot resolve it,
                            // while the compiler accepts it under bundler
                            // resolution. Pinned by eslint.boundaries.test.ts.
                            group: ["**/app/src/**"],
                            message:
                                "Plugins must not import host source (relative escape " +
                                "into packages/app). Depend only on @opencop/contributions " +
                                "and shared @opencop/* packages.",
                        },
                        ...NO_STATIC_PLUGIN_IMPORTS,
                    ],
                    paths: MIL_STD_WRAPPER_ONLY,
                },
            ],
        },
    },
    // Leaf and shared packages must never import host source (`~`) either. The
    // host and every plugin depend on these packages, so a host import here makes
    // that dependency circular and takes the "one declaration both sides consume"
    // property with it — the thing that lets a native subject move into a plugin
    // as a re-registration rather than a rewrite.
    //
    // Needed as its own specifier rule for the same reason the plugin rule above
    // is: `boundaries/element-types` cannot resolve the `~` alias, so it is
    // advisory here (pinned by eslint.boundaries.test.ts). Without this block a
    // leaf could import `~/features/...` and lint clean.
    // Flat config replaces a rule's options wholesale, so this block restates the
    // MIL-STD and static-plugin restrictions the general `packages/**` block above
    // applies — and repeats its `ignores`, or it would re-ban the two sanctioned
    // wrapper folders.
    //
    // Test files are exempt from the `~` rule: a leaf's tests legitimately reach
    // the app's test harness and i18n setup (`~/test`, `~/i18n/config`), which
    // is a test-only dependency and not a runtime edge.
    {
        files: ["packages/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/**",
            "packages/*-plugin/**",
            "packages/premium-*/**",
            "packages/symbology/src/mil-sym-ts-wrapper/**",
            "packages/symbology/src/milsymbol-wrapper/**",
            "**/*.test.{ts,tsx}",
        ],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            group: ["~", "~/**"],
                            message:
                                "Shared and leaf packages must not import host source (~). " +
                                "The host and every plugin depend on this package; a host " +
                                "import here would make that dependency circular.",
                        },
                        ...NO_SDK_IN_PRODUCTION,
                        ...NO_STATIC_PLUGIN_IMPORTS,
                    ],
                    paths: MIL_STD_WRAPPER_ONLY,
                },
            ],
        },
    },
    // App production code never imports `@opencop/plugin-sdk` (NO_SDK_IN_PRODUCTION);
    // its tests and the test harness may, through the block after this one.
    //
    // Specifier-based (`no-restricted-imports`) rather than the `boundaries`
    // element-types rule, for the same reason as the plugin `~` rule above: this is
    // a guarantee. `boundaries` resolves import specifiers to files and skips the
    // ones it cannot resolve; with no `import/resolver` configured it resolves no
    // `@opencop/*` or `~` alias at all — so for ALIASED specifiers (the dominant
    // form) its cross-package rules are advisory. It is weak, not inert: a RELATIVE
    // import with an explicit `.ts` extension does resolve and errors (extensionless
    // does not — the specifier rules cover that hole). Both facts are pinned by
    // `eslint.boundaries.test.ts`.
    {
        files: ["packages/app/src/**/*.{ts,tsx}"],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    // Restated, not inherited: this block re-declares the rule, so
                    // omitting these would silently un-ban the MIL-STD stack and the
                    // plugin/premium packages across the whole app.
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [
                        ...NO_STATIC_PLUGIN_IMPORTS,
                        ...NO_SDK_IN_PRODUCTION,
                        ...NO_APP_BARRELS,
                    ],
                },
            ],
        },
    },
    // App tests and the test harness may use the SDK's test kit; everything else applies.
    {
        files: ["packages/app/src/**/*.test.{ts,tsx}", "packages/app/src/test/**/*.{ts,tsx}"],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [...NO_STATIC_PLUGIN_IMPORTS, ...NO_APP_BARRELS],
                },
            ],
        },
    },
    // The static plugin table: the one production file that imports a plugin package.
    // Restated without the plugin ban; everything else still applies.
    {
        files: [STATIC_PLUGIN_TABLE],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [...NO_SDK_IN_PRODUCTION, ...NO_APP_BARRELS],
                },
            ],
        },
    },
    // The one test helper that installs a real plugin through the real manager, for app
    // tests whose feature needs what a plugin contributes (`installPlugins`, `withPlugins`). A test-only edge:
    // the app still may not import a plugin anywhere else, tests included.
    {
        files: [TEST_PLUGIN_INSTALLER],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [...NO_APP_BARRELS],
                },
            ],
        },
    },
    // The interaction runtime is infrastructure, not a leaf feature: it declares
    // what it requires of its host (`interaction-runtime/contract.ts`) and reaches
    // the map and the app only through the value `MapManager.bindInteraction`
    // hands it. It therefore imports no feature — features import it. The one
    // app-alias import it keeps is `~/keyboard.util`, which is not a feature.
    // Restated fully because `no-restricted-imports` replaces rather than
    // merges; the runtime's own tests are exempt (they build fakes from
    // features). Pinned by eslint.boundaries.test.ts.
    {
        files: ["packages/app/src/features/map/interaction-runtime/**/*.{ts,tsx}"],
        ignores: ["packages/app/src/features/map/interaction-runtime/**/*.test.{ts,tsx}"],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [
                        ...NO_STATIC_PLUGIN_IMPORTS,
                        ...NO_SDK_IN_PRODUCTION,
                        {
                            group: ["~/features/*", "~/features/**"],
                            message:
                                "the interaction runtime imports no feature — it declares what it needs in interaction-runtime/contract.ts and the host supplies it through MapManager.bindInteraction. Features import the runtime, never the reverse.",
                        },
                    ],
                },
            ],
        },
    },
    // The interaction session shape and its derivations must stay PURE: same
    // input, same answer, no store handle, no map, no React. These are host
    // policy and stay app-side permanently (the tool-facing contract lives in
    // `@opencop/tools`), so the rule is about testability and about keeping
    // arbitration out of the effect layer — not about promotion readiness.
    // Restated fully because `no-restricted-imports` replaces rather than
    // merges (see the note at the top of this file); pinned by
    // eslint.boundaries.test.ts.
    {
        files: [
            "packages/app/src/features/map/interaction-runtime/session/session.ts",
            "packages/app/src/features/map/interaction-runtime/**/derive/**/*.{ts,tsx}",
        ],
        rules: {
            "no-restricted-imports": [
                "error",
                {
                    paths: MIL_STD_WRAPPER_ONLY,
                    patterns: [
                        ...NO_STATIC_PLUGIN_IMPORTS,
                        ...NO_SDK_IN_PRODUCTION,
                        {
                            group: ["~/*", "~/**"],
                            message:
                                "the interaction session shape and derivations are pure — no app-alias imports (host policy, not effects).",
                        },
                        {
                            group: [
                                "@opencop/*",
                                "!@opencop/elements",
                                "!@opencop/tools",
                                "!@opencop/layers",
                                "!@opencop/utils",
                            ],
                            message:
                                "the interaction session shape and derivations may import only the leaf packages: @opencop/elements, @opencop/tools, @opencop/layers, @opencop/utils.",
                        },
                        {
                            group: ["react", "react-*", "zustand", "maplibre-gl", "@deck.gl/*"],
                            message:
                                "the interaction session shape and derivations are framework-free — no React, stores, or map libraries.",
                        },
                    ],
                },
            ],
        },
    },
    // Plugin/premium packages own their translations (plugin-local i18next
    // namespace bundles), so the host's literal-string rule doesn't police them.
    {
        files: ["packages/*-plugin/**/*.{ts,tsx}", "packages/premium-*/**/*.{ts,tsx}"],
        rules: {
            "i18next/no-literal-string": "off",
        },
    },
    // Theme browser: don't require i18n
    {
        files: ["**/themes/**", "**/ThemeView.tsx"],
        rules: {
            "i18next/no-literal-string": "off",
        },
    },
    // Context modules and route table are not HMR targets
    {
        files: ["**/*Context.{ts,tsx}", "**/app/src/router.tsx"],
        rules: {
            "react-refresh/only-export-components": "off",
        },
    },
    // UI library: whitelist known non-component exports (recipes, hooks, config)
    {
        files: ["packages/ui/src/**/*.{ts,tsx}"],
        rules: {
            "react-refresh/only-export-components": [
                "error",
                {
                    allowConstantExport: true,
                    customHOCs: ["chakra"],
                    allowExportNames: [
                        "toastRecipe",
                        "toaster",
                        "useColorMode",
                        "useColorModeValue",
                        "useDisplayPreferences",
                        "useFilterFields",
                        "DialogContainerContext",
                        "DrawerPanelHeadingContext",
                    ],
                },
            ],
        },
    },
    // Node / test tooling: console is the right output channel here. Build
    // config and Playwright e2e specs run outside the browser app, where the
    // js-logger app logger isn't available — so don't force js-logger on them.
    {
        files: ["**/*.config.ts", "e2e/**/*.{ts,tsx}", "scripts/**/*.{ts,js}"],
        rules: {
            "no-console": "off",
        },
    },
    // Component/hook test harness (`~/test`): infra module, not a fast-refresh
    // boundary, so it re-exports RTL utilities and factories. `_`-prefixed
    // params are the intentional-unused convention in the websocket stub, whose
    // signatures must match the real client.
    {
        files: ["packages/app/src/test/**/*.{ts,tsx}"],
        rules: {
            "react-refresh/only-export-components": "off",
            "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
        },
    },
    // The interaction session's writers stay inside commands.ts (see the
    // constant's note). Non-test app source here; app tests restate it below.
    {
        files: ["packages/app/src/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/src/features/map/interaction-runtime/session/commands.ts",
            "packages/app/src/test/interaction.ts",
        ],
        rules: {
            "no-restricted-syntax": ["error", INTERACTION_WRITERS_ARE_COMMANDS_ONLY],
        },
    },
    // The interaction runtime owns every input source (see the two constants'
    // notes). Feature code — outside the runtime, the manager that IS the
    // source, and the tests that build fakes — may not bind a keydown listener
    // or a contended pointer event. `no-restricted-syntax` replaces its options
    // wholesale, so the writers rule is restated here.
    {
        files: ["packages/app/src/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/src/features/map/interaction-runtime/**",
            "packages/app/src/features/map/MapManager.ts",
            "packages/app/src/test/**",
            "packages/app/src/**/*.test.{ts,tsx}",
        ],
        rules: {
            "no-restricted-syntax": [
                "error",
                INTERACTION_WRITERS_ARE_COMMANDS_ONLY,
                NO_KEYDOWN_LISTENER_OUTSIDE_ARBITRATION,
                ...NO_CONTENDED_POINTER_SUBSCRIPTION_OUTSIDE_RUNTIME,
            ],
        },
    },
    // Inside the runtime the pointer bindings are legitimate (the session's
    // draw setups and edit interpreter bind through the sources the runner
    // hands them), but a keydown listener still belongs to `arbitration/` alone.
    {
        files: ["packages/app/src/features/map/interaction-runtime/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/src/features/map/interaction-runtime/arbitration/**",
            "packages/app/src/features/map/interaction-runtime/session/commands.ts",
            "packages/app/src/features/map/interaction-runtime/**/*.test.{ts,tsx}",
        ],
        rules: {
            "no-restricted-syntax": [
                "error",
                INTERACTION_WRITERS_ARE_COMMANDS_ONLY,
                NO_KEYDOWN_LISTENER_OUTSIDE_ARBITRATION,
            ],
        },
    },
    // Component/hook test guard: app tests must render through the ~/test harness
    // and must not mock framework libraries or the API client. Mock the HTTP
    // boundary with MSW (server.use) instead. Legitimate system-boundary mocks
    // (websocket/native clients) and narrow internal mocks stay allowed.
    // Encoded as no-restricted-syntax (not no-restricted-imports) so it doesn't
    // replace the mil-sym no-restricted-imports rule on test files.
    {
        files: ["packages/app/**/*.test.{ts,tsx}"],
        rules: {
            "no-restricted-syntax": [
                "error",
                INTERACTION_WRITERS_ARE_COMMANDS_ONLY,
                {
                    // Named form: import { render } from "@testing-library/react".
                    selector:
                        'ImportDeclaration[source.value="@testing-library/react"] > ImportSpecifier[imported.name=/^(render|renderHook)$/]',
                    message:
                        "Import render/renderHook from ~/test to use the app-provider harness, not the raw @testing-library/react exports.",
                },
                {
                    // Namespace form: import * as RTL from "@testing-library/react" (RTL.render).
                    selector:
                        'ImportDeclaration[source.value="@testing-library/react"] > ImportNamespaceSpecifier',
                    message:
                        "Import test utilities from ~/test and use named exports, not a namespace import of the raw @testing-library/react library.",
                },
                {
                    // Matches vi.mock(...) and vi.doMock(...).
                    selector:
                        'CallExpression[callee.object.name="vi"][callee.property.name=/^(mock|doMock)$/] > Literal[value=/^@chakra-ui/]',
                    message:
                        "Don't mock @chakra-ui — render through the ~/test harness and query the real ARIA tree.",
                },
                {
                    selector:
                        'CallExpression[callee.object.name="vi"][callee.property.name=/^(mock|doMock)$/] > Literal[value="react-i18next"]',
                    message:
                        "Don't mock react-i18next — the shared harness supplies real translations; assert user-visible text.",
                },
                {
                    // Matches @opencop/api and any subpath (@opencop/api/...).
                    selector:
                        'CallExpression[callee.object.name="vi"][callee.property.name=/^(mock|doMock)$/] > Literal[value=/^@opencop\\/api/]',
                    message:
                        "Don't mock @opencop/api — register an MSW HTTP handler with server.use so tests exercise the real apiFetch path.",
                },
                RESTRICT_VIRTUALIZER_MOCK,
            ],
        },
    },
    // The virtualizer stub is installed for every Vitest project, so the ban applies to
    // leaf-package tests too — `packages/ui` is where VirtualList and ResourceTable live,
    // and both had their own copy of the mock before it was centralised. The rest of the
    // block above stays app-only (leaf tests can't mount the app-layer render harness).
    {
        files: ["packages/{ui,ingest,layers,elements,symbology}/**/*.test.{ts,tsx}"],
        rules: {
            "no-restricted-syntax": ["error", RESTRICT_VIRTUALIZER_MOCK],
        },
    },
    // E2E guards (see the constants' notes). map-helpers.ts owns the overflow-aware
    // toolbar / mapbar lookups, so it alone may name their buttons directly;
    // `no-restricted-syntax` replaces its options wholesale, so it restates the rest.
    {
        files: ["e2e/**/*.{ts,tsx}"],
        ignores: ["e2e/helpers/map-helpers.ts"],
        rules: {
            "no-restricted-syntax": [
                "error",
                E2E_ISVISIBLE_IGNORES_TIMEOUT,
                E2E_OVERFLOW_BAR_BUTTON_BY_NAME,
            ],
        },
    },
    {
        files: ["e2e/helpers/map-helpers.ts"],
        rules: {
            "no-restricted-syntax": ["error", E2E_ISVISIBLE_IGNORES_TIMEOUT],
        },
    },
    {
        // The host may not import a native plugin (see HOST_MAY_NOT_IMPORT_A_NATIVE_PLUGIN).
        files: ["packages/app/src/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/src/plugins/**",
            "packages/app/src/**/*.test.{ts,tsx}",
            COMPOSITION_ROOT,
        ],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                { patterns: HOST_MAY_NOT_IMPORT_A_NATIVE_PLUGIN },
            ],
        },
    },
    {
        // A native plugin may not import the host (see NATIVE_PLUGIN_MAY_NOT_IMPORT_THE_HOST).
        files: ["packages/app/src/plugins/**/*.{ts,tsx}"],
        // A native plugin's tests run it against the real host registries and the
        // app's test harness: a test-only edge, not a runtime one (the same
        // exemption the leaf packages' tests have).
        ignores: [NATIVE_PLUGIN_TESTS],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                { patterns: nativePluginPatterns(NATIVE_PLUGIN_EXACT_DEPTH) },
            ],
        },
    },
    ...Array.from({ length: NATIVE_PLUGIN_EXACT_DEPTH }, (_, depth) => ({
        files: [`packages/app/src/plugins/*/${"*/".repeat(depth)}*.{ts,tsx}`],
        ignores: [NATIVE_PLUGIN_TESTS],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                { patterns: nativePluginPatterns(depth) },
            ],
        },
    })),
    {
        // `@opencop/elements` is imported by nearly every package, so anything it
        // imports becomes reachable from all of them: only the lowest tier,
        // `@opencop/utils`, which imports none of ours.
        files: ["packages/elements/src/**/*.ts"],
        ignores: ["packages/elements/src/**/*.test.ts"],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            regex: "^@opencop/(?!utils$)",
                            message:
                                "@opencop/elements imports only @opencop/utils: everything depends on it.",
                        },
                    ],
                },
            ],
        },
    },
    {
        // Filter rules in `@opencop/ui` are the generic predicate language and its
        // builder. What a filter can be about — property paths, the SIDC — arrives as
        // fields from their owners, so this code names neither.
        files: ["packages/ui/src/data/FilterRules/**/*.{ts,tsx}"],
        ignores: ["packages/ui/src/data/FilterRules/**/*.test.{ts,tsx}"],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            regex: "^@opencop/symbology$",
                            message:
                                "Filter rules know no field: a SIDC field is contributed by its owner (registerFilterField).",
                        },
                    ],
                },
            ],
            "no-restricted-syntax": [
                "error",
                {
                    selector: "Literal[value=/^properties\\.opencop/]",
                    message: "Filter rules know no property path: a field names its own path.",
                },
            ],
        },
    },
    {
        // `@opencop/utils` is the lowest tier: every package may import it, so it
        // imports none of ours, holds no React and no i18n instance (translated
        // text comes in as a `t` argument). Third-party libraries are fine.
        files: ["packages/utils/src/**/*.ts"],
        ignores: ["packages/utils/src/**/*.test.ts"],
        rules: {
            "@typescript-eslint/no-restricted-imports": [
                "error",
                {
                    patterns: [
                        {
                            regex: "^@opencop/",
                            message:
                                "@opencop/utils imports none of our packages: every package depends on it.",
                        },
                        {
                            regex: "^react(-dom)?(/|$)",
                            message:
                                "@opencop/utils holds no React: put a hook or component in its owner's package.",
                        },
                        {
                            regex: "^(react-)?i18next(/|$)",
                            allowTypeImports: true,
                            message:
                                "@opencop/utils holds no i18n instance: take translated text as a `t` argument.",
                        },
                    ],
                },
            ],
        },
    },
    {
        files: ["packages/app/src/**/*.{ts,tsx}"],
        ignores: [
            "packages/app/src/plugins/**",
            "packages/app/src/test/**",
            "packages/app/src/**/*.test.{ts,tsx}",
        ],
        plugins: { opencop: opencopPlugin },
        rules: { "opencop/no-element-type-branch": "error" },
    },
    // this will turn off any formatting rules that conflict with prettier
    eslintConfigPrettier,
]);
