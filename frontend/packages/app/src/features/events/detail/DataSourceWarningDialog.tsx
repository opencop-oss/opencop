// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";
import type { ReactElement } from "react";

type DataSourceWarningKey =
    | "eventDelete.dataSourceWarningTitle"
    | "events.dataSourceEditWarningTitle"
    | "eventDelete.dataSourceWarningMessage"
    | "events.dataSourceEditWarningMessage";

export function DataSourceWarningDialog({
    open,
    onClose,
    onConfirm,
    titleKey = "eventDelete.dataSourceWarningTitle",
    messageKey = "eventDelete.dataSourceWarningMessage",
    confirmLabel,
    isDestructive = true,
}: {
    open: boolean;
    onClose: () => void;
    onConfirm: () => void;
    titleKey?: Extract<DataSourceWarningKey, `${string}Title`>;
    messageKey?: Extract<DataSourceWarningKey, `${string}Message`>;
    confirmLabel?: string;
    isDestructive?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    return (
        <ConfirmDialog
            open={open}
            onClose={onClose}
            onConfirm={onConfirm}
            title={t(titleKey as never)}
            message={t(messageKey as never)}
            confirmLabel={confirmLabel}
            isDestructive={isDestructive}
        />
    );
}
