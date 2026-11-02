// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { TFunction } from "i18next";
import { FaTrash } from "react-icons/fa";
import type { ToolbarAction } from "@opencop/ui";

export function buildBulkDeleteAction(
    count: number,
    onDelete: () => void,
    t: TFunction,
): ToolbarAction[] {
    return [
        {
            label: t("common.delete"),
            icon: <FaTrash />,
            disabled: count === 0,
            onClick: onDelete,
            colorPalette: "red",
            confirm: {
                title: t("resourceTable.confirmDeleteTitle"),
                message: t("resourceTable.confirmDeleteMessage", { count }),
            },
        },
    ];
}
