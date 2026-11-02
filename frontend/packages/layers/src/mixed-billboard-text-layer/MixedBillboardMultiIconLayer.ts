// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { _MultiIconLayer } from "@deck.gl/layers";
import { vs } from "./mixed-billboard-multi-icon-layer-vertex.glsl";

export class MixedBillboardMultiIconLayer<
    DataT,
    ExtraProps extends object = object,
> extends _MultiIconLayer<DataT, ExtraProps> {
    static defaultProps = { ...super.defaultProps, getBillboard: { type: "accessor", value: 0 } };
    static layerName = "MixedBillboardMultiIconLayer";
    static componentName = "MixedBillboardMultiIconLayer";

    getShaders() {
        return { ...super.getShaders(), vs };
    }

    initializeState(): void {
        super.initializeState();
        const am = this.getAttributeManager()!;
        am.addInstanced({
            instanceBillboards: {
                size: 1,
                type: "uint8",
                transition: false,
                defaultValue: 0,
                accessor: "getBillboard",
            },
        });
    }
}
