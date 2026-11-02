// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PluginManager, type PluginHostServices } from "@opencop/contributions/host";
import { DEFAULT_RETRY_POLICY, loadEnablements, runPluginLoader } from "./plugin-loader";
import { enablementsStateRegistry } from "./plugin-records";
import { noop } from "lodash";

// Replace the real static-plugins map with two well-formed fixtures — one meant
// to be flag-gated, one that always loads — so the test exercises the loader's
// gating without pulling in real plugin bundles.
vi.mock("./static-plugins", async () => {
    const { definePlugin, SDK_API_VERSION } = await import("@opencop/contributions/plugin");
    const mk = (id: string) =>
        definePlugin({
            manifest: { id, name: id, version: "1.0.0", apiVersion: SDK_API_VERSION },
            install: (ctx) => ctx.registerElementMapper({ type: id, map: () => [] }),
        });
    return {
        STATIC_PLUGINS: {
            "acme.flagged-plugin": mk("acme.flagged-plugin"),
            "acme.always-plugin": mk("acme.always-plugin"),
        },
    };
});

// Mapper registrations land in an app-owned registry, so the loader test counts
// them through this sink.
let registeredMappers = 0;

const services: PluginHostServices = {
    reservedOwnerIds: [],
    // Every key resolves: these tests drive the loader, not the host's translations.
    translationExists: () => true,
    registerTool: () => noop,
    i18n: { addResourceBundle: noop, removeResourceBundle: noop },
    registerDetailsHeader: () => noop,
    registerFilterField: () => noop,
    registerElementMapper: () => {
        registeredMappers += 1;
        return () => {
            registeredMappers -= 1;
        };
    },
    createContext: () => ({
        storage: { get: () => undefined, set: () => undefined, remove: () => undefined },
    }),
};

function stubEnablements(doc: unknown) {
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => ({ ok: true, status: 200, json: async () => doc }) as Response),
    );
}

/** A response with the given status and no usable body. */
function stubStatus(status: number, statusText = "") {
    vi.stubGlobal(
        "fetch",
        vi.fn(
            async () =>
                ({
                    ok: status >= 200 && status < 300,
                    status,
                    statusText,
                    json: async () => {
                        throw new Error("no body");
                    },
                }) as unknown as Response,
        ),
    );
}

/** A 200 whose body is not JSON. */
function stubMalformedJson() {
    vi.stubGlobal(
        "fetch",
        vi.fn(
            async () =>
                ({
                    ok: true,
                    status: 200,
                    json: async () => {
                        throw new SyntaxError("Unexpected token < in JSON");
                    },
                }) as unknown as Response,
        ),
    );
}

function stubNetworkError() {
    vi.stubGlobal(
        "fetch",
        vi.fn(async () => {
            throw new TypeError("Failed to fetch");
        }),
    );
}

let manager: PluginManager;

beforeEach(() => {
    registeredMappers = 0;
    enablementsStateRegistry.replace([]);
    manager = new PluginManager(services);
});

afterEach(() => {
    for (const record of manager.list()) manager.unload(record.manifest.id);
    vi.unstubAllGlobals();
    expect(registeredMappers).toBe(0);
});

const loadedIds = () =>
    manager
        .list()
        .map((r) => r.manifest.id)
        .sort();

describe("runPluginLoader flag gating", () => {
    it("loads an entry with no flag regardless of flag state", async () => {
        stubEnablements({ plugins: [{ id: "acme.always-plugin" }] });
        await runPluginLoader(manager, () => false);
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });

    it("skips a flagged entry when its flag is disabled, still loading unflagged ones", async () => {
        stubEnablements({
            plugins: [
                { id: "acme.flagged-plugin", flag: "test-flag" },
                { id: "acme.always-plugin" },
            ],
        });
        await runPluginLoader(manager, (flag) => flag !== "test-flag");
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });

    it("loads a flagged entry when its flag is enabled", async () => {
        stubEnablements({
            plugins: [
                { id: "acme.flagged-plugin", flag: "test-flag" },
                { id: "acme.always-plugin" },
            ],
        });
        await runPluginLoader(manager, (flag) => flag === "test-flag");
        expect(loadedIds()).toEqual(["acme.always-plugin", "acme.flagged-plugin"]);
    });

    it("fails closed: a flagged entry is skipped when no predicate is supplied", async () => {
        stubEnablements({
            plugins: [
                { id: "acme.flagged-plugin", flag: "test-flag" },
                { id: "acme.always-plugin" },
            ],
        });
        await runPluginLoader(manager);
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });
});

describe("runPluginLoader reconciliation", () => {
    const doc = {
        plugins: [{ id: "acme.flagged-plugin", flag: "test-flag" }, { id: "acme.always-plugin" }],
    };

    it("unloads a plugin that becomes ineligible, keeping the unrelated one loaded", async () => {
        stubEnablements(doc);
        // Flag on: both load.
        await runPluginLoader(manager, (flag) => flag === "test-flag");
        expect(loadedIds()).toEqual(["acme.always-plugin", "acme.flagged-plugin"]);

        // Flag off: only the flagged plugin unloads; the unflagged one is untouched.
        await runPluginLoader(manager, () => false);
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });

    it("does not reload an already-active, still-eligible plugin", async () => {
        stubEnablements(doc);
        await runPluginLoader(manager, () => false);
        expect(loadedIds()).toEqual(["acme.always-plugin"]);

        // Re-running with the same (ineligible) flag state must leave the active
        // unflagged plugin exactly as-is — reload() would tear down and rebuild it.
        const loadSpy = vi.spyOn(manager, "load");
        await runPluginLoader(manager, () => false);
        expect(loadSpy).not.toHaveBeenCalled();
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
        loadSpy.mockRestore();
    });
});

describe("enablements acquisition — absence vs failure", () => {
    // Same attempt count as production, no real sleeping — the pacing is a
    // policy, not a hidden constant, so a test can set it without fake timers.
    const FAST = { ...DEFAULT_RETRY_POLICY, baseDelayMs: 0 };
    const run = () => runPluginLoader(manager, undefined, undefined, FAST);

    it("treats a 404 as the documented 'no plugins' case, silently", async () => {
        stubStatus(404, "Not Found");
        const state = await run();
        expect(state).toEqual({ status: "absent" });
        expect(loadedIds()).toEqual([]);
    });

    it.each([
        ["a network error", stubNetworkError, "network"],
        ["a 500", () => stubStatus(500, "Internal Server Error"), "http"],
        ["a 403", () => stubStatus(403, "Forbidden"), "http"],
        ["malformed JSON", stubMalformedJson, "schema"],
    ])("reports %s as a failure, not as absence", async (_label, stub, reason) => {
        stub();
        const state = await run();
        expect(state).toMatchObject({ status: "failed", reason });
        // The admin surface must be able to tell this apart from an empty install.
        expect(enablementsStateRegistry.getAll()[0]).toMatchObject({ status: "failed", reason });
    });

    it.each([
        ["`plugins` missing", {}],
        ["`plugins` not an array", { plugins: { "acme.always-plugin": true } }],
        ["an entry without an id", { plugins: [{ flag: "x" }] }],
        ["a non-object entry", { plugins: ["acme.always-plugin"] }],
        ["a null document", null],
    ])("rejects %s as a schema failure rather than throwing", async (_label, doc) => {
        stubEnablements(doc);
        // The pre-fix loader cast the parsed JSON and iterated it, so a non-array
        // `plugins` threw out of an unawaited promise instead of being reported.
        const state = await run();
        expect(state).toMatchObject({ status: "failed", reason: "schema" });
        expect(loadedIds()).toEqual([]);
    });

    it("leaves already-loaded plugins running when a later fetch fails", async () => {
        stubEnablements({ plugins: [{ id: "acme.always-plugin" }] });
        await run();
        expect(loadedIds()).toEqual(["acme.always-plugin"]);

        // Reconciling against a document we could not read would unload everything.
        stubNetworkError();
        await run();
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });

    it("recovers on retry after a transient failure", async () => {
        stubNetworkError();
        expect(await run()).toMatchObject({ status: "failed" });
        expect(loadedIds()).toEqual([]);

        stubEnablements({ plugins: [{ id: "acme.always-plugin" }] });
        expect(await run()).toMatchObject({ status: "loaded" });
        expect(loadedIds()).toEqual(["acme.always-plugin"]);
    });

    it("retries a transient failure before giving up, but not a schema failure", async () => {
        stubNetworkError();
        await run();
        // 3 attempts for something that might succeed on the next one.
        expect(vi.mocked(fetch)).toHaveBeenCalledTimes(FAST.maxAttempts);

        vi.unstubAllGlobals();
        stubMalformedJson();
        await run();
        // Re-fetching a malformed file yields the same malformed file.
        expect(vi.mocked(fetch)).toHaveBeenCalledTimes(1);
    });
});

describe("loadEnablements", () => {
    it("distinguishes all four outcomes", async () => {
        stubEnablements({ plugins: [] });
        expect(await loadEnablements()).toEqual({ status: "loaded", document: { plugins: [] } });

        stubStatus(404);
        expect(await loadEnablements()).toEqual({ status: "absent" });

        stubStatus(503, "Service Unavailable");
        expect(await loadEnablements()).toMatchObject({ status: "failed", reason: "http" });

        stubNetworkError();
        expect(await loadEnablements()).toMatchObject({ status: "failed", reason: "network" });
    });
});
