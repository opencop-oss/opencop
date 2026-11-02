// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, HStack, Input, type InputProps } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useDisplayPreferences } from "../preferences/DisplayPreferencesContext.tsx";
import { CoordFormatSelect } from "./CoordFormatSelect.tsx";

export interface CoordinateInputFieldProps extends InputProps {
    label: string;
    error?: boolean;
    showFormatSelector?: boolean;
}

/**
 * A labelled coordinate input, with an optional format picker that changes the
 * operator's coordinate format. Pair it with {@link useCoordinateInput}.
 */
export function CoordinateInputField({
    label,
    error,
    showFormatSelector = false,
    ...inputProps
}: CoordinateInputFieldProps) {
    const { t } = useTranslation();
    const { coordFormat, setCoordFormat } = useDisplayPreferences();

    return (
        <Field.Root w="full" invalid={error}>
            <Field.Label color="fg.muted" textStyle="xs">
                {label}
            </Field.Label>
            <HStack w="full" alignItems="stretch">
                <Input flex="1" {...inputProps} placeholder={t("coordinates.placeholder")} />
                {showFormatSelector && (
                    <CoordFormatSelect
                        value={coordFormat}
                        onChange={setCoordFormat}
                        variant="inline"
                    />
                )}
            </HStack>
            {error && <Field.ErrorText>{t("coordinates.invalidCoordinate")}</Field.ErrorText>}
        </Field.Root>
    );
}
