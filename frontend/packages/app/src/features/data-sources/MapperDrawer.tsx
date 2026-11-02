// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MapperProfile } from "@opencop/api";
import { SideDrawer } from "@opencop/ui";
import type { ReactElement } from "react";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { MapperDrawerContent } from "./detail/MapperDrawerContent";
import type { RelatedEntity } from "./data-resources.util";

export type MapperDrawerState = DrawerState<MapperProfile, "mapper">;

const NOTHING: RelatedEntity[] = [];

interface MapperDrawerProps {
    drawerState: MapperDrawerState;
    /** Data sources using each mapper, by its id. */
    usedBy: Map<string, RelatedEntity[]>;
    /** Archived data sources using each mapper, by its id; counted, not linked. */
    archivedUsedBy: Map<string, RelatedEntity[]>;
    /** The data sources request failed, so which sources use each row is unknown. */
    isUsageError: boolean;
}

/** Detail drawer for a mapper row. */
export function MapperDrawer({
    drawerState,
    usedBy,
    archivedUsedBy,
    isUsageError,
}: MapperDrawerProps): ReactElement {
    const { closeDrawer } = useViewDrawerContext();

    return (
        <DrawerContextProvider onClose={closeDrawer}>
            <SideDrawer open={drawerState.type !== "closed"} onClose={closeDrawer} size="lgx">
                {drawerState.type === "mapper" && (
                    <MapperDrawerContent
                        key={drawerState.entity.id}
                        mapper={drawerState.entity}
                        usedBy={usedBy.get(drawerState.entity.id) ?? NOTHING}
                        archived={archivedUsedBy.get(drawerState.entity.id) ?? NOTHING}
                        isUsageError={isUsageError}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
