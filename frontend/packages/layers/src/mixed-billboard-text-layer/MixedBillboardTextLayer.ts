// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type _ConstructorOf, type Accessor, Layer } from "@deck.gl/core";
import type { TextLayerProps } from "@deck.gl/layers";
import { MixedBillboardMultiIconLayer } from "./MixedBillboardMultiIconLayer";
import { RecyclingTextLayer } from "../recycling-text-layer/RecyclingTextLayer";

export type MixedBillboardTextLayerProps<DataT = unknown> = TextLayerProps<DataT> & {
    getBillboard: Accessor<DataT, 0 | 1>;
};

// Extends `RecyclingTextLayer` (not `TextLayer` directly) so every live SDF label
// layer — all of which descend from this through `BinaryTextLayer` /
// `CulledTextLayer` — inherits the never-evicting-font-atlas bound. This is the
// single chokepoint for live label text (see `RecyclingTextLayer`).
export class MixedBillboardTextLayer<
    DataT,
    ExtraProps extends object = object,
> extends RecyclingTextLayer<DataT, ExtraProps & MixedBillboardTextLayerProps<DataT>> {
    static defaultProps = { ...super.defaultProps, getBillboard: { type: "accessor", value: 0 } };
    static layerName = "MixedBillboardTextLayer";
    static componentName = "MixedBillboardTextLayer";

    protected getSubLayerClass<T extends Layer>(
        subLayerId: string,
        DefaultLayerClass: _ConstructorOf<T>,
    ): _ConstructorOf<T> {
        if (subLayerId === "characters") {
            const cls = MixedBillboardMultiIconLayer;
            return super.getSubLayerClass(subLayerId, cls) as unknown as _ConstructorOf<T>;
        }

        return super.getSubLayerClass(subLayerId, DefaultLayerClass);
    }

    protected getSubLayerProps(sublayerProps?: {
        id?: string;
        updateTriggers?: Record<string, unknown>;
        [propName: string]: unknown;
    }) {
        return {
            ...super.getSubLayerProps(sublayerProps),
            getBillboard: this.props["getBillboard"],
        };
    }
}
