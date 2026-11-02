// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import type { PartialPrimitive, Primitive, PrimitiveGeometry, PrimitiveStyle } from "./primitive";
import type { Layer, LayerExtension } from "@deck.gl/core";
import type { Batch } from "./batches/Batch";
import type { SceneProps } from "./SceneProps";

const logger = Logger.get("scene");

function isIterable<T>(primitives: T | Iterable<T>): primitives is Iterable<T> {
    return (
        typeof primitives !== "string" &&
        typeof (primitives as never)[Symbol.iterator] === "function"
    );
}

const extensionIds = new WeakMap<LayerExtension, number>();
let nextExtensionId = 0;

/** A layer's id qualified by the identity of each of its extensions. */
function layerId(layer: Layer): string {
    const ids = layer.props.extensions.map((extension) => {
        let id = extensionIds.get(extension);
        if (id === undefined) extensionIds.set(extension, (id = nextExtensionId++));
        return id;
    });
    return [layer.id, ...ids].join("-");
}

/**
 * Maintains the renderable primitives in the scene, delegating their GPU
 * representation to compatible batches.
 */
export class Scene {
    private readonly primitives = new Map<string, Primitive>();
    private batches: Record<string, Batch> = {};
    private dirty: boolean = false;

    constructor(batches?: Batch[]) {
        for (const batch of batches ?? []) {
            this.register(batch);
        }
    }

    /**
     * Adds a primitive to the scene.
     * @param primitive The primitive.
     */
    public add(primitive: Primitive): void;
    /**
     * Adds a set of primitives to the scene.
     * @param primitives Iterable set of primitives.
     */
    public add(primitives: Iterable<Primitive>): void;
    public add(primitives: Primitive | Iterable<Primitive>): void {
        if (isIterable(primitives)) for (const primitive of primitives) this._add(primitive);
        else this._add(primitives);
    }

    /**
     * Patches an existing primitive in the scene.
     *
     * The patch must have the same {@link Primitive.geometry geometry} and {@link Primitive.style style}
     * as the original if either are specified.
     * @param partial A {@link PartialPrimitive} meant to patch an existing primitive.
     */
    public update(partial: PartialPrimitive): void;
    /**
     * Patches existing primitive in the scene.
     *
     * Each patch must have the same {@link Primitive.geometry geometry} and {@link Primitive.style style}
     * as the original if either are specified.
     * @param partials
     */
    public update(partials: Iterable<PartialPrimitive>): void;
    public update(partials: PartialPrimitive | Iterable<PartialPrimitive>): void {
        if (isIterable(partials)) for (const partial of partials) this._update(partial);
        else this._update(partials);
    }

    /**
     * Removes a primitive from the scene and its batch.
     * @param id The ID of the primitive.
     */
    public remove(id: string): void;
    /**
     * Removes a set of primitives from the scene and their batches.
     * @param ids The ids of some primitives.
     */
    public remove(ids: Iterable<string>): void;
    public remove(ids: string | Iterable<string>): void {
        if (isIterable(ids)) for (const id of ids) this._remove(id);
        else this._remove(ids);
    }

    /** Returns the primitive with the given ID, if it exists. */
    public get(id: string): Primitive | undefined {
        return this.primitives.get(id);
    }

    /**
     * Commits any updates in the scene to the GPU.
     * @returns `true` if updates were made, `false` otherwise.
     */
    public flush(): boolean {
        const dirty = this.dirty;
        this.dirty = false;
        return (
            Object.values(this.batches).reduce((acc, batch) => batch.flush() || acc, false) || dirty
        );
    }

    /**
     * Produces deck.gl layers representing the scene.
     *
     * deck.gl only initializes a layer's extensions when it creates the layer, so
     * each layer's id includes its extensions: changing them creates a new layer.
     * @param props Optional deck.gl props for the batches' layers.
     * @returns List of deck.gl layers.
     */
    public render(props: SceneProps = {}): Layer[] {
        return Object.values(this.batches)
            .map((batch) => batch.render(props))
            .flat()
            .map((layer) => layer.clone({ id: layerId(layer) }));
    }

    public register(batch: Batch): void {
        const existing = this.batches[this.key(batch.geometry, batch.style)];
        if (existing === batch) return;
        if (existing) this.unregister(existing);
        this.batches[this.key(batch.geometry, batch.style)] = batch;
        this.dirty = true;
    }

    public unregister(batch: Batch): void {
        const existing = this.batches[this.key(batch.geometry, batch.style)];
        if (existing !== batch) return;
        delete this.batches[this.key(batch.geometry, batch.style)];
        batch.destroy();
        this.dirty = true;
    }

    public reconcile(batches: Batch[]): void {
        const next = new Set(batches);
        const prev = new Set(Object.values(this.batches));

        for (const batch of next) {
            if (!prev.has(batch)) this.register(batch);
        }

        for (const batch of prev) {
            if (!next.has(batch)) this.unregister(batch);
        }
    }

    /** Destroys all batches. */
    public destroy() {
        for (const batch of Object.values(this.batches)) batch.destroy();
        this.batches = {};
        this.primitives.clear();
    }

    /**
     * Adds a single primitive.
     * @param primitive The {@link Primitive}.
     */
    private _add(primitive: Primitive): void {
        if (this.primitives.has(primitive.id)) {
            logger.warn(`Primitive ${primitive.id} already exists. Use \`update\`.`);
            return;
        }

        const batch = this.batchFor(primitive);
        if (!batch) {
            logger.warn("No batch for the input primitive.", primitive);
            return;
        }

        const added = batch.add(primitive);
        if (!added) {
            logger.warn(`Batch failed to add primitive.`, primitive);
            return;
        }

        this.primitives.set(primitive.id, primitive);
    }

    /**
     * Updates a single primitive.
     * @param primitive The {@link Primitive}.
     */
    private _update(partial: PartialPrimitive): void {
        const prev = this.primitives.get(partial.id);
        if (!prev) {
            logger.warn(`Primitive ${partial.id} does not exist. Use \`add\` before \`update\`.`);
            return;
        }

        const prevBatch = this.batchFor(prev)!;
        const patched = {
            ...prev,
            geometry: {
                ...prev.geometry,
                ...partial.geometry,
            },
            style: {
                ...prev.style,
                ...partial.style,
            },
            filter: partial.filter ?? prev.filter,
            outline: partial.outline ?? prev.outline,
        } as Primitive;
        const nextBatch = this.batchFor(patched);
        if (prevBatch !== nextBatch) {
            logger.warn("Input primitive's geometry or style type differs from original.");
            return;
        }

        const updated = nextBatch.update(partial, patched);
        if (!updated) {
            logger.warn(`Batch failed to update the primitive ${patched.id}`);
            return;
        }

        this.primitives.set(partial.id, patched);
    }

    /**
     * Removes a single primitive.
     * @param id
     */
    private _remove(id: string): void {
        const primitive = this.primitives.get(id);
        if (!primitive) {
            logger.warn(`Primitive ${id} does not exist.`);
            return;
        }

        const batch = this.batchFor(primitive)!;
        const removed = batch.remove(id);
        if (!removed) {
            logger.warn(`Batch failed to remove primitive ${id}.`);
            return;
        }

        this.primitives.delete(id);
    }

    private batchFor(primitive: Primitive): Batch | undefined {
        return this.batches[this.key(primitive.geometry.type, primitive.style.type)];
    }

    private key(geometry: PrimitiveGeometry["type"], style: PrimitiveStyle["type"]): string {
        return geometry + ":" + style;
    }
}
