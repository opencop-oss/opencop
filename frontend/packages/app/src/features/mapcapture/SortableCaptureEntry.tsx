// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SortableLayerEntry, type SortableLayerEntryProps } from "@opencop/ui";
import { useTranslation } from "react-i18next";

type SortableCaptureEntryProps = Omit<
    SortableLayerEntryProps,
    "dragAriaLabel" | "moveUpAriaLabel" | "moveDownAriaLabel" | "handleBorderWidth"
> & { captureName: string };

export function SortableCaptureEntry({ captureName, ...props }: SortableCaptureEntryProps) {
    const { t } = useTranslation();
    return (
        <SortableLayerEntry
            {...props}
            // ListPanelItem draws its own border; suppress the handle divider
            handleBorderWidth="0"
            dragAriaLabel={t("mapCapture.dragToReorder", { name: captureName })}
            moveUpAriaLabel={t("mapCapture.moveUp", { name: captureName })}
            moveDownAriaLabel={t("mapCapture.moveDown", { name: captureName })}
        />
    );
}
