// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, Input } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { FormDialog } from "@opencop/ui";

interface FavoriteLabelDialogProps {
    open: boolean;
    onClose: () => void;
    onSubmit: (label: string) => void;
    label: string;
    onLabelChange: (label: string) => void;
    historyKeyPrefix: "symbology:symbolHistory";
}

export function FavoriteLabelDialog({
    open,
    onClose,
    onSubmit,
    label,
    onLabelChange,
    historyKeyPrefix,
}: FavoriteLabelDialogProps) {
    const { t } = useTranslation(["translation", "symbology"]);

    const handleSubmit = () => {
        onSubmit(label);
    };

    return (
        <FormDialog
            open={open}
            onClose={onClose}
            onSubmit={handleSubmit}
            title={t("symbology:symbolHistory.saveFavorite")}
            submitLabel={t("common.save")}
            size="sm"
        >
            <Field.Root>
                <Field.Label>{t("symbology:symbolHistory.labelOptional")}</Field.Label>
                <Input
                    value={label}
                    onChange={(e) => onLabelChange(e.target.value)}
                    placeholder={t(`${historyKeyPrefix}.enterLabel`)}
                    autoFocus
                />
                <Field.HelperText>{t(`${historyKeyPrefix}.labelHelper`)}</Field.HelperText>
            </Field.Root>
        </FormDialog>
    );
}
