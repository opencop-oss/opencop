// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { DataSourceInfo } from "@opencop/api";
import { useState, type ReactElement } from "react";
import { SideDrawer } from "@opencop/ui";
import { DrawerContextProvider } from "~/features/drawers/DrawerContext";
import { useViewDrawerContext } from "~/features/drawers/ViewDrawerContext";
import { DataSourceDrawerContent } from "./detail/DataSourceDrawerContent";

export type DataSourceDrawerState = { type: "closed" } | { type: "source"; entity: DataSourceInfo };

export type ModalState =
    | { type: "none" }
    | { type: "addDataSource" }
    | { type: "deleteSource"; dataSource: DataSourceInfo };

interface DataSourceDrawerProps {
    drawerState: DataSourceDrawerState;
    mapperNames: Map<string, string>;
}

interface SourceDrawerContentProps {
    dataSource: DataSourceInfo;
    onOpenModal: (modal: ModalState) => void;
    mapperNames: Map<string, string>;
}

function SourceDrawerContent({
    dataSource,
    onOpenModal,
    mapperNames,
}: SourceDrawerContentProps): ReactElement {
    return (
        <DataSourceDrawerContent
            dataSource={dataSource}
            onOpenModal={onOpenModal}
            mapperNames={mapperNames}
        />
    );
}

export function DataSourceDrawer({
    drawerState,
    mapperNames,
}: DataSourceDrawerProps): ReactElement | null {
    const { closeDrawer, setModal } = useViewDrawerContext<ModalState>();
    const [busy, setBusy] = useState(false);

    return (
        <DrawerContextProvider onClose={closeDrawer} setBusy={setBusy}>
            <SideDrawer
                open={drawerState.type !== "closed"}
                onClose={closeDrawer}
                size="lgx"
                closeDisabled={busy}
            >
                {drawerState.type === "source" && (
                    <SourceDrawerContent
                        key={drawerState.entity.id}
                        dataSource={drawerState.entity}
                        onOpenModal={setModal}
                        mapperNames={mapperNames}
                    />
                )}
            </SideDrawer>
        </DrawerContextProvider>
    );
}
