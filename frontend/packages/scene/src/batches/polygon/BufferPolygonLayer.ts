// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DefaultProps } from "@deck.gl/core";
import { SolidPolygonLayer, type SolidPolygonLayerProps } from "@deck.gl/layers";
import type { FillStyleExtensionProps } from "@deck.gl/extensions";
import { pickable, type PickableProps } from "../../picking/picking.util";

type BufferPolygonLayerProps = {
    /** Indices to draw. */
    vertexCount?: number;
};

/**
 * `SolidPolygonLayer` sets its top model's vertex count from its own tesselator, which
 * external buffers starve — so the draw takes its count from the tables instead. Everything
 * else rides the stock layer.
 */
class _BufferPolygonLayer extends SolidPolygonLayer<unknown, BufferPolygonLayerProps> {
    static layerName = "BufferPolygonLayer";
    static defaultProps: DefaultProps<SolidPolygonLayerProps & BufferPolygonLayerProps> = {
        vertexCount: { type: "number", value: 0 },
    };

    override draw(): void {
        const { topModel } = this.state as {
            topModel?: {
                setVertexCount(count: number): void;
                shaderInputs: { setProps(props: Record<string, unknown>): void };
                draw(pass: unknown): void;
            };
        };
        if (!topModel || !this.props.filled) return;

        topModel.setVertexCount(this.props.vertexCount ?? 0);
        topModel.shaderInputs.setProps({
            solidPolygon: { extruded: false, elevationScale: 1, isWireframe: false },
        });
        topModel.draw(this.context.renderPass);
    }
}

const BufferPolygonLayer = pickable(_BufferPolygonLayer);
export const pickableBufferPolygon = (
    props: BufferPolygonLayerProps &
        SolidPolygonLayerProps &
        Partial<FillStyleExtensionProps> &
        PickableProps,
) => new BufferPolygonLayer(props);
