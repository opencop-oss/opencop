// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";
import type { Watchbox } from "./watchbox.model";

export type WatchboxDeleteDialogProps = {
    /** The watchbox pending deletion, or `null` when the dialog is closed. */
    watchbox: Watchbox | null;
    onClose: () => void;
    onConfirm: (watchbox: Watchbox) => void;
    loading?: boolean;
    /** Prevent confirmation while a delete mutation is already active. */
    disabled?: boolean;
    /** Element to focus after the dialog closes. */
    finalFocusEl?: () => HTMLElement | null;
};

export function WatchboxDeleteDialog({
    watchbox,
    onClose,
    onConfirm,
    loading = false,
    disabled = false,
    finalFocusEl,
}: WatchboxDeleteDialogProps) {
    const { t } = useTranslation();

    // WatchboxPanel owns both dismissal policy and the mutation; while a DELETE
    // is active it rejects close requests until the request settles.
    const handleConfirm = () => {
        if (!watchbox) return;
        // Closing on completion is the caller's job (WatchboxPanel), scoped to
        // whichever watchbox is still pending — a request that settles after the
        // user moved on to a different watchbox must not close it here.
        onConfirm(watchbox);
    };

    return (
        <ConfirmDialog
            open={watchbox !== null}
            onClose={onClose}
            onConfirm={handleConfirm}
            loading={loading}
            loadingText={t("common.saving")}
            dismissDuringLoading={false}
            disabled={disabled}
            title={t("watchbox.confirmDeleteTitle")}
            finalFocusEl={finalFocusEl}
            message={
                <Text>{t("watchbox.confirmDeleteMessage", { name: watchbox?.name ?? "" })}</Text>
            }
        />
    );
}
