// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    _TerrainExtension as TerrainExtension,
    type TerrainExtensionProps,
} from "@deck.gl/extensions";
import type {
    AccessorFunction,
    DefaultProps,
    Layer,
    LayerContext,
    UpdateParameters,
} from "@deck.gl/core";
import { TerrainOutlineEffect } from "./TerrainOutlineEffect";

export type TerrainOutlineExtensionProps<DataT = unknown> = TerrainExtensionProps & {
    getOutline: AccessorFunction<DataT, boolean>;
};

export type TerrainOutlineExtensionOptions = {
    outline: boolean;
    color: readonly [number, number, number];
};

const defaultProps = {
    ...TerrainExtension.defaultProps,
    getOutline: { type: "accessor", value: () => false },
} as const satisfies DefaultProps<TerrainOutlineExtensionProps>;

/**
 * Subclassed deck.gl {@link TerrainExtension} that supports outlining draped features.
 * The `OutlineEffect`'s coverage pass is too expensive for draped terrain.
 */
export class TerrainOutlineExtension extends TerrainExtension {
    static defaultProps = defaultProps;
    static extensionName = "TerrainOutlineExtension";

    declare opts: TerrainOutlineExtensionOptions;

    constructor(
        opts: Partial<TerrainOutlineExtensionOptions> = {
            outline: false,
            color: [1, 1, 1],
        },
    ) {
        const { outline = false, color = [1, 1, 1] } = opts;
        super({ outline, color });
    }

    getShaders(this: Layer<TerrainExtensionProps>) {
        return {
            defines: { OUTLINE: this.state.terrainOutline ? 1 : 0 },
        };
    }

    initializeState(
        this: Layer<TerrainExtensionProps>,
        context?: LayerContext,
        extension?: TerrainOutlineExtension,
    ): void {
        context?.deck?._addDefaultEffect(new TerrainOutlineEffect());
        extension?.updateOutline.bind(this)(extension?.opts.outline);
        this.setState({
            terrainOutline: extension?.opts.outline,
        });
    }

    updateState(
        this: Layer<TerrainExtensionProps>,
        params: UpdateParameters<Layer<TerrainExtensionProps>>,
        extension?: this,
    ): void {
        super.updateState.bind(this)(params);
        if (params.changeFlags.extensionsChanged)
            extension?.updateOutline.bind(this)(extension?.opts.outline);
    }

    private updateOutline(this: Layer, outline: boolean): void {
        this.setState({
            terrainOutline: outline,
        });
        if (outline) {
            const am = this.getAttributeManager();
            if (!am) return;

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
}
