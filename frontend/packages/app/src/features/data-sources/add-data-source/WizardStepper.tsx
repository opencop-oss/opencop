// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Steps, VisuallyHidden } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { LuCheck, LuTriangleAlert } from "react-icons/lu";

export interface WizardStepperStep {
    id: string;
    title: string;
}

interface WizardStepperProps {
    steps: readonly WizardStepperStep[];
    orientation: "horizontal" | "vertical";
    /** Steps holding a problem the server reported; they show a warning until it is fixed. */
    failedStepIds?: readonly string[];
    /** Locks every step, as while a dispatched creation is in flight. */
    disabled?: boolean;
}

/**
 * The wizard's step list, rendered inside a `Steps.Root`: a filled check for completed steps, a
 * ring with a dot for the current one, an empty ring for those ahead, and a warning for a failed
 * one. Vertical beside the form, with a divider; horizontal above it, where only the current step
 * keeps its title so the row fits a narrow screen.
 */
export function WizardStepper({
    steps,
    orientation,
    failedStepIds = [],
    disabled = false,
}: WizardStepperProps) {
    const { t } = useTranslation();
    const vertical = orientation === "vertical";

    return (
        <Steps.List
            flexShrink={0}
            h="fit-content"
            css={{
                "--steps-size": "sizes.5",
                "--steps-icon-size": "sizes.3",
                "--steps-gutter": vertical ? "spacing.1" : "spacing.2",
            }}
            pr={vertical ? 6 : 0}
            pb={vertical ? 0 : 3}
            borderRightWidth={vertical ? "1px" : "0"}
            borderBottomWidth={vertical ? "0" : "1px"}
            borderColor="border"
        >
            {steps.map((step, index) => {
                const failed = failedStepIds.includes(step.id);
                return (
                    <Steps.Item
                        key={step.id}
                        index={index}
                        title={step.title}
                        minH={vertical ? 14 : undefined}
                    >
                        <Steps.Trigger disabled={disabled}>
                            {failed ? (
                                <Steps.Indicator
                                    borderWidth="0"
                                    bg="transparent"
                                    color="fg.error"
                                    _current={{ bg: "transparent", borderWidth: "0" }}
                                    _complete={{ bg: "transparent", borderWidth: "0" }}
                                    _incomplete={{ borderWidth: "0" }}
                                    css={{ "--steps-icon-size": "sizes.5" }}
                                >
                                    <LuTriangleAlert />
                                </Steps.Indicator>
                            ) : (
                                <Steps.Indicator
                                    borderRadius="full"
                                    _incomplete={{ borderColor: "fg.muted" }}
                                    _current={{ bg: "transparent" }}
                                >
                                    <Steps.Status
                                        complete={<LuCheck strokeWidth={3} />}
                                        current={
                                            <Box
                                                boxSize={2}
                                                borderRadius="full"
                                                bg="colorPalette.solid"
                                            />
                                        }
                                        // `null` would fall back to `current`.
                                        incomplete={false}
                                    />
                                </Steps.Indicator>
                            )}
                            <Steps.Title
                                fontWeight="semibold"
                                color={failed ? "fg.error" : "fg"}
                                css={{
                                    ...(failed
                                        ? {}
                                        : {
                                              "[data-current] > &": { color: "colorPalette.fg" },
                                              "[data-incomplete] > &": { color: "fg.muted" },
                                          }),
                                    ...(vertical
                                        ? {}
                                        : {
                                              "[data-complete] > &, [data-incomplete] > &": {
                                                  srOnly: true,
                                              },
                                          }),
                                }}
                            >
                                {step.title}
                                {failed && (
                                    <VisuallyHidden>{` ${t("connectionSetup.stepFailed")}`}</VisuallyHidden>
                                )}
                            </Steps.Title>
                        </Steps.Trigger>
                        <Steps.Separator bg="fg.muted" _complete={{ bg: "colorPalette.solid" }} />
                    </Steps.Item>
                );
            })}
        </Steps.List>
    );
}
