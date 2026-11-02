// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { GetPickingInfoParams, Layer, LayerContext, PickingInfo } from "@deck.gl/core";
import {
    IconLayer,
    PathLayer,
    ScatterplotLayer,
    SolidPolygonLayer,
    type IconLayerProps,
    type PathLayerProps,
    type ScatterplotLayerProps,
    type SolidPolygonLayerProps,
} from "@deck.gl/layers";
import type { PickingIndex } from "./PickingIndex";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type LayerCtor = new (...args: any[]) => Layer;

/**
 * Routes deck.gl's multi-depth picking hooks back to the store that owns the
 * buffer.
 *
 * The stock implementations assume the layer allocated its own picking-color
 * attribute; ours is an external buffer, so `Attribute.buffer` is null and they
 * throw.
 */
export function pickable<T extends LayerCtor>(Base: T) {
    return class extends Base {
        declare props: {
            pickingIndex: PickingIndex;
        } & Layer["props"];

        declare initializeState: (context: LayerContext) => void;

        override getPickingInfo({ info }: GetPickingInfoParams) {
            return {
                ...info,
                object: {
                    primitiveId: this.props.pickingIndex.resolve(info.index),
                },
            };
        }

        override disablePickingIndex(handle: number): void {
            this.props.pickingIndex.mask(handle);
        }

        override restorePickingColors(): void {
            this.props.pickingIndex.unmask();
        }
    };
}

const PickableIconLayer = pickable(IconLayer);
const PickableScatterplotLayer = pickable(ScatterplotLayer);
const PickablePathLayer = pickable(PathLayer);
const PickableSolidPolygonLayer = pickable(SolidPolygonLayer);

export type PickableProps = { pickable: true; pickingIndex: PickingIndex };
export const pickableIcon = (props: IconLayerProps & PickableProps) => new PickableIconLayer(props);
export const pickableScatterplot = (props: ScatterplotLayerProps & PickableProps) =>
    new PickableScatterplotLayer(props);
export const pickablePath = (props: PathLayerProps & PickableProps) => new PickablePathLayer(props);
export const pickableSolidPolygon = (props: SolidPolygonLayerProps & PickableProps) =>
    new PickableSolidPolygonLayer(props);

export function primitiveId(info: PickingInfo): string | undefined {
    if (!info.object || !("primitiveId" in info.object)) return;
    return info.object.primitiveId as string;
}
