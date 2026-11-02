// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient } from "@tanstack/react-query";
import { Line, Polygon, type Element } from "@opencop/elements";
import { ElementStore } from "@opencop/ingest";
import { label, path, type PartialPrimitive, type Primitive, type Scene } from "@opencop/scene";
import { AllExpression } from "~/features/map-filter/compiled-filter.model";
import { useMapFilterStore } from "~/features/map-filter/map-filter.store";
import { useMapStore } from "../../core/map.store";
import { defaultMapSettings, useMapSettingsStore } from "../../map-settings.store";
import { RenderSink, type Selector } from "../RenderSink";
import { codec } from "../ref-codec";
import type { ElementMapper } from "@opencop/contributions";
import { ElementPipeline } from "./ElementPipeline";
import { LABEL } from "./element-pipeline.util";

/** A scene that records what it holds and each update it is sent. */
function fakeScene() {
    const drawn = new Map<string, Primitive>();
    const updates: PartialPrimitive[] = [];
    const scene = {
        get: (id: string) => drawn.get(id),
        add: (primitives: Primitive | Iterable<Primitive>) => {
            for (const primitive of Symbol.iterator in primitives ? primitives : [primitives])
                drawn.set(primitive.id, { ...primitive });
        },
        remove: (ids: string | Iterable<string>) => {
            for (const id of typeof ids === "string" ? [ids] : ids) drawn.delete(id);
        },
        update: (partial: PartialPrimitive) => {
            updates.push(partial);
            const held = drawn.get(partial.id);
            if (held) drawn.set(partial.id, { ...held, ...partial } as Primitive);
        },
    } as unknown as Scene;
    return { scene, drawn, updates };
}

/** A mapper drawing a path and a label for each element of `type`. */
function stubMapper<S = undefined>(type: string, selector?: Selector<S>) {
    const map = vi.fn<(element: Element, state: S) => Primitive[]>((element) => [
        path({
            id: "path",
            geometry: { positions: [0, 0, 0, 1, 1, 0] },
            style: { strokeColor: [0, 0, 0, 255], strokeWidth: 1 },
        }),
        label({
            id: "label",
            geometry: { positions: [0, 0, 0] },
            style: { text: element.getDisplayName() },
        }),
    ]);
    return { type, selector, map } satisfies ElementMapper<Element, S>;
}

const sizeSelector: Selector<number> = { select: (state) => state.icons.size };

const line = (objectId: string, displayName = objectId) =>
    Line.create({ positions: [0, 0, 1, 1], meta: { objectId, source: "feed", displayName } });
const polygon = (objectId: string) =>
    Polygon.create({ positions: [0, 0, 1, 0, 1, 1], meta: { objectId, source: "feed" } });

const idOf = (element: Element, nanoid: string) =>
    codec.encode({ object: { type: "element", key: element.key }, nanoid });

const flush = () => new Promise<void>((resolve) => queueMicrotask(resolve));

const stores: ElementStore[] = [];
const pipelines: ElementPipeline[] = [];

function setup(...elements: Element[]) {
    const store = new ElementStore();
    store.add(elements);
    const { scene, drawn, updates } = fakeScene();
    const pipeline = new ElementPipeline({ store, scene, sink: new RenderSink(new QueryClient()) });
    stores.push(store);
    pipelines.push(pipeline);
    /** The keys of the elements with something drawn. */
    const keys = () => new Set([...drawn.keys()].map((id) => codec.decode(id)!.object.key));
    return { store, pipeline, drawn, updates, keys };
}

afterEach(() => {
    pipelines.splice(0).forEach((pipeline) => pipeline.dispose());
    stores.splice(0).forEach((store) => store.dispose());
    useMapStore.setState({ selected: null, hovered: null });
    useMapSettingsStore.setState({ ...defaultMapSettings });
    useMapFilterStore.setState({ filters: [], activeNames: [], active: [] });
});

describe("ElementPipeline", () => {
    it("draws the store's elements with the mappers registered before it initializes", () => {
        const a = line("a");
        const { pipeline, drawn } = setup(a, polygon("p"));

        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();

        expect([...drawn.keys()]).toEqual([idOf(a, "path"), idOf(a, "label")]);
        expect(drawn.get(idOf(a, "label"))?.filter).toEqual([1, LABEL.shown]);
        expect(drawn.get(idOf(a, "path"))?.filter).toEqual([1]);
    });

    it("draws a late mapper's type from the elements already in the store", async () => {
        const a = line("a");
        const p = polygon("p");
        const { pipeline, keys } = setup(a, p);
        pipeline.initialize();
        expect(keys()).toEqual(new Set());

        pipeline.registerMapper(stubMapper("polygon"));
        await flush();

        expect(keys()).toEqual(new Set([p.key]));
    });

    it("erases what a mapper drew when it is removed", async () => {
        const a = line("a");
        const p = polygon("p");
        const { pipeline, keys } = setup(a, p);
        pipeline.registerMapper(stubMapper("polygon"));
        const remove = pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();

        remove();
        await flush();

        expect(keys()).toEqual(new Set([p.key]));
    });

    it("re-maps only the type whose mapper's selection changed, with the new selection", async () => {
        const a = line("a");
        const { pipeline } = setup(a, polygon("p"));
        const lines = stubMapper("line", sizeSelector);
        const polygons = stubMapper("polygon");
        pipeline.registerMapper(lines);
        pipeline.registerMapper(polygons);
        pipeline.initialize();
        lines.map.mockClear();
        polygons.map.mockClear();

        useMapSettingsStore.getState().setTextSize(20);
        await flush();
        expect(lines.map).not.toHaveBeenCalled();

        useMapSettingsStore.getState().setSymbolSize(45);
        await flush();
        expect(lines.map).toHaveBeenCalledExactlyOnceWith(a, 45);
        expect(polygons.map).not.toHaveBeenCalled();
    });

    it("follows the store's adds, updates and removals", () => {
        const { store, pipeline, drawn, keys } = setup();
        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();
        const a = line("a", "first");

        store.add(a);
        expect(keys()).toEqual(new Set([a.key]));

        store.add(line("a", "second"));
        expect(drawn.get(idOf(a, "label"))?.style).toMatchObject({ text: "second" });

        store.delete(a.key);
        expect(drawn.size).toBe(0);
    });

    it("draws a draft always included with its label pinned, and erases it when cleared", () => {
        const { store, pipeline, drawn } = setup();
        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();
        useMapFilterStore.setState({
            active: [new AllExpression("none", [{ execute: () => false }])],
        });
        const draft = line("draft");

        store.setDraft(draft);
        expect(drawn.get(idOf(draft, "path"))?.filter).toEqual([1]);
        expect(drawn.get(idOf(draft, "label"))?.filter).toEqual([1, LABEL.pinned]);

        store.clearDraft();
        expect(drawn.size).toBe(0);
    });

    it("redraws a committed draft as an ordinary element", () => {
        const { store, pipeline, drawn } = setup();
        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();
        const draft = line("draft");
        store.setDraft(draft);

        store.commitDraft();

        expect(store.get(draft.key)).toBe(draft);
        expect(drawn.get(idOf(draft, "label"))?.filter).toEqual([1, LABEL.shown]);
    });

    it("redraws a draft as its geometry changes", () => {
        const { store, pipeline, drawn } = setup();
        const lines = stubMapper("line");
        pipeline.registerMapper(lines);
        pipeline.initialize();
        const draft = line("draft");
        store.setDraft(draft);
        lines.map.mockClear();

        draft.geometry.setPos([2, 2], 1);

        expect(lines.map).toHaveBeenCalledWith(draft, undefined);
        expect(drawn.get(idOf(draft, "label"))?.filter).toEqual([1, LABEL.pinned]);
    });

    it("draws the draft too when a late mapper draws its type", async () => {
        const { store, pipeline, keys } = setup();
        pipeline.initialize();
        const draft = line("draft");
        store.setDraft(draft);

        pipeline.registerMapper(stubMapper("line"));
        await flush();

        expect(keys()).toEqual(new Set([draft.key]));
    });

    it("redecorates the elements emphasized before or after a selection or hover change, and no others", () => {
        const a = line("a");
        const b = line("b");
        const { pipeline, drawn, updates } = setup(a, b, line("c"));
        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();
        const updatedKeys = () => new Set(updates.map((u) => codec.decode(u.id)!.object.key));

        useMapStore.getState().select(a.key);
        expect(updatedKeys()).toEqual(new Set([a.key]));
        expect(drawn.get(idOf(a, "path"))?.outline).toBe(true);
        expect(drawn.get(idOf(a, "label"))?.filter).toEqual([1, LABEL.pinned]);

        updates.length = 0;
        useMapStore.getState().hover(b.key);
        expect(updatedKeys()).toEqual(new Set([a.key, b.key]));
        expect(drawn.get(idOf(b, "label"))?.filter).toEqual([1, LABEL.pinned]);
        expect(drawn.get(idOf(b, "path"))?.outline).toBe(false);

        useMapStore.getState().resetSelection();
        expect(drawn.get(idOf(a, "path"))?.outline).toBe(false);
        expect(drawn.get(idOf(a, "label"))?.filter).toEqual([1, LABEL.shown]);
    });

    it("redecorates every element when the text settings or map filters change", () => {
        const a = line("a");
        const b = line("b");
        const { pipeline, drawn } = setup(a, b);
        pipeline.registerMapper(stubMapper("line"));
        pipeline.initialize();

        useMapSettingsStore.getState().setTextEnabled(false, "line");
        expect(drawn.get(idOf(a, "label"))?.filter).toEqual([1, LABEL.hidden]);
        expect(drawn.get(idOf(b, "label"))?.filter).toEqual([1, LABEL.hidden]);

        useMapFilterStore.setState({
            active: [new AllExpression("not a", [{ execute: ({ element }) => element !== a }])],
        });
        expect(drawn.get(idOf(a, "path"))?.filter).toEqual([-1]);
        expect(drawn.get(idOf(b, "path"))?.filter).toEqual([1]);
    });

    it("leaves what it drew and stops listening when disposed", async () => {
        const a = line("a");
        const { store, pipeline, drawn, updates } = setup(a);
        pipeline.registerMapper(stubMapper("line", sizeSelector));
        pipeline.initialize();
        const before = new Map(drawn);

        pipeline.dispose();
        expect(drawn).toEqual(before);

        store.add(line("b"));
        useMapStore.getState().select(a.key);
        useMapSettingsStore.getState().setTextEnabled(false, "line");
        useMapSettingsStore.getState().setSymbolSize(45);
        await flush();
        expect(drawn).toEqual(before);
        expect(updates).toEqual([]);
    });

    it("draws nothing after dispose for a re-map queued before it", async () => {
        const { pipeline, drawn, updates } = setup(line("a"));
        pipeline.registerMapper(stubMapper("line", sizeSelector));
        pipeline.initialize();
        const before = new Map(drawn);

        useMapSettingsStore.getState().setSymbolSize(45);
        pipeline.dispose();
        await flush();

        expect(drawn).toEqual(before);
        expect(updates).toEqual([]);
    });
});
