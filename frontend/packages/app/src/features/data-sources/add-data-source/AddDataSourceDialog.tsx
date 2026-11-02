// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Dialog, Steps, useBreakpointValue } from "@chakra-ui/react";
import type { ConnectionCreatedV2Response, KafkaCredentials, TransportType } from "@opencop/api";
import { BaseDialog, ConfirmDialog, DialogHeader, WizardDialogActions } from "@opencop/ui";
import { useMemo, useState } from "react";
import { FormProvider } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useFeatureFlagEnabled } from "~/features/admin/runtime-features/use-feature-flag.hook";

// Step Components
import { ConnectionStep } from "../connection-setup/ConnectionStep";
import { IntegrationStep } from "../connection-setup/IntegrationStep";
import { MappingStep } from "../connection-setup/MappingStep";
import { ConnectionReviewStep } from "../connection-setup/ConnectionReviewStep";
import {
    useConnectionSetup,
    type SetupStepId,
} from "../connection-setup/use-connection-setup.hook";
import { KafkaCredentialsPanel } from "./KafkaCredentialsPanel";
import { StepDataFlow } from "./StepDataFlow";
import { WizardStepper } from "./WizardStepper";

/** Steps of every transport: Data Flow picks the transport, then the integration-driven flow. */
const WIZARD_STEPS = [
    "dataFlow",
    "integration",
    "connection",
    "mapping",
    "review",
] as const satisfies readonly ("dataFlow" | SetupStepId)[];

interface DialogAddDataSourceProps {
    open: boolean;
    onClose: () => void;
}

export function AddDataSourceDialog({ open, onClose }: DialogAddDataSourceProps) {
    const { t } = useTranslation();
    const rtspEnabled = useFeatureFlagEnabled("rtsp");
    const [transportType, setTransportType] = useState<TransportType>("TCP");
    const [step, setStep] = useState(0);
    const [kafkaCredentials, setKafkaCredentials] = useState<KafkaCredentials | null>(null);
    const [confirmCloseOpen, setConfirmCloseOpen] = useState(false);

    const handleCreated = (created: ConnectionCreatedV2Response) => {
        if (created.kafkaCredentials) {
            setKafkaCredentials(created.kafkaCredentials);
            return;
        }
        onClose();
    };
    const connectionSetup = useConnectionSetup({
        protocol: transportType,
        goToStep: (id) => setStep(WIZARD_STEPS.indexOf(id)),
        onCreated: handleCreated,
    });

    /** Integrations, extractors and mappers differ per protocol, so a new transport starts over. */
    const chooseTransport = (selected: TransportType) => {
        connectionSetup.startOver();
        setTransportType(selected);
    };

    const stepTitles = useMemo<Record<string, string>>(
        () => ({
            dataFlow: t("admin.dataFlow"),
            integration: t("connectionSetup.integration"),
            connection: t("admin.connection"),
            mapping: t("admin.mapping"),
            review: t("admin.review"),
        }),
        [t],
    );

    // Vertical beside the form where there is room; horizontal above it on a narrow screen.
    const orientation =
        useBreakpointValue({ base: "horizontal", md: "vertical" } as const) ?? "vertical";

    const steps = useMemo(
        () => WIZARD_STEPS.map((id) => ({ id, title: stepTitles[id] })),
        [stepTitles],
    );

    /** Data Flow always holds a transport; the integration-driven steps validate their own form. */
    const validateStepAt = (stepIndex: number): Promise<boolean> => {
        const id = WIZARD_STEPS[stepIndex];
        return id === "dataFlow" ? Promise.resolve(true) : connectionSetup.validateStep(id);
    };

    const isLastStep = step === steps.length - 1;
    const isFirstStep = step === 0;

    const closeDialog = () => {
        setKafkaCredentials(null);
        setConfirmCloseOpen(false);
        setStep(0);
        onClose();
    };

    const creating = connectionSetup.isPending;

    const requestClose = () => {
        if (creating) return;
        if (kafkaCredentials) {
            setConfirmCloseOpen(true);
            return;
        }
        closeDialog();
    };

    return (
        <>
            <BaseDialog
                open={open}
                onClose={requestClose}
                variant="admin"
                size="xl"
                contentWidth="1100px"
            >
                {kafkaCredentials ? (
                    <KafkaCredentialsPanel credentials={kafkaCredentials} onClose={requestClose} />
                ) : (
                    <Steps.Root
                        colorPalette="brand"
                        step={step}
                        onStepChange={async (details) => {
                            // While Confirm is held, its reason must stay beside it.
                            if (!connectionSetup.createdWithoutCredentials) {
                                connectionSetup.clearErrorMessage();
                            }
                            if (details.step > step) {
                                for (
                                    let stepToValidate = step;
                                    stepToValidate < details.step;
                                    stepToValidate += 1
                                ) {
                                    const isValid = await validateStepAt(stepToValidate);
                                    if (!isValid) {
                                        setStep(stepToValidate);
                                        return;
                                    }
                                }
                            }
                            setStep(details.step);
                        }}
                        count={steps.length}
                        orientation={orientation}
                        display="flex"
                        flexDirection="column"
                        flex="1"
                        minH="0"
                    >
                        <DialogHeader
                            title={t("admin.dataSource")}
                            secondaryText={t("admin.addNewDataSource")}
                        />

                        <Dialog.Body
                            flex="0 1 352px"
                            minH="0"
                            display="flex"
                            flexDirection={orientation === "vertical" ? "row" : "column"}
                            gap={orientation === "vertical" ? 6 : 3}
                            overflow="hidden"
                            pt={4}
                        >
                            {/* A dispatched creation cannot be recalled and its outcome renders
                                only on Review, so leaving that step would hide the eventual
                                failure. */}
                            <WizardStepper
                                steps={steps}
                                orientation={orientation}
                                failedStepIds={connectionSetup.failedSteps}
                                disabled={creating}
                            />
                            {/* One height on every step; the form scrolls what
                                does not fit beside the stepper. */}
                            <Box flex="1" minW="0" position="relative">
                                <Box
                                    position="absolute"
                                    inset={0}
                                    overflowY="auto"
                                    overflowX="hidden"
                                    pr={1}
                                >
                                    <FormProvider {...connectionSetup.form}>
                                        {steps.map((stepItem, index) => (
                                            <Box
                                                key={stepItem.id}
                                                display={step === index ? "block" : "none"}
                                                w="full"
                                            >
                                                {stepItem.id === "dataFlow" && (
                                                    <StepDataFlow
                                                        transportType={transportType}
                                                        onChange={chooseTransport}
                                                        rtspEnabled={rtspEnabled}
                                                    />
                                                )}
                                                {stepItem.id === "integration" && (
                                                    <IntegrationStep setup={connectionSetup} />
                                                )}
                                                {stepItem.id === "connection" && (
                                                    <ConnectionStep
                                                        protocol={connectionSetup.protocol}
                                                        extractors={connectionSetup.extractors}
                                                        extractor={connectionSetup.extractor}
                                                    />
                                                )}
                                                {stepItem.id === "mapping" && (
                                                    <MappingStep setup={connectionSetup} />
                                                )}
                                                {stepItem.id === "review" && (
                                                    <ConnectionReviewStep setup={connectionSetup} />
                                                )}
                                            </Box>
                                        ))}
                                    </FormProvider>
                                </Box>
                            </Box>
                        </Dialog.Body>

                        {/* Custom footer with per-step validation on Next */}
                        <WizardDialogActions
                            onClose={requestClose}
                            isFirstStep={isFirstStep}
                            isLastStep={isLastStep}
                            onSubmit={() => void connectionSetup.submit()}
                            loading={creating}
                            loadingText={t("common.saving")}
                            disabled={creating}
                            submitDisabled={connectionSetup.createdWithoutCredentials}
                            onNext={async () => {
                                const isValid = await validateStepAt(step);
                                if (isValid) {
                                    setStep(step + 1);
                                }
                            }}
                        />
                    </Steps.Root>
                )}
            </BaseDialog>
            <ConfirmDialog
                open={confirmCloseOpen}
                onClose={() => setConfirmCloseOpen(false)}
                onConfirm={closeDialog}
                title={t("admin.kafkaCredentialsCloseTitle")}
                message={t("admin.kafkaCredentialsCloseMessage")}
                confirmLabel={t("admin.kafkaCredentialsCloseConfirm")}
                cancelLabel={t("common.cancel")}
                isDestructive={false}
            />
        </>
    );
}
