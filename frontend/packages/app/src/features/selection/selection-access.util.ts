// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import type { EventListItemContainer } from "~/features/events/EventListItem";

/**
 * The layer or data source an element source belongs to, or `undefined` when the source is in
 * neither of the user's membership lists. Data sources win, matching `getEditability`'s
 * precedence.
 */
export function getSelectionContainer(
    source: string,
    myLayers: readonly MyLayerInfo[],
    myDataSources: readonly MyDataSource[],
): EventListItemContainer | undefined {
    const dataSource = myDataSources.find((d) => d.serverId === source);
    if (dataSource) return { kind: "dataSource", name: dataSource.serverName };
    const layer = myLayers.find((l) => l.layerId === source);
    if (layer) return { kind: "layer", name: layer.layerName };
    return undefined;
}
