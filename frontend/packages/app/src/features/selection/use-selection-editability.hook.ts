// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { getElementKey, localEventSource, type Element } from "@opencop/elements";
import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { getEditability, type Editability } from "~/features/map/editability";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";

export type SelectionPermissionStatus = "pending" | "error" | "ready";

// Stable references so consumers memoizing on these don't recompute every render
// while the membership queries are still loading.
const EMPTY_LAYERS: readonly MyLayerInfo[] = [];
const EMPTY_DATA_SOURCES: readonly MyDataSource[] = [];

export interface SelectionEditability {
    /** Per-element editability keyed by `getElementKey`; empty until `permissionStatus` is "ready". */
    editability: ReadonlyMap<string, Editability>;
    permissionStatus: SelectionPermissionStatus;
    myLayers: readonly MyLayerInfo[];
    myDataSources: readonly MyDataSource[];
}

/**
 * Editability for every element of an area selection, from the user's layer
 * and data-source memberships. An all-local selection is ready immediately;
 * anything remote waits for both membership lists, and reports "error" rather
 * than treating an unresolved lookup as denial.
 */
export function useSelectionEditability(elements: readonly Element[]): SelectionEditability {
    const layerQuery = useQuery(layerQueries.myList());
    const dataSourceQuery = useQuery(dataSourceQueries.myList());
    const myLayers = layerQuery.data ?? EMPTY_LAYERS;
    const myDataSources = dataSourceQuery.data ?? EMPTY_DATA_SOURCES;

    const allLocal = elements.every((element) => element.source === localEventSource);
    const membershipsAvailable =
        layerQuery.data !== undefined && dataSourceQuery.data !== undefined;
    const permissionStatus: SelectionPermissionStatus =
        allLocal || membershipsAvailable
            ? "ready"
            : layerQuery.isError || dataSourceQuery.isError
              ? "error"
              : "pending";

    const editability = useMemo(() => {
        if (permissionStatus !== "ready") return new Map<string, Editability>();
        return new Map(
            elements.map((element) => [
                getElementKey(element),
                getEditability(element.source, myLayers, myDataSources),
            ]),
        );
    }, [elements, myDataSources, myLayers, permissionStatus]);

    return { editability, permissionStatus, myLayers, myDataSources };
}
