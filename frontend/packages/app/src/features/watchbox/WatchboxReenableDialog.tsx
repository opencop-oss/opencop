// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { List, Text } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { ConfirmDialog } from "@opencop/ui";
import type { Watchbox } from "./watchbox.model";

/**
 * Reset reasons the frontend has copy for. `REENABLED` is the only one this MR can trigger (it
 * only ever patches `enabled`), but the backend's `confirmTrackingReset` gate is shared with a
 * future full-field-edit PATCH that can shrink coverage (polygon/layers/filter) and return other
 * reason codes — this map is deliberately future-proofed for those. Any reason not listed here
 * falls back to the raw string below rather than rendering blank.
 */
const reasonKey = {
    REENABLED: "watchbox.reenableReasons.REENABLED",
} as const;

export type WatchboxReenablePending = {
    watchbox: Watchbox;
    reasons: string[];
};

export type WatchboxReenableDialogProps = {
    pending: WatchboxReenablePending | null;
    onClose: () => void;
    onConfirm: () => void;
    loading?: boolean;
};

export function WatchboxReenableDialog({
    pending,
    onClose,
    onConfirm,
    loading = false,
}: WatchboxReenableDialogProps) {
    const { t } = useTranslation();

    return (
        <ConfirmDialog
            open={pending !== null}
            onClose={onClose}
            onConfirm={onConfirm}
            loading={loading}
            loadingText={t("common.saving")}
            dismissDuringLoading={false}
            isDestructive={false}
            confirmLabel={t("watchbox.confirmReenableAction")}
            title={t("watchbox.confirmReenableTitle")}
            message={
                <>
                    <Text>
                        {t("watchbox.confirmReenableIntro", {
                            name: pending?.watchbox.name ?? "",
                        })}
                    </Text>
                    <List.Root ps="4" mt="2">
                        {(pending?.reasons ?? []).map((reason) => (
                            <List.Item key={reason}>
                                {reason in reasonKey
                                    ? t(reasonKey[reason as keyof typeof reasonKey])
                                    : reason}
                            </List.Item>
                        ))}
                    </List.Root>
                </>
            }
        />
    );
}
