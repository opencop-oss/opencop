// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import type { NamedExportSubject } from "~/features/exports/exports.util";
import { LayerDrawerContent } from "./detail/LayerDrawerContent";

export type LayerDrawerState = DrawerState<MyLayerInfo, "layer">;

export type LayerModalState =
    | { type: "none" }
    | { type: "addLayer" }
    | { type: "deleteLayer"; layer: MyLayerInfo }
    | { type: "importFile"; layerId: string }
    | { type: "duplicateLayer"; layer: MyLayerInfo }
    | { type: "exportLayers"; subjects: NamedExportSubject[] };

interface LayerDrawerProps {
    drawerState: LayerDrawerState;
}

export function LayerDrawer({ drawerState }: LayerDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<LayerModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "layer" && (
                    <LayerDrawerContent
                        key={drawerState.entity.layerId}
                        layer={drawerState.entity}
                        onOpenModal={setModal}
                        isArchived={!!drawerState.entity.archivedAt}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
