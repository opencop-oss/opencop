// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Layer, _LayersPass as LayersPass } from "@deck.gl/core";
import { OutlineExtension } from "./OutlineExtension";

/** Render pass over all layers that draws outlined object's silhouette into an offscreen buffer. */
export class CoverPass extends LayersPass {
    shouldDrawLayer(layer: Layer): boolean {
        return layer.props.extensions.some((ex) => ex instanceof OutlineExtension);
    }

    protected getShaderModuleProps() {
        return {
            cover: { enabled: true }, // turn cover on for the shader
            lighting: { enabled: false }, // turn lighting off (we don't need it here).
        };
    }

    getLayerParameters(layer: Layer) {
        return {
            ...(layer.props.parameters as object),
            depthWriteEnabled: true,
            depthCompare: "less-equal" as const,
        };
    }
}
