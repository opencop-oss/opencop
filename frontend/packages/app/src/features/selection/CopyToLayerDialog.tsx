// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";
import { Box, Combobox, Dialog, Field, HStack, Text, VisuallyHidden } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import type { MyLayerInfo } from "@opencop/api";
import { BaseDialog, DialogActions, DialogHeader, OcCombobox } from "@opencop/ui";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";

interface LayerOption {
    label: string;
    value: string;
    accessScope: MyLayerInfo["accessScope"];
}

interface CopyToLayerDialogProps {
    open: boolean;
    onClose: () => void;
    /** Layers the user may copy into. */
    targetLayers: readonly MyLayerInfo[];
    /**
     * How many checked elements the copy covered when the dialog opened. Frozen so it doesn't
     * shrink as a copy unticks items; the result toast reports what was actually sent, so the
     * two can differ by an item or two if one expires and is evicted before Confirm.
     */
    count: number;
    /** How many of those are past their stale time and will be skipped (rare; see `isStaleForCopy`). */
    staleCount: number;
    /**
     * How many of the non-stale checked elements sit on each layer, as of when the dialog opened.
     * Those on the chosen destination are skipped rather than duplicated.
     */
    freshCountByLayer: Readonly<Record<string, number>>;
    /**
     * Why the copy is no longer allowed, when permissions change while the dialog is open.
     * Disables Confirm and is shown in the dialog, so a confirm that would do nothing can't be
     * pressed.
     */
    disabledReason?: string;
    isCopying: boolean;
    onConfirm: (target: MyLayerInfo) => void;
    /** Element to focus after the dialog closes. Falls back to the opening control. */
    finalFocusEl?: () => HTMLElement | null;
}

/**
 * Picks the layer the checked elements are copied into, and confirms the copy. The chosen layer
 * is local state, so the caller gives each opening a fresh `key` to start from no choice.
 */
export function CopyToLayerDialog({
    open,
    onClose,
    targetLayers,
    count,
    staleCount,
    freshCountByLayer,
    disabledReason,
    isCopying,
    onConfirm,
    finalFocusEl,
}: CopyToLayerDialogProps) {
    const { t } = useTranslation();
    const [targetId, setTargetId] = useState("");

    const options: LayerOption[] = targetLayers.map((layer) => ({
        label: layer.layerName,
        value: layer.layerId,
        accessScope: layer.accessScope,
    }));
    const target = targetLayers.find((layer) => layer.layerId === targetId);
    const sameLayerCount = target ? (freshCountByLayer[target.layerId] ?? 0) : 0;
    const sameLayerNote =
        target && sameLayerCount > 0
            ? t("selectionPanel.copyDialogSameLayerNote", {
                  count: sameLayerCount,
                  layer: target.layerName,
              })
            : "";

    const handleClose = () => {
        if (isCopying) return;
        onClose();
    };

    return (
        <BaseDialog
            open={open}
            onClose={handleClose}
            variant="admin"
            size="sm"
            finalFocusEl={finalFocusEl}
        >
            <DialogHeader title={t("selectionPanel.copyDialogTitle")} />

            <Dialog.Body>
                <Text>{t("selectionPanel.copyDialogMessage", { count })}</Text>
                <Text mt={2} fontSize="sm" color="fg.muted">
                    {t("selectionPanel.copyDialogLimitationNote")}
                </Text>
                {/* Always mounted: a live region only reliably announces a change inside a node
                    that is already in the DOM, and the notes below mount with their text in place.
                    Picking a destination or losing permission otherwise disables Confirm silently. */}
                <VisuallyHidden role="status">{disabledReason ?? sameLayerNote}</VisuallyHidden>
                {disabledReason !== undefined && (
                    <Text mt={2} fontSize="sm" color="fg.error">
                        {disabledReason}
                    </Text>
                )}
                {staleCount > 0 && (
                    <Text mt={2} fontSize="sm" color="fg.warning">
                        {t("selectionPanel.copyDialogStaleNote", { count: staleCount })}
                    </Text>
                )}
                <Field.Root mt={4}>
                    <Field.Label>{t("selectionPanel.copyTargetLabel")}</Field.Label>
                    <OcCombobox
                        items={options}
                        value={targetId}
                        onValueChange={(value) => setTargetId(Array.isArray(value) ? "" : value)}
                        placeholder={t("selectionPanel.copyTargetPlaceholder")}
                        emptyMessage={t("common.noResults")}
                        disabled={isCopying}
                        renderItem={(item) => (
                            <HStack w="full" gap="2">
                                {/* A fixed-width slot, so layer names line up whether or not the layer shows the icon. */}
                                <Box
                                    display="inline-flex"
                                    justifyContent="center"
                                    w="14px"
                                    flexShrink={0}
                                >
                                    <ScopeIndicator scope={item.accessScope} iconOnly />
                                </Box>
                                <Combobox.ItemText>{item.label}</Combobox.ItemText>
                            </HStack>
                        )}
                    />
                </Field.Root>
                {sameLayerNote && (
                    <Text mt={2} fontSize="sm" color="fg.warning">
                        {sameLayerNote}
                    </Text>
                )}
            </Dialog.Body>

            <DialogActions
                onClose={handleClose}
                closeDisabled={isCopying}
                onAction={() => target && onConfirm(target)}
                actionLabel={t("selectionPanel.copyConfirm")}
                loading={isCopying}
                loadingText={t("selectionPanel.copyConfirm")}
                disabled={
                    !target ||
                    count - staleCount - sameLayerCount <= 0 ||
                    disabledReason !== undefined
                }
            />
        </BaseDialog>
    );
}
