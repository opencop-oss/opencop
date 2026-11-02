// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { noop } from "lodash";
import { QueryClient } from "@tanstack/react-query";
import { PolygonGeometry } from "@opencop/elements";
import type { ObjectRef } from "@opencop/ingest";
import type { Primitive, Scene } from "@opencop/scene";
import type { RenderSink } from "~/features/map/scene-binding/RenderSink";
import { LABEL } from "~/features/map/scene-binding/elements/element-pipeline.util";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import { WatchboxPipeline } from "./WatchboxPipeline";
import type { Watchbox } from "./watchbox.model";
import {
    createWatchboxStore,
    watchboxDraftOf,
    WATCHBOX_DRAFT_KEY,
    WATCHBOX_STORE_ID,
    WATCHBOX_TYPE,
    type WatchboxObject,
    type WatchboxStore,
} from "./watchbox.store";

function watchbox(id: string, enabled = true): WatchboxObject {
    return {
        key: id,
        type: WATCHBOX_TYPE,
        watchbox: { id, name: `Watchbox ${id}`, enabled } as Watchbox,
        polygon: [0, 0, 2, 0, 2, 2],
    };
}

/** A watchbox store already holding `objects`; it never changes. */
function heldStore(objects: WatchboxObject[]): WatchboxStore {
    const held = new Map(objects.map((object) => [object.key, object]));
    return {
        type: WATCHBOX_STORE_ID,
        get: (key: string) => held.get(key),
        getAll: () => held.values(),
        ref: (object: WatchboxObject): ObjectRef => ({ type: WATCHBOX_STORE_ID, key: object.key }),
        on: noop,
        off: noop,
        connect: () => () => {},
    } as unknown as WatchboxStore;
}

/** A scene that only records what it holds. */
function fakeScene() {
    const drawn = new Map<string, Primitive>();
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
    return { scene, drawn };
}

const pipelines: WatchboxPipeline[] = [];

function draw(objects: WatchboxObject[]) {
    const { scene, drawn } = fakeScene();
    const pipeline = new WatchboxPipeline({
        scene,
        store: heldStore(objects),
        sink: {} as RenderSink,
    });
    pipeline.initialize();
    pipelines.push(pipeline);
    const labels = () =>
        [...drawn.values()].filter((p) => p.style.type === "text").map((p) => p.filter);
    const keys = () => new Set([...drawn.keys()].map((id) => id.split(":")[1]));
    return { pipeline, labels, keys };
}

afterEach(() => {
    pipelines.splice(0).forEach((pipeline) => pipeline.dispose());
    useWatchboxPanelStore.setState(useWatchboxPanelStore.getInitialState(), true);
    useWatchboxVisibilityStore.setState({ hideDisabled: false });
});

describe("WatchboxPipeline", () => {
    it("draws every saved watchbox, unlabelled", () => {
        const { keys, labels } = draw([watchbox("a"), watchbox("b")]);

        expect(keys()).toEqual(new Set(["a", "b"]));
        expect(labels()).toEqual([]);
    });

    it("pins a label on the watchbox hovered or selected in the panel, and drops it after", () => {
        const { labels } = draw([watchbox("a"), watchbox("b")]);

        useWatchboxPanelStore.getState().hover("a");
        expect(labels()).toEqual([[1, LABEL.pinned]]);

        useWatchboxPanelStore.getState().select("b");
        expect(labels()).toHaveLength(2);

        useWatchboxPanelStore.getState().hover(null);
        useWatchboxPanelStore.getState().select(null);
        expect(labels()).toEqual([]);
    });

    it("removes disabled watchboxes while hide-disabled is on, and restores them after", () => {
        const { keys } = draw([watchbox("on"), watchbox("off", false)]);

        useWatchboxVisibilityStore.getState().actions.setHideDisabled(true);
        expect(keys()).toEqual(new Set(["on"]));

        useWatchboxVisibilityStore.getState().actions.setHideDisabled(false);
        expect(keys()).toEqual(new Set(["on", "off"]));
    });

    it("erases every watchbox it drew", () => {
        const { pipeline, keys } = draw([watchbox("a"), watchbox("b")]);
        useWatchboxPanelStore.getState().select("a");

        pipeline.erase();
        expect(keys()).toEqual(new Set());
    });

    it("draws the watchbox being drawn, unlabelled, until the store drops it", () => {
        const store = createWatchboxStore(new QueryClient());
        const { scene, drawn } = fakeScene();
        const pipeline = new WatchboxPipeline({ scene, store, sink: {} as RenderSink });
        pipeline.initialize();
        pipelines.push(pipeline);
        const geometry = new PolygonGeometry([0, 0, 2, 0, 2, 2], 2);

        store.setDraft(watchboxDraftOf({ key: "d", type: WATCHBOX_TYPE, geometry }));
        const keys = [...drawn.keys()].map((id) => id.split(":")[1]);
        expect(new Set(keys)).toEqual(new Set([WATCHBOX_DRAFT_KEY]));
        expect([...drawn.values()].filter((p) => p.style.type === "text")).toEqual([]);

        store.clearDraft();
        expect(drawn.size).toBe(0);
    });
});
