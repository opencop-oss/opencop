// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { MixedBillboardMultiIconLayer } from "../mixed-billboard-text-layer/MixedBillboardMultiIconLayer";

/** Small subclass of {@link _MultiIconLayer} that overrides an attribute definition. */
export class BinaryMultiIconLayer<
    DataT,
    ExtraProps extends object = object,
> extends MixedBillboardMultiIconLayer<DataT, ExtraProps> {
    initializeState(): void {
        super.initializeState();
        const am = this.getAttributeManager()!;
        // Override super parent definition to use default updater.
        am.addInstanced({
            instanceIconDefs: {
                size: 7,
                accessor: "getIcon",
                shaderAttributes: {
                    instanceOffsets: {
                        size: 2,
                        elementOffset: 0,
                    },
                    instanceIconFrames: {
                        size: 4,
                        elementOffset: 2,
                    },
                    instanceColorModes: {
                        size: 1,
                        elementOffset: 6,
                    },
                },
            },
        });
    }
}
