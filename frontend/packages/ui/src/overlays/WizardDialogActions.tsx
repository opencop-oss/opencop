// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Dialog, Flex, Steps } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";

interface WizardDialogActionsProps {
    onClose: () => void;
    cancelLabel?: string;
    isFirstStep?: boolean;
    isLastStep: boolean;
    onNext?: () => void | Promise<void>;
    onSubmit: () => void;
    submitLabel?: string;
    loading?: boolean;
    loadingText?: string;
    /** Locks every wizard action while an in-flight operation must retain its result owner. */
    disabled?: boolean;
    /** Disables only the final submit, e.g. once its operation must not be repeated. */
    submitDisabled?: boolean;
}

export function WizardDialogActions({
    onClose,
    cancelLabel,
    isFirstStep = false,
    isLastStep,
    onNext,
    onSubmit,
    submitLabel,
    loading = false,
    loadingText,
    disabled = false,
    submitDisabled = false,
}: WizardDialogActionsProps) {
    const { t } = useTranslation();
    const resolvedCancelLabel = cancelLabel ?? t("common.cancel");
    const resolvedSubmitLabel = submitLabel ?? t("common.confirm");

    return (
        <Dialog.Footer justifyContent="space-between">
            <Button
                variant="subtle"
                colorPalette="secondary"
                size="sm"
                onClick={onClose}
                disabled={disabled}
            >
                {resolvedCancelLabel}
            </Button>
            <Flex gap={2}>
                {!isFirstStep && (
                    <Steps.PrevTrigger asChild>
                        <Button
                            variant="subtle"
                            colorPalette="secondary"
                            size="sm"
                            disabled={disabled}
                        >
                            {t("common.previous")}
                        </Button>
                    </Steps.PrevTrigger>
                )}
                {isLastStep ? (
                    <Button
                        colorPalette="brand"
                        size="sm"
                        onClick={onSubmit}
                        loading={loading}
                        loadingText={loadingText}
                        disabled={disabled || submitDisabled}
                    >
                        {resolvedSubmitLabel}
                    </Button>
                ) : onNext ? (
                    <Button colorPalette="brand" size="sm" onClick={onNext} disabled={disabled}>
                        {t("common.next")}
                    </Button>
                ) : (
                    <Steps.NextTrigger asChild>
                        <Button colorPalette="brand" size="sm" disabled={disabled}>
                            {t("common.next")}
                        </Button>
                    </Steps.NextTrigger>
                )}
            </Flex>
        </Dialog.Footer>
    );
}
