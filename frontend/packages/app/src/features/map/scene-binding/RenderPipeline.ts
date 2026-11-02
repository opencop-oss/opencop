// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Primitive, Scene } from "@opencop/scene";
import type { RenderSink } from "./RenderSink";
import type { ObjectRef, Store } from "@opencop/ingest";
import { codec } from "./ref-codec";

export type ObjectOf<S extends Store> = S extends Store<infer T> ? T : never;

export type RenderPipelineProps<S extends Store> = {
    sink: RenderSink;
    scene: Scene;
    store: S;
};

export abstract class RenderPipeline<S extends Store = Store> {
    protected readonly scene: Scene;
    protected readonly store: S;
    protected readonly sink: RenderSink;
    protected readonly primitives = new Map<string, Primitive[]>();

    constructor({ scene, store, sink }: RenderPipelineProps<S>) {
        this.scene = scene;
        this.store = store;
        this.sink = sink;
    }

    public initialize(): void {
        this.store.on("add", this.onAdd);
        this.store.on("update", this.onUpdate);
        this.store.on("remove", this.onRemove);
        for (const object of this.store.getAll()) this.add(object as ObjectOf<S>);
    }

    public dispose(): void {
        this.store.off("add", this.onAdd);
        this.store.off("update", this.onUpdate);
        this.store.off("remove", this.onRemove);
        this.primitives.clear();
    }

    protected abstract map(object: ObjectOf<S>): Primitive[] | undefined;

    protected add(object: ObjectOf<S>): void {
        const primitives = this.map(object);
        if (!primitives) return;

        const ref = this.store.ref(object);
        this.qualify(ref, primitives);

        this.scene.add(primitives);
        this.primitives.set(ref.key, primitives);
    }

    protected update(object: ObjectOf<S>): void {
        const ref = this.store.ref(object);
        const prev = this.primitives.get(ref.key) ?? [];
        const next = this.map(object) ?? [];
        this.qualify(ref, next);

        const ids = new Set(next.map((primitive) => primitive.id));
        for (const { id } of prev) if (!ids.has(id) && this.scene.get(id)) this.scene.remove(id);
        for (const primitive of next) {
            if (this.scene.get(primitive.id)) this.scene.update(primitive);
            else this.scene.add(primitive);
        }

        if (next.length > 0) this.primitives.set(ref.key, next);
        else this.primitives.delete(ref.key);
    }

    protected remove(object: ObjectOf<S>): void {
        const ref = this.store.ref(object);
        const primitives = this.primitives.get(ref.key);
        if (!primitives) return;
        this.scene.remove(primitives.map((p) => p.id));
        this.primitives.delete(ref.key);
    }

    protected qualify(ref: ObjectRef, primitives: Primitive[]): void {
        for (const prim of primitives) {
            const primRef = {
                object: ref,
                nanoid: prim.id,
            };
            const encoded = codec.encode(primRef);
            prim.id = encoded;
        }
    }

    private onAdd = (objects: unknown[]) =>
        objects.forEach((object) => this.add(object as ObjectOf<S>));
    private onUpdate = (objects: unknown[]) =>
        objects.forEach((object) => this.update(object as ObjectOf<S>));
    private onRemove = (objects: unknown[]) =>
        objects.forEach((object) => this.remove(object as ObjectOf<S>));
}
