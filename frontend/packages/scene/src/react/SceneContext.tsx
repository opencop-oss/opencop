// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useEffect, useMemo, useState, type PropsWithChildren } from "react";
import { Scene } from "../Scene";
import type { Batch } from "../batches/Batch";

export type SceneContextValue = {
    scene: Scene;
};

const SceneContext = createContext<SceneContextValue | null>(null);

export type SceneProviderProps = PropsWithChildren & {
    batches: Batch[];
};

export function SceneProvider({ batches, children }: SceneProviderProps) {
    const [scene] = useState(() => new Scene(batches));
    const value = useMemo(() => ({ scene }), [scene]);

    useEffect(() => () => scene.destroy(), [scene]);

    useEffect(() => {
        scene.reconcile(batches);
    }, [scene, batches]);

    return <SceneContext value={value}>{children}</SceneContext>;
}

export function useScene(): SceneContextValue {
    const ctx = use(SceneContext);
    if (!ctx) throw new Error("useScene must be used within a SceneProvider.");
    return ctx;
}
