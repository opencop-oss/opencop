// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, userEvent } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { PluginRecord } from "@opencop/contributions/host";
import type { EnablementsState } from "./plugin-loader";
import { PluginsPanel } from "./PluginsPanel";

/**
 * `/admin/plugins` must distinguish "this install has no plugins" from "we could
 * not find out which plugins to load". Both leave the table empty, and the second
 * means military point symbols are gone for the session — so the empty
 * table alone sends support toward the map instead of the loader.
 */

const retryPluginLoad = vi.fn(async () => ({ status: "loaded", pluginCount: 1 }) as const);
vi.mock("./plugin-runtime", () => ({
    retryPluginLoad: () => retryPluginLoad(),
}));

// The panel's job is rendering the loader's outcome, not producing it — so the
// two reactive reads it makes are the boundary these drive. The outcomes
// themselves are covered in `plugin-loader.test.ts`.
let state: EnablementsState = { status: "pending" };
let records: readonly PluginRecord[] = [];
vi.mock("./plugin-records", async (importOriginal) => ({
    ...(await importOriginal<typeof import("./plugin-records")>()),
    usePluginRecords: () => records,
    usePluginEnablementsState: () => state,
}));

function publish(next: EnablementsState) {
    state = next;
}

beforeEach(() => {
    vi.clearAllMocks();
    state = { status: "pending" };
    records = [];
});

describe("PluginsPanel — a refused plugin", () => {
    it("lists every reason it was refused, one per line", () => {
        records = [
            {
                manifest: { id: "acme.broken", name: "Broken", version: "1.0.0", apiVersion: 1 },
                status: "failed",
                error: 'manifest.name missing; tools: tool "paint" needs content or draw',
                problems: [
                    {
                        rule: "manifest.name-missing",
                        kind: "manifest",
                        severity: "error",
                        message: "manifest.name missing",
                    },
                    {
                        rule: "tools.content-or-draw-missing",
                        kind: "tools",
                        severity: "error",
                        message: 'tool "paint" needs content or draw',
                        id: "paint",
                    },
                ],
            },
        ];
        render(<PluginsPanel />);

        expect(
            screen.getByText(
                (_, element) =>
                    element?.tagName === "P" &&
                    element.textContent ===
                        'manifest.name missing\ntools: tool "paint" needs content or draw',
            ),
        ).toBeInTheDocument();
    });
});

describe("PluginsPanel — enablements state", () => {
    it.each(["pending", "absent"] as const)("shows no alert when the state is %s", (status) => {
        publish({ status } as EnablementsState);
        render(<PluginsPanel />);
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("shows no alert on a successful load", () => {
        publish({ status: "loaded", pluginCount: 1 });
        render(<PluginsPanel />);
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it.each([
        ["network", "could not be reached"],
        ["http", "returned an error"],
        ["schema", "not valid"],
    ] as const)("reports a %s failure with a reason the operator can act on", (reason, phrase) => {
        publish({ status: "failed", reason, detail: "503 Service Unavailable" });
        render(<PluginsPanel />);

        const alert = screen.getByRole("alert");
        expect(alert).toHaveTextContent(/could not be loaded/i);
        expect(alert).toHaveTextContent(new RegExp(phrase, "i"));
    });

    it("interpolates the failure detail so the operator sees the actual error", () => {
        publish({ status: "failed", reason: "http", detail: "503 Service Unavailable" });
        render(<PluginsPanel />);
        expect(screen.getByRole("alert")).toHaveTextContent("503 Service Unavailable");
    });

    it("offers a retry that re-runs the loader", async () => {
        publish({ status: "failed", reason: "network", detail: "Failed to fetch" });
        render(<PluginsPanel />);

        await userEvent.click(screen.getByRole("button", { name: "Retry" }));

        expect(retryPluginLoad).toHaveBeenCalledTimes(1);
    });

    it("says it is retrying rather than raising an alert the operator must act on", () => {
        publish({ status: "retrying", attempt: 1, detail: "Failed to fetch" });
        render(<PluginsPanel />);

        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(screen.getByText(/retrying/i)).toBeInTheDocument();
    });
});
