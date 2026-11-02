// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { Line, type Element } from "@opencop/elements";
import { _useIngestContext, type ElementStore } from "@opencop/ingest";
import { path, type Primitive, type Scene } from "@opencop/scene";
import type { ElementMapper } from "@opencop/contributions";
import { act, render } from "~/test";
import { nativeOwner } from "~/features/contributions/contribution-guard";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { codec } from "./ref-codec";
import { SceneBindingProvider, useInitializeElementPipeline } from "./SceneBindingContext";

const { drawn, scene } = vi.hoisted(() => {
    const drawn = new Map<string, Primitive>();
    /** A scene that only records what it holds. */
    const scene = {
        get: (id: string) => drawn.get(id),
        add: (primitives: Primitive | Iterable<Primitive>) => {
            for (const primitive of Symbol.iterator in primitives ? primitives : [primitives])
                drawn.set(primitive.id, primitive);
        },
        update: (primitive: Primitive) => void drawn.set(primitive.id, primitive),
        remove: (ids: string | Iterable<string>) => {
            for (const id of typeof ids === "string" ? [ids] : ids) drawn.delete(id);
        },
    } as unknown as Scene;
    return { drawn, scene };
});

vi.mock("@opencop/scene", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/scene")>()),
    useScene: () => ({ scene }),
}));

/** A mapper drawing one path for each element of `type`. */
function stubMapper(type: string) {
    const map = vi.fn<(element: Element) => Primitive[]>(() => [
        path({
            id: "path",
            geometry: { positions: [0, 0, 0, 1, 1, 0] },
            style: { strokeColor: [0, 0, 0, 255], strokeWidth: 1 },
        }),
    ]);
    return { type, map } satisfies ElementMapper;
}

const line = (objectId: string) =>
    Line.create({ positions: [0, 0, 1, 1], meta: { objectId, source: "feed" } });

const cleanups: (() => void)[] = [];
const register = (mapper: ElementMapper) => {
    const remove = registerElementMapper(nativeOwner("opencop.test"), mapper);
    cleanups.push(remove);
    return remove;
};

/** The keys of the elements with something drawn. */
const keys = () => new Set([...drawn.keys()].map((id) => codec.decode(id)!.object.key));

const flush = () => act(() => new Promise<void>((resolve) => queueMicrotask(resolve)));

function Pipeline({ onStore }: { onStore: (store: ElementStore) => void }) {
    onStore(_useIngestContext().store);
    useInitializeElementPipeline();
    return null;
}

/** Mounts the element pipeline over the harness's store, holding `elements`. */
function mount(...elements: Element[]) {
    let store!: ElementStore;
    const result = render(
        <SceneBindingProvider>
            <Pipeline
                onStore={(s) => {
                    if (store) return;
                    store = s;
                    store.add(elements);
                }}
            />
        </SceneBindingProvider>,
    );
    return { ...result, store };
}

afterEach(() => {
    cleanups.splice(0).forEach((cleanup) => cleanup());
    drawn.clear();
});

describe("useInitializeElementPipeline", () => {
    it("draws with the mappers registered before it mounts", () => {
        const a = line("a");
        register(stubMapper("line"));

        mount(a);

        expect(keys()).toEqual(new Set([a.key]));
    });

    it("draws with a mapper registered after it mounts", async () => {
        const a = line("a");
        mount(a);
        expect(keys()).toEqual(new Set());

        register(stubMapper("line"));
        await flush();

        expect(keys()).toEqual(new Set([a.key]));
    });

    it("erases what a mapper drew when the registry drops it", async () => {
        const a = line("a");
        const remove = register(stubMapper("line"));
        mount(a);

        remove();
        await flush();

        expect(keys()).toEqual(new Set());
    });

    it("draws with a mapper re-registered for a type after the first was dropped", async () => {
        const a = line("a");
        const first = stubMapper("line");
        register(first)();
        mount(a);

        const second = stubMapper("line");
        register(second);
        await flush();

        expect(first.map).not.toHaveBeenCalled();
        expect(second.map).toHaveBeenCalledWith(a, undefined);
    });

    it("stops following the registry once unmounted", async () => {
        const { unmount } = mount(line("a"));
        unmount();

        const late = stubMapper("line");
        register(late);
        await flush();

        expect(late.map).not.toHaveBeenCalled();
    });
});
