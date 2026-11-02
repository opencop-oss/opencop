// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { CompositeLayer, Layer, type LayerContext, type LayersList } from "@deck.gl/core";
import type { Scene } from "./Scene";
import type { SceneProps } from "./SceneProps";

export type SceneLayerProps = SceneProps & {
    scene: Scene;
};

export class SceneLayer extends CompositeLayer<SceneLayerProps> {
    public static layerName = "SceneLayer";
    public static get componentName(): string {
        return this.layerName;
    }

    declare state: {
        handle: number;
    };

    initializeState(context: LayerContext): void {
        const internal = this.internalState!;
        const handle = context.timeline.attachAnimation({
            setTime: () => {
                const layer = internal.layer;
                if (layer?.props.scene.flush()) layer.setNeedsUpdate();
            },
        });
        this.setState({ handle });
    }

    finalizeState(context: LayerContext): void {
        context.timeline.detachAnimation(this.state.handle);
        super.finalizeState(context);
    }

    renderLayers(): Layer | null | LayersList {
        return this.props.scene.render(this.props);
    }
}
