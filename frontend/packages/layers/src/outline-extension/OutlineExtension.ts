// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    type AccessorFunction,
    type DefaultProps,
    Layer,
    type LayerContext,
    LayerExtension,
} from "@deck.gl/core";
import cover from "./cover.shader";
import { OutlineEffect } from "./OutlineEffect";

export type OutlineExtensionProps<DataT = unknown> = {
    getOutline: AccessorFunction<DataT, boolean>;
};

export type OutlineExtensionOptions = {
    enabled?: boolean;
    color?: readonly [number, number, number];
    width?: number;
};

const defaultProps = {
    getOutline: { type: "accessor", value: () => false },
} as const satisfies DefaultProps<OutlineExtensionProps>;

export class OutlineExtension extends LayerExtension<OutlineExtensionOptions> {
    static defaultProps = defaultProps;
    static extensionName = "OutlineExtension";

    getShaders(this: Layer) {
        return {
            modules: [cover],
        };
    }

    constructor(
        opts: Partial<OutlineExtensionOptions> = { enabled: false, color: [1, 1, 1], width: 3 },
    ) {
        const { color = [1, 1, 1], width = 3, enabled = false } = opts;
        super({ color, width, enabled });
    }

    initializeState(
        this: Layer<OutlineExtensionProps>,
        context: LayerContext,
        extension: this,
    ): void {
        const am = this.getAttributeManager();
        if (!am) return;
        const { color = [1, 1, 1], width = 3 } = extension.opts ?? {};

        context.deck?._addDefaultEffect(new OutlineEffect({ color, width }));

        am.add({
            outlines: {
                accessor: "getOutline",
                size: 1,
                stepMode: "dynamic",
                type: "float32",
                transition: false,
                transform: (v: boolean) => Number(v),
            },
        });
    }
}
