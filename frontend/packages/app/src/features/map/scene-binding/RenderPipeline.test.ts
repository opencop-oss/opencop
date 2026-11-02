// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import type { ObjectRef, Store, StoreEvent } from "@opencop/ingest";
import { path, type Primitive, type Scene } from "@opencop/scene";
import { RenderPipeline } from "./RenderPipeline";
import type { RenderSink } from "./RenderSink";
import { codec } from "./ref-codec";

type Thing = { readonly key: string; readonly drawn: boolean };

/** A store of things whose writes emit the matching event. */
function thingStore() {
    const held = new Map<string, Thing>();
    const listeners: Record<StoreEvent, Set<(things: Thing[]) => void>> = {
        add: new Set(),
        update: new Set(),
        remove: new Set(),
    };
    const store: Store<Thing> = {
        type: "things",
        get: (key) => held.get(key),
        getAll: () => held.values(),
        ref: (thing): ObjectRef => ({ type: "things", key: thing.key }),
        on: (event, listener) => void listeners[event].add(listener),
        off: (event, listener) => void listeners[event].delete(listener),
    };
    const emit = (event: StoreEvent, thing: Thing) => {
        if (event === "remove") held.delete(thing.key);
        else held.set(thing.key, thing);
        listeners[event].forEach((listener) => listener([thing]));
    };
    return { store, emit };
}

/** A scene that only records what it holds. */
function fakeScene() {
    const drawn = new Map<string, Primitive>();
    const remove = vi.fn((ids: string | Iterable<string>) => {
        for (const id of typeof ids === "string" ? [ids] : ids) drawn.delete(id);
    });
    const scene = {
        get: (id: string) => drawn.get(id),
        add: (primitives: Primitive | Iterable<Primitive>) => {
            for (const primitive of Symbol.iterator in primitives ? primitives : [primitives])
                drawn.set(primitive.id, primitive);
        },
        update: (primitive: Primitive) => void drawn.set(primitive.id, primitive),
        remove,
    } as unknown as Scene;
    return { scene, drawn, remove };
}

class ThingPipeline extends RenderPipeline<Store<Thing>> {
    protected map(thing: Thing): Primitive[] | undefined {
        if (!thing.drawn) return;
        return [
            path({
                id: "line",
                geometry: { positions: [0, 0, 0, 1, 1, 0] },
                style: { strokeColor: [0, 0, 0, 255], strokeWidth: 1 },
            }),
        ];
    }
}

function setup() {
    const { store, emit } = thingStore();
    const { scene, drawn, remove } = fakeScene();
    const pipeline = new ThingPipeline({ store, scene, sink: {} as RenderSink });
    pipeline.initialize();
    return { pipeline, emit, drawn, remove };
}

const idOf = (key: string, nanoid: string) =>
    codec.encode({ object: { type: "things", key }, nanoid });

describe("RenderPipeline", () => {
    it("draws an object when it arrives, under ids that encode its ref", () => {
        const { emit, drawn } = setup();

        emit("add", { key: "a", drawn: true });

        expect([...drawn.keys()]).toEqual([idOf("a", "line")]);
    });

    it("draws an object an update makes drawable, though nothing was drawn for it before", () => {
        const { emit, drawn } = setup();

        emit("add", { key: "a", drawn: false });
        expect(drawn.size).toBe(0);

        emit("update", { key: "a", drawn: true });
        expect([...drawn.keys()]).toEqual([idOf("a", "line")]);
    });

    it("removes what an update maps to nothing, and has nothing left to remove after", () => {
        const { emit, drawn, remove } = setup();
        emit("add", { key: "a", drawn: true });

        emit("update", { key: "a", drawn: false });
        expect(drawn.size).toBe(0);

        remove.mockClear();
        emit("remove", { key: "a", drawn: false });
        expect(remove).not.toHaveBeenCalled();
    });

    it("leaves what it drew when disposed, and stops listening", () => {
        const { pipeline, emit, drawn, remove } = setup();
        emit("add", { key: "a", drawn: true });
        emit("add", { key: "b", drawn: true });

        pipeline.dispose();
        expect(remove).not.toHaveBeenCalled();

        emit("add", { key: "c", drawn: true });
        expect([...drawn.keys()]).toEqual([idOf("a", "line"), idOf("b", "line")]);
    });
});
