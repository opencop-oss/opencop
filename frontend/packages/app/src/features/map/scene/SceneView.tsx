// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SceneProvider } from "@opencop/scene";
import { useState, type PropsWithChildren } from "react";
import { setup } from "./setup";
import { useDevice } from "../DeckLayerContext";
import { SceneBindingProvider } from "../scene-binding/SceneBindingContext";

export function SceneView({ children }: PropsWithChildren) {
    const device = useDevice();
    const [batches] = useState(() => setup({ device }));
    return (
        <SceneProvider batches={batches}>
            <SceneBindingProvider>{children}</SceneBindingProvider>
        </SceneProvider>
    );
}
