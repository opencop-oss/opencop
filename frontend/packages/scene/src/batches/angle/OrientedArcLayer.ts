// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Moved from @opencop/layers' angle layer, adding the picking wrapper and making getAngles optional.

import type { Accessor, DefaultProps } from "@deck.gl/core";
import { ScatterplotLayer, type ScatterplotLayerProps } from "@deck.gl/layers";
import { inject } from "./arc.shader";
import { pickable, type PickableProps } from "../../picking/picking.util";

type _OrientedArcLayerProps<DataT = unknown> = {
    getAngles?: Accessor<DataT, [number, number]>;
};

export type OrientedArcLayerProps<DataT = unknown> = _OrientedArcLayerProps<DataT> &
    ScatterplotLayerProps<DataT>;

const defaultProps = {
    ...ScatterplotLayer.defaultProps,
    getAngles: { type: "accessor", value: () => [0, 0] },
} as const satisfies DefaultProps<OrientedArcLayerProps>;

export class OrientedArcLayer<DataT, ExtraPropsT extends object = object> extends ScatterplotLayer<
    DataT,
    ExtraPropsT & _OrientedArcLayerProps<DataT>
> {
    static layerName = "OrientedArcLayer";
    static componentName = OrientedArcLayer.layerName;
    static defaultProps = defaultProps;

    getShaders() {
        return { ...super.getShaders(), inject };
    }

    initializeState(): void {
        super.initializeState();
        const am = this.getAttributeManager()!;
        am.addInstanced({
            instanceAngles: {
                size: 2,
                type: "float32",
                accessor: "getAngles",
                transition: false,
            },
        });
    }
}

const PickableOrientedArcLayer = pickable(OrientedArcLayer);
export const pickableOrientedArc = (props: OrientedArcLayerProps & PickableProps) =>
    new PickableOrientedArcLayer(props);
