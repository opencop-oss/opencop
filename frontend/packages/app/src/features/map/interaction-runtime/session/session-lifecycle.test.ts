// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Logger from "js-logger";
import { Line, type Element } from "@opencop/elements";
import {
    ELEMENT_STORE_ID,
    traceGrammar,
    createSequentialGrammar,
    type ToolDescriptor,
} from "@opencop/tools";
import { __resetToolRegistry, registerTool } from "~/features/contributions/tool-registry";
import { getInteractionState } from "./interaction.store";
import {
    activate,
    beginDraft,
    cancel,
    complete,
    draftCreated,
    exit,
    resetInteraction,
    restart,
    save,
    setLocked,
    setParams,
    startEdit,
} from "./commands";
import { attachHost } from "../host";
import { fakeHost } from "~/test/interaction-runtime-harness";
import {
    emitSessionLifecycle,
    subscribeSessionLifecycle,
    type SessionLifecycleEvent,
} from "./session-lifecycle";

/**
 * The session-lifecycle notification, pinned from both sides:
 *
 * 1. Mechanics — synchronous, in subscription order, safe against a listener
 *    that unsubscribes or throws mid-dispatch.
 * 2. Emission — which commands fire which events, in which order, with the
 *    store already holding the state each event describes (except
 *    `activated`, which is announced before its session is written and says
 *    so). No UI reaction is asserted here: those live with their subscribers.
 */

const lineFixture: ToolDescriptor = {
    id: "fixtureLine",
    label: "fixture",
    icon: () => null,
    produces: { store: ELEMENT_STORE_ID, type: "line" },
    draw: {
        grammar: createSequentialGrammar({
            min: 2,
            create: (cursor) => Line.create({ positions: cursor }),
        }),
    },
};

/** No element-store product: a measure-class tool whose product stays session-local. */
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

const traceFixture = (onComplete: ToolDescriptor["onComplete"]): ToolDescriptor => ({
    id: "fixtureTrace",
    label: "fixture-trace",
    icon: () => null,
    draw: { grammar: traceGrammar },
    onComplete,
});

/** Activate `id` and place one draft, returning the draft. */
function placeDraft(id: string): Element {
    expect(activate(id)).toEqual({ ok: true });
    const session = getInteractionState().session;
    const epoch = session.kind === "draw" ? session.epoch : -1;
    const element = Line.create({ positions: [0, 0] });
    draftCreated(element, epoch);
    beginDraft(element, epoch);
    setLocked(2, epoch);
    return element;
}

let events: SessionLifecycleEvent[];
let unsubscribe: () => void;
let unconfigure: () => void;

beforeEach(() => {
    __resetToolRegistry();
    resetInteraction();
    unconfigure = attachHost(fakeHost().host);
    events = [];
    unsubscribe = subscribeSessionLifecycle((e) => events.push(e));
});

afterEach(() => {
    unsubscribe();
    resetInteraction();
    unconfigure();
    __resetToolRegistry();
    vi.restoreAllMocks();
});

const types = () => events.map((e) => e.type);

describe("mechanics", () => {
    it("delivers synchronously, in subscription order, and stops after unsubscribe", () => {
        const seen: string[] = [];
        const offA = subscribeSessionLifecycle(() => seen.push("a"));
        const offB = subscribeSessionLifecycle(() => seen.push("b"));

        emitSessionLifecycle({ type: "activated", descriptorId: "x", custody: false });
        expect(seen).toEqual(["a", "b"]);

        offA();
        emitSessionLifecycle({ type: "activated", descriptorId: "x", custody: false });
        expect(seen).toEqual(["a", "b", "b"]);
        offB();
    });

    it("a listener that unsubscribes mid-dispatch does not skip the next one", () => {
        const seen: string[] = [];
        const offA = subscribeSessionLifecycle(() => {
            seen.push("a");
            offA();
        });
        const offB = subscribeSessionLifecycle(() => seen.push("b"));

        emitSessionLifecycle({ type: "activated", descriptorId: "x", custody: false });
        expect(seen).toEqual(["a", "b"]);
        offB();
    });

    it("contains a throwing listener: logged, the others still run, the emitter returns", () => {
        const error = vi.spyOn(Logger.get("interaction"), "error").mockImplementation(() => {});
        const seen: string[] = [];
        const offA = subscribeSessionLifecycle(() => {
            throw new Error("boom");
        });
        const offB = subscribeSessionLifecycle(() => seen.push("b"));

        expect(() =>
            emitSessionLifecycle({ type: "activated", descriptorId: "x", custody: true }),
        ).not.toThrow();
        expect(seen).toEqual(["b"]);
        expect(error).toHaveBeenCalledTimes(1);
        offA();
        offB();
    });
});

describe("what the commands emit", () => {
    it("a custody tool: activated → edit-started → ended(saved), each describing the store's state", () => {
        registerTool(lineFixture);
        const observed: string[] = [];
        const off = subscribeSessionLifecycle((e) => {
            observed.push(`${e.type}:${getInteractionState().session.kind}`);
        });

        const element = placeDraft("fixtureLine");
        expect(complete()).toEqual({ ok: true });
        expect(save(element)).toEqual({ ok: true });

        expect(events).toMatchObject([
            { type: "activated", descriptorId: "fixtureLine", custody: true },
            { type: "edit-started", session: { kind: "edit", custody: true, product: element } },
            {
                type: "ended",
                how: "saved",
                session: { kind: "edit", custody: true, product: element },
            },
        ]);
        // `activated` is the one event announced before its session is written.
        expect(observed).toEqual(["activated:idle", "edit-started:edit", "ended:idle"]);
        off();
    });

    it("a custody-less tool: the same events, with custody false; cancel ends it", () => {
        registerTool(productFixture);

        const element = placeDraft("fixtureProduct");
        expect(complete()).toEqual({ ok: true });
        expect(cancel()).toEqual({ ok: true });

        expect(events).toMatchObject([
            { type: "activated", descriptorId: "fixtureProduct", custody: false },
            { type: "edit-started", session: { custody: false, product: element } },
            { type: "ended", how: "cancelled", session: { kind: "edit", custody: false } },
        ]);
    });

    it("restart, setParams and a re-arm continue the session and emit nothing", () => {
        registerTool(productFixture);
        placeDraft("fixtureProduct");
        events.length = 0;

        expect(restart()).toEqual({ ok: true });
        expect(setParams({ shape: "polygon" })).toEqual({ ok: true });
        expect(events).toEqual([]);

        // A re-arm through onComplete is silent too.
        exit();
        events.length = 0;
        registerTool(traceFixture(() => "re-arm"));
        placeDraft("fixtureTrace");
        expect(complete()).toEqual({ ok: true });
        expect(types()).toEqual(["activated"]);
        expect(getInteractionState().session.kind).toBe("draw");
    });

    it("leaving through onComplete, Escape or a teardown is ended(exited) with the draw session", () => {
        registerTool(traceFixture(() => "exit"));
        placeDraft("fixtureTrace");
        expect(complete()).toEqual({ ok: true });
        expect(events.at(-1)).toMatchObject({
            type: "ended",
            how: "exited",
            session: { kind: "draw", descriptorId: "fixtureTrace" },
        });

        registerTool(lineFixture);
        placeDraft("fixtureLine");
        expect(exit()).toEqual({ ok: true });
        expect(events.at(-1)).toMatchObject({
            type: "ended",
            how: "exited",
            session: { kind: "draw", descriptorId: "fixtureLine", custody: true },
        });

        placeDraft("fixtureLine");
        resetInteraction();
        expect(events.at(-1)).toMatchObject({ type: "ended", how: "exited" });
    });

    it('save() over a custody-less product reports "saved", the same as a custody save', () => {
        // A custody-less product (watchbox) persists itself through its own API
        // and then ends the session with `save()`. The runtime commits nothing,
        // but the ending is a success and a subscriber must see it as one —
        // that is what keeps the watchbox panel open on the list it just
        // joined. The event is indistinguishable from a custody save on
        // purpose: subscribers react to "it was kept", not to where.
        registerTool(productFixture);
        placeDraft("fixtureProduct");
        complete();

        expect(save()).toEqual({ ok: true });
        expect(events.at(-1)).toMatchObject({ type: "ended", how: "saved" });
    });

    it("save(updated) over a custody-less product is refused: no held element to apply it to", () => {
        registerTool(productFixture);
        placeDraft("fixtureProduct");
        complete();

        expect(save({} as never).ok).toBe(false);
        // Refused means refused — the session is untouched, not ended.
        expect(getInteractionState().session.kind).toBe("edit");
    });

    it("activating over a waiting session ends it first: ended(exited) then activated", () => {
        registerTool(lineFixture);
        registerTool(productFixture);
        expect(activate("fixtureLine")).toEqual({ ok: true });
        events.length = 0;

        expect(activate("fixtureProduct")).toEqual({ ok: true });
        expect(events).toMatchObject([
            { type: "ended", how: "exited", session: { descriptorId: "fixtureLine" } },
            { type: "activated", descriptorId: "fixtureProduct" },
        ]);
    });

    it("a refused activation emits nothing", () => {
        registerTool(lineFixture);
        placeDraft("fixtureLine");
        events.length = 0;

        expect(activate("fixtureLine")).toMatchObject({ ok: false });
        expect(activate("unknown")).toMatchObject({ ok: false });
        expect(events).toEqual([]);
    });

    it("an immediate placement: activated, then edit-started, in one command", () => {
        registerTool(lineFixture);

        expect(activate("fixtureLine", { placeAt: [1, 2] })).toEqual({ ok: true });
        expect(events).toMatchObject([
            { type: "activated", descriptorId: "fixtureLine", custody: true },
            { type: "edit-started", session: { kind: "edit", custody: true, isNew: true } },
        ]);
    });

    it("startEdit over an existing element: edit-started under custody, ending any prior session", () => {
        registerTool(productFixture);
        placeDraft("fixtureProduct");
        complete();
        events.length = 0;

        const existing = Line.create({ positions: [0, 0, 1, 1] });
        expect(startEdit(existing, false)).toEqual({ ok: true });
        expect(events).toMatchObject([
            { type: "ended", how: "exited", session: { kind: "edit", custody: false } },
            { type: "edit-started", session: { kind: "edit", custody: true, isNew: false } },
        ]);
        const started = events[1];
        if (started.type !== "edit-started") throw new Error("expected edit-started");
        expect(started.session.product).not.toBe(existing);
        expect((started.session.product as Element).key).toBe(existing.key);
    });
});
