// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "@opencop/elements";
import { RenderPipeline } from "../RenderPipeline";
import type { ElementMapper } from "@opencop/contributions";
import { type Primitive } from "@opencop/scene";
import type { ElementStore } from "@opencop/ingest";
import { decorator, emphasisSelector, settingsSelector } from "./element-pipeline.util";

export class ElementPipeline extends RenderPipeline<ElementStore> {
    private readonly mappers: Record<string, ElementMapper> = {};
    private readonly selectorSubs = new Map<string, () => void>();
    private queued = new Set<string>(); // Queued types for invalidations.
    private initialized = false;
    private cleanup?: () => void;

    public override initialize(): void {
        this.initialized = true;
        for (const mapper of Object.values(this.mappers)) this.subscribeSelector(mapper);
        // TODO(343): Decoration is hardcoded here until filtering, text, and emphasis find a better home.
        const s0 = this.sink.subscribe(settingsSelector, () =>
            this.redecorate(this.store.getAll()),
        );
        const s1 = this.sink.subscribe(emphasisSelector, (next, prev) => {
            const keys = new Set([...next, ...prev]);
            this.redecorate([...keys].flatMap((key) => (key && this.store.get(key)) || []));
        });

        super.initialize();
        this.cleanup = () => (s0(), s1());
    }

    public override dispose(): void {
        super.dispose();
        this.initialized = false;
        this.cleanup?.();
        for (const unsub of this.selectorSubs.values()) unsub();
        this.selectorSubs.clear();
    }

    /**
     * Draws elements of the mapper's type with it. Elements already in the store
     * are drawn right away.
     * @returns The removal, which erases what the mapper drew.
     */
    public registerMapper<E extends Element, S>(mapper: ElementMapper<E, S>): () => void {
        const { type } = mapper;
        this.mappers[type] = mapper as ElementMapper;
        if (this.initialized) {
            this.subscribeSelector(mapper as ElementMapper);
            this.invalidate(type);
        }

        return () => {
            if (this.mappers[type] !== (mapper as ElementMapper)) return;
            delete this.mappers[type];
            this.selectorSubs.get(type)?.();
            this.selectorSubs.delete(type);
            if (this.initialized) this.invalidate(type);
        };
    }

    protected map(object: Element): Primitive[] | undefined {
        const mapper = this.mappers[object.type];
        if (!mapper) return;

        const state = this.sink.get();
        const selected = mapper.selector?.select(state);
        const mapped = mapper.map(object, selected);
        const decorate = decorator(object, state, this.isDraft(object), this.isContent(object));
        for (const primitive of mapped) Object.assign(primitive, decorate(primitive));

        return mapped;
    }

    private subscribeSelector(mapper: ElementMapper): void {
        if (!mapper.selector) return;
        const type = mapper.type;
        this.selectorSubs.set(
            type,
            this.sink.subscribe(mapper.selector, () => this.invalidate(type)),
        );
    }

    private isDraft(element: Element): boolean {
        return this.store.getDraft()?.key === element.key;
    }

    /** Whether the type's text is the object rather than a label about it. */
    private isContent(element: Element): boolean {
        return this.mappers[element.type]?.textIsContent === true;
    }

    /** Rewrites the decoration of the given elements' primitives. */
    private redecorate(elements: Iterable<Element>): void {
        const state = this.sink.get();
        for (const element of elements) {
            const primitives = this.primitives.get(element.key);
            if (!primitives) continue;
            const decorate = decorator(
                element,
                state,
                this.isDraft(element),
                this.isContent(element),
            );
            for (const primitive of primitives)
                this.scene.update({ id: primitive.id, ...decorate(primitive) });
        }
    }

    private invalidate(type: string) {
        if (this.queued.size === 0) queueMicrotask(() => this.flush());
        this.queued.add(type);
    }

    private flush() {
        if (!this.initialized) return this.queued.clear();

        const draft = this.store.getDraft();
        for (const type of this.queued) {
            for (const element of this.store.getByType(type)) {
                this.update(element);
            }
            // TODO(344): until we find better way to handle drafts.
            if (draft?.type === type) this.update(draft);
        }

        this.queued.clear();
    }
}
