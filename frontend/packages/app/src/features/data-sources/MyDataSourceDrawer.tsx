// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyDataSource } from "@opencop/api";
import { useState, type ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import type { DrawerState } from "~/features/drawers/drawer-state.model";
import { MyDataSourceDrawerContent } from "./detail/MyDataSourceDrawerContent";

export type MyDataSourceDrawerState = DrawerState<MyDataSource, "dataSource">;

export type MyDataSourceModalState =
    | { type: "none" }
    | { type: "addDataSource" }
    | { type: "deleteDataSource"; dataSource: MyDataSource };

interface MyDataSourceDrawerProps {
    drawerState: MyDataSourceDrawerState;
    showArchived: boolean;
}

interface DataSourceContentProps {
    dataSource: MyDataSource;
    onOpenModal: (modal: MyDataSourceModalState) => void;
    showArchived: boolean;
}

function DataSourceContent({
    dataSource,
    onOpenModal,
    showArchived,
}: DataSourceContentProps): ReactElement {
    // The archived listing requests `recordVisibility=ARCHIVED`, so every row it returns is
    // archived. `SourceInfo` does not serialize `archivedAt`, so the row on its own can never
    // prove lifecycle state here; the listing the row came from is the authoritative signal.
    const isArchived = showArchived || !!dataSource.archivedAt;

    return (
        <MyDataSourceDrawerContent
            dataSource={dataSource}
            onOpenModal={onOpenModal}
            isArchived={isArchived}
        />
    );
}

export function MyDataSourceDrawer({
    drawerState,
    showArchived,
}: MyDataSourceDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<MyDataSourceModalState>();
    const [busy, setBusy] = useState(false);

    return (
        <DrawerContextProvider onClose={closeDrawer} setBusy={setBusy}>
            <SideDrawer
                open={drawerState.type !== "closed"}
                onClose={closeDrawer}
                size="lgx"
                closeDisabled={busy}
            >
                {drawerState.type === "dataSource" && (
                    <DataSourceContent
                        key={drawerState.entity.serverId}
                        dataSource={drawerState.entity}
                        onOpenModal={setModal}
                        showArchived={showArchived}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
