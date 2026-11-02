// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { IconLayer, type IconLayerProps } from "@deck.gl/layers";
import vs from "./mixed-icon-layer.shader";
import { pickable, type PickableProps } from "../../picking/picking.util";

class MixedIconLayer extends IconLayer {
    static defaultProps = { ...super.defaultProps, getBillboard: { type: "accessor", value: 0 } };
    static layerName = "MixedIconLayer";
    static componentName = "MixedIconLayer";

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

const PickableMixedIconLayer = pickable(MixedIconLayer);
export const pickableMixedIcon = (props: IconLayerProps & PickableProps) =>
    new PickableMixedIconLayer(props);
