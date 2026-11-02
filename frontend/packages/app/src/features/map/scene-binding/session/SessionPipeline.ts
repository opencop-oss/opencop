// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MeasurementSystem } from "@opencop/layers";
import { interleave, path, type Primitive } from "@opencop/scene";
import { RenderPipeline } from "../RenderPipeline";
import type { Selector } from "../RenderSink";
import { pinLabels } from "../elements/element-pipeline.util";
import type { SessionObject, SessionStore } from "./SessionStore";

const unitSelector: Selector<MeasurementSystem> = {
    select: (state) => state.units.measurement,
};

/**
 * Draws the running session's product, when it is not an element, and its
 * construction path. Picking any of it does nothing.
 */
export class SessionPipeline extends RenderPipeline<SessionStore> {
    private cleanup?: () => void;

    public override initialize(): void {
        super.initialize();
        this.cleanup = this.sink.subscribe(unitSelector, () => {
            for (const object of this.store.getAll()) this.update(object);
        });
    }

    public override dispose(): void {
        super.dispose();
        this.cleanup?.();
    }

    protected map({ product, construction }: SessionObject): Primitive[] | undefined {
        const primitives: Primitive[] = [];
        const selected = unitSelector.select(this.sink.get());
        if (product)
            primitives.push(
                ...(product.tool.mapProduct?.(product.draft, { measurement: selected }) ?? []),
            );
        if (construction)
            primitives.push(
                path({
                    id: "construction",
                    geometry: { positions: interleave(construction) },
                    style: {
                        strokeColor: [123, 116, 116, 255],
                        strokeWidth: 3,
                        strokeDash: [3, 3],
                    },
                }),
            );
        return pinLabels(primitives);
    }
}
