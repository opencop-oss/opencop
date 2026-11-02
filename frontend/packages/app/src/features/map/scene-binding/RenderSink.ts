// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { hashKey, type QueryClient } from "@tanstack/react-query";
import { useMapFilterStore } from "~/features/map-filter/map-filter.store";
import { preferencesQueries } from "~/features/preferences/preferences.queries";
import { useMapStore } from "../core/map.store";
import { useMapSettingsStore } from "../map-settings.store";
import type { CompiledFilter } from "~/features/map-filter/compiled-filter.model";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import type { UserPreferences } from "@opencop/api";
import type { LabelUnits } from "@opencop/tools";
import { shallow } from "zustand/shallow";
import { debounce } from "lodash";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import type { HistoricalDataManager } from "~/features/historical/data-plane/HistoricalDataManager";
import { isCustodyEdit } from "../interaction-runtime/session/session";
import {
    getInteractionState,
    subscribeInteraction,
} from "../interaction-runtime/session/interaction.store";

export type RenderState = {
    /** Which identities are emphasized. */
    readonly emphasis: {
        readonly selected: string | null;
        readonly hovered: string | null;
        readonly outlineColor: string;
        /** Whether the running interaction session is a custody edit. */
        readonly custodyEdit: boolean;
    };
    /** Which facts are shown — your fact-selection trichotomy leg. */
    readonly inclusion: {
        readonly filters: readonly CompiledFilter[];
        /** Whether historical playback is shown rather than live data. */
        readonly historical: boolean;
        /** The historical playhead's data filter range. Null without a historical session. */
        readonly playheadRange: [number, number] | null;
    };
    /** Text display policy inputs. */
    readonly text: {
        readonly enabledTypes: Readonly<Record<string, boolean>>;
        /** Sources subject to per-source text visibility; others always show text. */
        readonly managedSources: readonly string[];
        /** Managed sources whose text is shown. */
        readonly visibleSources: readonly string[];
        readonly size: number;
        readonly zoomThreshold: number;
    };
    readonly units: LabelUnits;
    readonly icons: { readonly size: number };
    readonly terrain: {
        readonly enabled: boolean;
        readonly drape: boolean;
        readonly depthCompare: "lessequal" | "always";
    };
    /** Settled viewport for screen-anchored derivation. Null until the first fix. */
    readonly view: {
        readonly bounds: [left: number, bottom: number, right: number, top: number];
        readonly latitude: number;
        readonly zoom: number;
    };
};

/** A state selector for {@link RenderState}. */
export interface Selector<State = unknown> {
    select(state: RenderState): State;
    equalityFn?(next: State, prev: State): boolean;
    debounce?: number;
}

type Subscriber<State = unknown> = {
    selector: Selector<State>;
    listener: (next: State, prev: State) => void;
};

export class RenderSink {
    private readonly client: QueryClient;
    private readonly subscribers = new Set<Subscriber>();
    private detach?: () => void;
    private historical: HistoricalDataManager | null = null;
    private unsubscribeHistorical?: () => void;

    private state: RenderState;

    constructor(client: QueryClient) {
        this.client = client;
        this.state = this.read();
    }

    /** The current render state; cached while anything is subscribed, read fresh otherwise. */
    public get(): RenderState {
        return this.detach ? this.state : this.read();
    }

    private read(): RenderState {
        const { selected, hovered, latitude, zoom, bounds, terrainConfig, drape, depthCompare } =
            useMapStore.getState();
        const {
            outlineColor,
            textEnabled,
            textSize,
            textZoomThreshold,
            angleUnit,
            angleOrientation,
            symbolSize,
        } = useMapSettingsStore.getState();
        const { knownSources, textVisibleSources } = useFilterPanelStore.getState();
        const { active } = useMapFilterStore.getState();
        const { mode } = useHistoricalStore.getState();
        const { session } = getInteractionState();

        return {
            emphasis: {
                selected,
                hovered,
                outlineColor,
                custodyEdit: isCustodyEdit(session),
            },
            inclusion: {
                filters: active,
                historical: mode === "historical",
                playheadRange: this.historical?.dataFilterRange ?? null,
            },
            text: {
                enabledTypes: textEnabled,
                managedSources: knownSources,
                visibleSources: textVisibleSources,
                size: textSize,
                zoomThreshold: textZoomThreshold,
            },
            units: {
                angle: angleUnit,
                angleOrientation,
                measurement:
                    this.client.getQueryData<UserPreferences>(preferencesQueries.current().queryKey)
                        ?.measurementSystem ?? "IMPERIAL",
            },
            icons: {
                size: symbolSize,
            },
            terrain: {
                enabled: !!terrainConfig,
                depthCompare,
                drape,
            },
            view: {
                latitude,
                zoom,
                bounds: bounds ?? [-180, -90, 180, 90],
            },
        };
    }

    public subscribe<State = RenderState>(
        selector: Selector<State>,
        listener: (next: State, prev: State) => void,
    ): () => void {
        const debounced = selector.debounce ? debounce(listener, selector.debounce) : undefined;
        const subscriber = { selector, listener: debounced ?? listener } as Subscriber;
        if (this.subscribers.size === 0) this.attach();
        this.subscribers.add(subscriber);
        return () => {
            debounced?.cancel();
            if (this.subscribers.delete(subscriber) && this.subscribers.size === 0) this.detach?.();
        };
    }

    /** Points the sink at the current historical session's manager, or none. */
    public setHistorical(historical: HistoricalDataManager | null): void {
        if (historical === this.historical) return;
        this.historical = historical;
        if (!this.detach) return;
        this.listenToHistorical();
        this.updateState();
    }

    private listenToHistorical(): void {
        this.unsubscribeHistorical?.();
        this.unsubscribeHistorical = this.historical?.subscribe(() => this.updateState());
    }

    private attach(): void {
        this.state = this.read();
        const preferences = hashKey(preferencesQueries.current().queryKey);
        const unsubscribes = [
            useMapStore.subscribe(
                ({
                    selected,
                    hovered,
                    latitude,
                    zoom,
                    bounds,
                    terrainConfig,
                    drape,
                    depthCompare,
                }) => ({
                    selected,
                    hovered,
                    latitude,
                    zoom,
                    bounds,
                    terrainConfig,
                    drape,
                    depthCompare,
                }),
                () => this.updateState(),
                {
                    equalityFn: shallow,
                },
            ),
            useMapSettingsStore.subscribe(
                ({
                    outlineColor,
                    textEnabled,
                    textSize,
                    textZoomThreshold,
                    angleUnit,
                    angleOrientation,
                    symbolSize,
                }) => ({
                    outlineColor,
                    textEnabled,
                    textSize,
                    textZoomThreshold,
                    angleUnit,
                    angleOrientation,
                    symbolSize,
                }),
                () => this.updateState(),
                {
                    equalityFn: shallow,
                },
            ),
            useFilterPanelStore.subscribe(
                ({ knownSources, textVisibleSources }) => ({ knownSources, textVisibleSources }),
                () => this.updateState(),
                {
                    equalityFn: shallow,
                },
            ),
            useMapFilterStore.subscribe(
                ({ active }) => active,
                () => this.updateState(),
            ),
            this.client.getQueryCache().subscribe(({ query }) => {
                if (query.queryHash === preferences) this.updateState();
            }),
            useHistoricalStore.subscribe((state, prev) => {
                if (state.mode !== prev.mode) this.updateState();
            }),
            subscribeInteraction((state, prev) => {
                if (isCustodyEdit(state.session) !== isCustodyEdit(prev.session))
                    this.updateState();
            }),
        ];
        this.listenToHistorical();
        this.detach = () => {
            for (const unsubscribe of unsubscribes) unsubscribe();
            this.unsubscribeHistorical?.();
            this.unsubscribeHistorical = undefined;
            this.detach = undefined;
        };
    }

    private emit(next: RenderState, prev: RenderState): void {
        this.subscribers.forEach(({ selector, listener }) => {
            const snext = selector.select(next);
            const sprev = selector.select(prev);
            const equal = selector.equalityFn ? selector.equalityFn(snext, sprev) : snext === sprev;
            if (!equal) listener(snext, sprev);
        });
    }

    private updateState() {
        const prev = this.state;
        const next = this.read();

        this.state = next;

        if (this.state !== prev) this.emit(next, prev);
    }
}
