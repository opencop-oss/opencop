// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { Tooltip } from "../overlays/Tooltip.tsx";
import { DefaultCheckbox } from "../forms/DefaultCheckbox.tsx";
import type { DetailTableRow } from "./DetailTable.tsx";

export interface DefaultVisibleRowProps {
    checked: boolean;
    disabled: boolean;
    onCheckedChange: (checked: boolean) => void;
}

export function useDefaultVisibleRow({
    checked,
    disabled,
    onCheckedChange,
}: DefaultVisibleRowProps): DetailTableRow {
    const { t } = useTranslation();
    return {
        key: "defaultVisible",
        label: (
            <Tooltip content={t("common.defaultVisibleTooltip")} uppercase={false}>
                <span tabIndex={0} style={{ cursor: "help" }}>
                    {t("common.defaultVisible")}
                </span>
            </Tooltip>
        ),
        value: (
            <DefaultCheckbox
                checked={checked}
                label=""
                aria-label={t("common.defaultVisible")}
                disabled={disabled}
                onCheckedChange={onCheckedChange}
            />
        ),
    };
}
