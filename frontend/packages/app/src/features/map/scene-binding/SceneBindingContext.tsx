// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, useEffect, useMemo, type PropsWithChildren } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useScene } from "@opencop/scene";
import { _useIngestContext } from "@opencop/ingest";
import { RenderSink } from "./RenderSink";
import { ElementPipeline } from "./elements/ElementPipeline";
import {
    getElementMappers,
    subscribeElementMappers,
} from "~/features/contributions/element-mapper-registry";
import { getTool } from "~/features/contributions/tool-registry";
import { useHistoricalDataManager } from "~/features/historical/data-plane/HistoricalDataContext";
import { SessionPipeline } from "./session/SessionPipeline";
import { createSessionStore } from "./session/SessionStore";

export type SceneBindingContextValue = {
    /** The render state every pipeline reads. */
    sink: RenderSink;
    /** The pipeline drawing the element store. */
    elements: ElementPipeline;
};

const SceneBindingContext = createContext<SceneBindingContextValue | null>(null);

/** Binds the app's stores to the scene. Must be rendered inside a `SceneProvider`. */
export function SceneBindingProvider({ children }: PropsWithChildren) {
    const client = useQueryClient();
    const { store } = _useIngestContext();
    const { scene } = useScene();
    const historical = useHistoricalDataManager();
    const sink = useMemo(() => new RenderSink(client), [client]);
    useEffect(() => sink.setHistorical(historical), [sink, historical]);
    const elements = useMemo(
        () => new ElementPipeline({ store, scene, sink }),
        [store, scene, sink],
    );

    const value = useMemo(() => ({ sink, elements }), [sink, elements]);

    return <SceneBindingContext value={value}>{children}</SceneBindingContext>;
}

export function useSceneBinding(): SceneBindingContextValue {
    const ctx = use(SceneBindingContext);
    if (!ctx) throw new Error("useSceneBinding must be used within a SceneBindingProvider");
    return ctx;
}

/** Draws the running interaction session's non-element product through the scene while mounted. */
export function useInitializeSessionPipeline(): void {
    const { scene } = useScene();
    const { sink } = useSceneBinding();

    useEffect(() => {
        const store = createSessionStore(getTool);
        const disconnect = store.connect();
        const pipeline = new SessionPipeline({ scene, store, sink });
        pipeline.initialize();
        return () => {
            pipeline.dispose();
            disconnect();
        };
    }, [scene, sink]);
}

/** Draws the element store through the scene while mounted, with every registered mapper. */
export function useInitializeElementPipeline(): void {
    const { elements } = useSceneBinding();

    useEffect(() => {
        const removals = new Map<object, () => void>();
        const syncContributed = () => {
            const current = new Set(getElementMappers());
            for (const [mapper, remove] of removals) {
                if (current.has(mapper as never)) continue;
                remove();
                removals.delete(mapper);
            }
            for (const mapper of current) {
                if (!removals.has(mapper)) removals.set(mapper, elements.registerMapper(mapper));
            }
        };
        syncContributed();
        const unsubscribe = subscribeElementMappers(syncContributed);

        elements.initialize();
        return () => {
            unsubscribe();
            for (const remove of removals.values()) remove();
            elements.dispose();
        };
    }, [elements]);
}
