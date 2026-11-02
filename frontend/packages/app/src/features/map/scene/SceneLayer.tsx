// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useEffect } from "react";
import { isEqual } from "lodash";
import { SceneLayer as _SceneLayer, useScene, type SceneProps } from "@opencop/scene";
import { useRegister } from "../DeckLayerContext";
import { DECK_PRIORITY_SCENE } from "../deck-layer-priorities";
import { readSceneProps } from "./scene-props.util";
import {
    useInitializeElementPipeline,
    useInitializeSessionPipeline,
    useSceneBinding,
} from "../scene-binding/SceneBindingContext";

/** Renders the element store through the retained scene. */
export function SceneLayer() {
    const { scene } = useScene();
    const { sink } = useSceneBinding();
    const register = useRegister();
    useInitializeElementPipeline();
    useInitializeSessionPipeline();

    useEffect(() => {
        const render = (props: SceneProps) =>
            register("scene", [new _SceneLayer({ scene, ...props })], DECK_PRIORITY_SCENE);

        render(readSceneProps(sink.get()));
        return sink.subscribe({ select: readSceneProps, equalityFn: isEqual }, render);
    }, [scene, sink, register]);

    return null;
}
