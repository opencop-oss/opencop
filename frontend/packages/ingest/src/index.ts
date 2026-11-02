// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

export { ElementStore } from "./ElementStore";
export type { Store, StoreEvent, ObjectRef } from "./Store";
export { decodeHistoricalElement } from "./historical";
export type { MessageProcessor } from "./MessageProcessor";
export { Source, type SourceState } from "./sources/Source";
export { WebSocketSource, destinationPrefix } from "./sources/WebSocketSource";
export { LocalSource } from "./sources/LocalSource";
export { getProcessor } from "./init-processor.util";
export {
    useElementCount,
    useAllElements,
    useElementsBySource,
    useElement,
    useDraftElement,
    useElementsByType,
    useSourceCount,
    createActions,
    useElementActions,
    type ElementActions,
    type UseAllElementsOptions,
} from "./react/use-elements.hook.ts";
export {
    useSources,
    useSourceActions,
    useSourceState,
    useSourcesWithState,
    type SourceActions,
    useLocalSource,
    useContributeSources,
} from "./react/use-sources.hook.ts";
export { type SourceInfo } from "./source-controller.store.ts";
export { useGeometrySubscription } from "./react/use-geometry-subscription.hook.ts";
/** EXPORTED ONLY FOR MAPMANAGER COUPLING */
export { useIngestContext as _useIngestContext } from "./react/IngestContext.tsx";
