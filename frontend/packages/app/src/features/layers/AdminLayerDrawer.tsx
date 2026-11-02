// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerInfo } from "@opencop/api";
import type { ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import type { NamedExportSubject } from "~/features/exports/exports.util";
import { AdminLayerDrawerContent } from "./detail/AdminLayerDrawerContent";

export type AdminLayerDrawerState = DrawerState<LayerInfo, "layer">;

export type AdminLayerModalState =
    | { type: "none" }
    | { type: "addLayer" }
    | { type: "deleteLayer"; layer: LayerInfo }
    | { type: "duplicateLayer"; layer: LayerInfo }
    | { type: "exportLayers"; subjects: NamedExportSubject[] }
    | { type: "importFile"; layerId: string };

interface AdminLayerDrawerProps {
    drawerState: AdminLayerDrawerState;
}

export function AdminLayerDrawer({ drawerState }: AdminLayerDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<AdminLayerModalState>();
    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "layer" && (
                    <AdminLayerDrawerContent
                        key={drawerState.entity.id}
                        layer={drawerState.entity}
                        onOpenModal={setModal}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
