// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/** Custody: the StrictMode canary and the element-store rule. See `~/test/interaction-runtime-harness` for the fixtures. */
import { describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import { Line, PolylineGeometry } from "@opencop/elements";
import { ELEMENT_STORE_ID, createSequentialGrammar, type ToolDescriptor } from "@opencop/tools";
import { registerTool } from "~/features/contributions/tool-registry";
import { getInteractionState } from "./interaction.store";
import { activate, beginDraft, complete, draftCreated, isDraftPlaced, restart } from "./commands";
import { attachHost } from "../host";
import {
    fakeDeps,
    lineFixture,
    measureFixture,
    installInteractionRuntimeHarness,
    attachPointerArbitration,
} from "~/test/interaction-runtime-harness";

installInteractionRuntimeHarness();

describe("StrictMode custody canary", () => {
    it("a runner detach/attach cycle mid-draw preserves the in-flight element", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(lineFixture);

        expect(activate("fixtureLine")).toEqual({ ok: true });
        const session = getInteractionState().session;
        expect(session.kind).toBe("draw");
        const epoch = session.kind === "draw" ? session.epoch : -1;

        // First click placed the element (transaction began).
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);
        expect(deps.held()).toBe(element);

        // StrictMode: setup → cleanup → setup. Only listeners detach.
        const dispose1 = attachPointerArbitration(deps);
        dispose1();
        const dispose2 = attachPointerArbitration(deps);

        const after = getInteractionState().session;
        expect(after.kind).toBe("draw");
        if (after.kind === "draw") {
            expect(after.draft?.object).toBe(element);
        }
        expect(deps.held()).toBe(element);

        dispose2();
        unconfigure();
    });
});

describe("custody follows the element store in produces, not what completion does", () => {
    /** A shape-shaped descriptor (completes into an edit) that declares no element type. */
    const productFixture: ToolDescriptor = {
        id: "fixtureProduct",
        label: "fixture-product",
        icon: () => null,
        draw: {
            grammar: createSequentialGrammar({
                min: 2,
                create: (cursor) => Line.create({ positions: cursor }),
            }),
        },
    };

    it("a draw with no element-store product never touches the store or the pipeline, and completes without custody", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool(productFixture);

        expect(activate("fixtureProduct")).toEqual({ ok: true });
        const session = getInteractionState().session;
        expect(session).toMatchObject({ kind: "draw", custody: false });
        const epoch = session.kind === "draw" ? session.epoch : -1;

        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        expect(beginDraft(element, epoch)).toEqual({ ok: true });
        // Placed for the draw grammar's purposes, but nothing was stored or locked.
        expect(isDraftPlaced()).toBe(true);
        expect(deps.store.size).toBe(0);
        expect(deps.host.custody.isLocked()).toBe(false);

        expect(complete()).toEqual({ ok: true });
        expect(getInteractionState().session).toMatchObject({
            kind: "edit",
            custody: false,
            isNew: true,
        });
        expect(deps.mapManager.selectElement).not.toHaveBeenCalled();
        expect(deps.store.size).toBe(0);

        unconfigure();
    });

    it("a draw producing into the element store takes custody of its draft at placement, and dismisses the selection as it starts", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        registerTool({
            ...measureFixture,
            id: "fixtureTypedMeasure",
            produces: { store: ELEMENT_STORE_ID, type: "line" },
        });

        expect(activate("fixtureTypedMeasure", { params: { shape: "line" } })).toEqual({
            ok: true,
        });
        const armed = getInteractionState().session;
        expect(armed).toMatchObject({ kind: "draw", custody: true });
        // Its completion will select the product, so the old selection goes now.
        expect(deps.mapManager.handleEmptyClick).toHaveBeenCalledTimes(1);
        const epoch = armed.kind === "draw" ? armed.epoch : -1;

        const draft = Line.create({ positions: [0, 0] });
        draftCreated(draft, epoch);
        beginDraft(draft, epoch);
        expect(deps.held()).toBe(draft);
        expect(deps.host.custody.isLocked()).toBe(true);

        // Escape's restart releases it the same way a shape's would.
        expect(restart()).toEqual({ ok: true });
        expect(deps.store.size).toBe(0);
        expect(deps.held()).toBeUndefined();

        unconfigure();
    });

    it("refuses a non-element draft from a custody tool: the defect is reported and the session ends", () => {
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        const report = vi.spyOn(deps.host.environment, "reportToolDefect").mockImplementation(noop);
        registerTool(lineFixture);

        activate("fixtureLine");
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        // The element store admits only elements; a plain draft object is the tool's defect.
        const notAnElement = { key: "d", type: "line", geometry: new PolylineGeometry([0, 0], 2) };

        expect(draftCreated(notAnElement, epoch)).toMatchObject({ ok: false });

        expect(report).toHaveBeenCalledWith("fixtureLine", expect.any(Error));
        expect(getInteractionState().session.kind).toBe("idle");
        expect(deps.store.has("d")).toBe(false);
        unconfigure();
    });

    it("a session releases its custody even after its descriptor is deregistered", async () => {
        const { attachRegistryWatch } = await import("./registry-watch");
        const deps = fakeDeps();
        const unconfigure = attachHost(deps.host);
        const detachWatch = attachRegistryWatch(deps.host.environment);
        const disposeTool = registerTool(lineFixture);

        activate("fixtureLine");
        const session = getInteractionState().session;
        const epoch = session.kind === "draw" ? session.epoch : -1;
        const element = Line.create({ positions: [0, 0] });
        draftCreated(element, epoch);
        beginDraft(element, epoch);
        expect(deps.held()).toBe(element);

        // The plugin unloads mid-draw: the descriptor no longer resolves, so the
        // custody decision must already be on the session — nothing left to ask.
        disposeTool();

        expect(getInteractionState().session.kind).toBe("idle");
        expect(deps.store.has(element.key)).toBe(false);
        expect(deps.held()).toBeUndefined();

        detachWatch();
        unconfigure();
    });
});
