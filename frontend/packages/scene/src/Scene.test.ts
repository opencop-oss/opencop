// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it, vi } from "vitest";
import { LayerExtension, type Layer } from "@deck.gl/core";
import { ScatterplotLayer } from "@deck.gl/layers";
import type { Batch } from "./batches/Batch";
import type { Primitive, PrimitiveGeometry, PrimitiveStyle } from "./primitive";
import { Scene } from "./Scene";

class TestExtension extends LayerExtension {}

type FakeBatch = Batch & {
    contents: Map<string, Primitive>;
    accepts: boolean;
    layers: Layer[] | Layer;
    dirty: boolean;
};

function fakeBatch(geometry: PrimitiveGeometry["type"], style: PrimitiveStyle["type"]): FakeBatch {
    const batch: FakeBatch = {
        geometry,
        style,
        contents: new Map(),
        accepts: true,
        layers: [],
        dirty: false,
        add: vi.fn((primitive: Primitive) => {
            if (!batch.accepts) return false;
            batch.contents.set(primitive.id, primitive);
            return true;
        }),
        update: vi.fn((_, patched: Primitive) => {
            if (!batch.accepts) return false;
            batch.contents.set(patched.id, patched);
            return true;
        }),
        remove: vi.fn((id: string) => batch.accepts && batch.contents.delete(id)),
        flush: vi.fn(() => batch.dirty),
        render: vi.fn(() => batch.layers),
        destroy: vi.fn(),
    };
    return batch;
}

const polygon = (id: string): Primitive => ({
    id,
    geometry: { type: "polygon", positions: [0, 0, 0, 1, 0, 0, 1, 1, 0] },
    style: { type: "polygon", fillColor: [255, 0, 0, 255] },
    filter: [0.5],
});

const point = (id: string): Primitive => ({
    id,
    geometry: { type: "point", positions: [1, 2, 3] },
    style: {
        type: "point",
        fillColor: [0, 0, 0, 255],
        strokeColor: [0, 0, 0, 255],
        strokeWidth: 1,
        radius: 4,
    },
});

const icon = (id: string): Primitive => ({
    id,
    geometry: { type: "point", positions: [1, 2, 3] },
    style: { type: "icon", id: "i", source: "i.png", width: 8, height: 8, billboard: true },
});

function setup() {
    const batches = {
        polygon: fakeBatch("polygon", "polygon"),
        point: fakeBatch("point", "point"),
        icon: fakeBatch("point", "icon"),
    };
    const scene = new Scene(Object.values(batches));
    return { scene, ...batches };
}

describe("add", () => {
    it("routes each primitive to the batch registered for its geometry and style", () => {
        const { scene, polygon: polygons, point: points, icon: icons } = setup();
        scene.add([polygon("a"), point("b"), icon("c")]);

        expect([...polygons.contents.keys()]).toEqual(["a"]);
        expect([...points.contents.keys()]).toEqual(["b"]);
        expect([...icons.contents.keys()]).toEqual(["c"]);
        expect(scene.get("c")).toEqual(icon("c"));
    });

    it("ignores a primitive without a batch", () => {
        const { scene, polygon: polygons } = setup();
        const marchingAnts = { ...polygon("a"), style: { type: "marching-ants" } } as Primitive;
        scene.add(marchingAnts);

        expect(scene.get("a")).toBeUndefined();
        expect(polygons.add).not.toHaveBeenCalled();
    });

    it("does not keep a primitive its batch rejected", () => {
        const { scene, polygon: polygons } = setup();
        polygons.accepts = false;
        scene.add(polygon("a"));

        expect(polygons.add).toHaveBeenCalledOnce();
        expect(scene.get("a")).toBeUndefined();
    });

    it("routes through a batch registered after construction", () => {
        const scene = new Scene();
        const texts = fakeBatch("point", "text");
        scene.register(texts);
        const text = {
            ...icon("t"),
            style: { type: "text", text: "hi", size: 12, color: [0, 0, 0, 255] },
        };
        scene.add(text as Primitive);

        expect(texts.contents.has("t")).toBe(true);
    });
});

describe("update", () => {
    it("patches the primitive and hands the batch both the patch and the result", () => {
        const { scene, polygon: polygons } = setup();
        scene.add(polygon("a"));
        const partial = {
            id: "a",
            style: { type: "polygon", fillColor: [0, 0, 255, 255] },
        } as const;
        scene.update(partial);

        const patched = {
            ...polygon("a"),
            style: { type: "polygon", fillColor: [0, 0, 255, 255] },
        };
        expect(polygons.update).toHaveBeenCalledWith(partial, patched);
        expect(scene.get("a")).toEqual(patched);
    });

    it("keeps the filter and outline unless the patch sets them", () => {
        const { scene } = setup();
        scene.add({ ...polygon("a"), outline: true });

        scene.update({ id: "a", geometry: { type: "polygon", positions: [0, 0, 0] } });
        expect(scene.get("a")).toMatchObject({ filter: [0.5], outline: true });

        scene.update({ id: "a", filter: [0], outline: false });
        expect(scene.get("a")).toMatchObject({ filter: [0], outline: false });
        expect(scene.get("a")?.geometry).toEqual({ type: "polygon", positions: [0, 0, 0] });
    });

    it("updates an iterable of patches", () => {
        const { scene } = setup();
        scene.add([polygon("a"), polygon("b")]);
        scene.update([
            { ...polygon("a"), filter: [0.1] },
            { ...polygon("b"), filter: [0.2] },
        ]);

        expect(scene.get("a")?.filter).toEqual([0.1]);
        expect(scene.get("b")?.filter).toEqual([0.2]);
    });

    it("ignores an unknown id", () => {
        const { scene, polygon: polygons } = setup();
        scene.update({ id: "missing", filter: [0] });

        expect(polygons.update).not.toHaveBeenCalled();
        expect(scene.get("missing")).toBeUndefined();
    });

    it("rejects a patch that changes the primitive's batch", () => {
        const { scene, point: points, icon: icons } = setup();
        scene.add(point("a"));
        scene.update({ id: "a", style: { type: "icon", id: "i" } });

        expect(scene.get("a")).toEqual(point("a"));
        expect(points.contents.has("a")).toBe(true);
        expect(icons.contents.has("a")).toBe(false);
        expect(points.update).not.toHaveBeenCalled();
        expect(icons.update).not.toHaveBeenCalled();
    });

    it("keeps the previous primitive when its batch rejects the update", () => {
        const { scene, polygon: polygons } = setup();
        scene.add(polygon("a"));
        polygons.accepts = false;
        scene.update({ id: "a", filter: [0] });

        expect(scene.get("a")).toEqual(polygon("a"));
    });
});

describe("remove", () => {
    it("removes primitives from the scene and their batches", () => {
        const { scene, polygon: polygons, icon: icons } = setup();
        scene.add([polygon("a"), icon("b"), icon("c")]);
        scene.remove("a");
        scene.remove(["b"]);

        expect(polygons.remove).toHaveBeenCalledWith("a");
        expect(icons.remove).toHaveBeenCalledWith("b");
        expect(scene.get("a")).toBeUndefined();
        expect(scene.get("b")).toBeUndefined();
        expect([...icons.contents.keys()]).toEqual(["c"]);
    });

    it("removes a multi-character id as a single id", () => {
        const { scene, polygon: polygons } = setup();
        scene.add(polygon("abc"));
        scene.remove("abc");

        expect(polygons.remove).toHaveBeenCalledExactlyOnceWith("abc");
        expect(scene.get("abc")).toBeUndefined();
    });

    it("ignores an unknown id", () => {
        const { scene, polygon: polygons } = setup();
        scene.remove("missing");

        expect(polygons.remove).not.toHaveBeenCalled();
    });

    it("keeps a primitive its batch failed to remove", () => {
        const { scene, polygon: polygons } = setup();
        scene.add(polygon("a"));
        polygons.accepts = false;
        scene.remove("a");

        expect(scene.get("a")).toEqual(polygon("a"));
    });
});

describe("batches", () => {
    it("renders the next frame after a batch is registered", () => {
        const { scene } = setup();
        scene.flush();

        scene.register(fakeBatch("point", "text"));

        expect(scene.flush()).toBe(true);
        expect(scene.flush()).toBe(false);
    });

    it("renders the next frame after a batch is unregistered, and stops routing to and rendering it", () => {
        const { scene, polygon: polygons } = setup();
        scene.flush();

        scene.unregister(polygons);
        scene.add(polygon("a"));
        scene.render();

        expect(scene.flush()).toBe(true);
        expect(polygons.add).not.toHaveBeenCalled();
        expect(polygons.render).not.toHaveBeenCalled();
    });

    it("does not render for unregistering a batch it does not hold", () => {
        const { scene, polygon: polygons } = setup();
        scene.flush();

        scene.unregister(fakeBatch("point", "text"));
        scene.unregister(fakeBatch("polygon", "polygon"));
        scene.add(polygon("a"));

        expect(scene.flush()).toBe(false);
        expect(polygons.contents.has("a")).toBe(true);
    });

    it("reconciles to new instances of the batches it holds", () => {
        const { scene, polygon: old } = setup();
        const next = fakeBatch("polygon", "polygon");

        scene.reconcile([next]);
        scene.add(polygon("a"));
        scene.render();

        expect(next.contents.has("a")).toBe(true);
        expect(next.render).toHaveBeenCalledOnce();
        expect(old.render).not.toHaveBeenCalled();
    });

    it("replaces the batch registered for the same geometry and style", () => {
        const { scene, polygon: first } = setup();
        scene.flush();
        const second = fakeBatch("polygon", "polygon");

        scene.register(second);
        scene.add(polygon("a"));

        expect(scene.flush()).toBe(true);
        expect(first.add).not.toHaveBeenCalled();
        expect(second.contents.has("a")).toBe(true);
    });

    it("reconciles to a set of batches, rendering the next frame only when the set changed", () => {
        const { scene, polygon: polygons, point: points, icon: icons } = setup();
        scene.flush();

        scene.reconcile([polygons, points, icons]);
        expect(scene.flush()).toBe(false);

        const texts = fakeBatch("point", "text");
        scene.reconcile([polygons, texts]);
        scene.render();

        expect(scene.flush()).toBe(true);
        expect(polygons.render).toHaveBeenCalledOnce();
        expect(texts.render).toHaveBeenCalledOnce();
        expect(points.render).not.toHaveBeenCalled();
        expect(icons.render).not.toHaveBeenCalled();
    });
});

describe("flush", () => {
    it("reports true once after its batches change, then false while no batch writes", () => {
        const { scene } = setup();

        expect(scene.flush()).toBe(true);
        expect(scene.flush()).toBe(false);
    });

    it("reports true when any batch wrote, and flushes every batch", () => {
        const { scene, polygon: polygons, point: points, icon: icons } = setup();
        scene.flush();
        polygons.dirty = true;

        expect(scene.flush()).toBe(true);
        for (const batch of [polygons, points, icons]) expect(batch.flush).toHaveBeenCalledTimes(2);
    });
});

describe("render", () => {
    it("flattens every batch's layers and passes each batch the props", () => {
        const { scene, polygon: polygons, icon: icons } = setup();
        polygons.layers = new ScatterplotLayer({ id: "polygons" });
        icons.layers = [
            new ScatterplotLayer({ id: "icons" }),
            new ScatterplotLayer({ id: "labels" }),
        ];
        const props = { icon: { sizeScale: 2 } };

        expect(scene.render(props).map((layer) => layer.id)).toEqual([
            "polygons",
            "icons",
            "labels",
        ]);
        expect(icons.render).toHaveBeenCalledWith(props);
        expect(polygons.render).toHaveBeenCalledWith(props);
    });

    it("passes empty props by default", () => {
        const { scene, point: points } = setup();
        scene.render();

        expect(points.render).toHaveBeenCalledWith({});
    });

    it("returns clones that keep the batch layer's props", () => {
        const { scene, point: points } = setup();
        const extension = new TestExtension();
        const original = new ScatterplotLayer({
            id: "points",
            radiusScale: 3,
            extensions: [extension],
        });
        points.layers = original;
        const [layer] = scene.render() as ScatterplotLayer[];

        expect(layer).not.toBe(original);
        expect(original.id).toBe("points");
        expect(layer).toBeInstanceOf(ScatterplotLayer);
        expect(layer.props.radiusScale).toBe(3);
        expect(layer.props.extensions).toEqual([extension]);
    });

    it("gives the same extension instances a stable layer id", () => {
        const { scene, point: points } = setup();
        const extensions = [new TestExtension(), new TestExtension()];
        points.layers = new ScatterplotLayer({ id: "points", extensions });
        const [first] = scene.render();
        points.layers = new ScatterplotLayer({ id: "points", extensions: [...extensions] });
        const [second] = scene.render();
        const [other] = new Scene([points]).render();

        expect(first.id).toMatch(/^points-\d+-\d+$/);
        expect(second.id).toBe(first.id);
        expect(other.id).toBe(first.id);
    });

    it("changes the layer id when its extension set changes", () => {
        const { scene, point: points } = setup();
        const a = new TestExtension();
        const render = (extensions: LayerExtension[]) => {
            points.layers = new ScatterplotLayer({ id: "points", extensions });
            return scene.render()[0].id;
        };
        const ids = [
            render([]),
            render([a]),
            render([a, new TestExtension()]),
            render([new TestExtension()]),
        ];

        expect(ids[0]).toBe("points");
        expect(new Set(ids).size).toBe(ids.length);
    });
});
