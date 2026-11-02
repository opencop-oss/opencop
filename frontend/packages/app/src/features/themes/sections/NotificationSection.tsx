// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, HStack, VStack } from "@chakra-ui/react";
import { ThemesSection } from "../ThemesSection";
import { ExampleSection } from "../ExampleSection";
import { useToast } from "~/features/notifications/use-toast.hook";

export function NotificationSection() {
    const { showToast } = useToast();

    return (
        <ThemesSection title="Notifications">
            <VStack align="start" gap="8" w="full">
                <ExampleSection
                    label="Toast Notifications"
                    code={`import { useToast } from "~/features/notifications/use-toast.hook";

const { showToast } = useToast();

showToast({
    title: "Success",
    description: "Operation completed successfully.",
    type: "success",
});

showToast({
    title: "Error",
    description: "Something went wrong.",
    type: "error",
});

showToast({
    title: "Warning",
    description: "Please check your input.",
    type: "warning",
});

showToast({
    title: "Info",
    description: "New data is available.",
    type: "info",
});`}
                >
                    <HStack gap="3" flexWrap="wrap">
                        <Button
                            colorPalette="success"
                            onClick={() =>
                                showToast({
                                    title: "Success",
                                    description: "Operation completed successfully.",
                                    type: "success",
                                })
                            }
                        >
                            Success Toast
                        </Button>
                        <Button
                            colorPalette="red"
                            onClick={() =>
                                showToast({
                                    title: "Error",
                                    description: "Something went wrong.",
                                    type: "error",
                                })
                            }
                        >
                            Error Toast
                        </Button>
                        <Button
                            colorPalette="warning"
                            onClick={() =>
                                showToast({
                                    title: "Warning",
                                    description: "Please check your input.",
                                    type: "warning",
                                })
                            }
                        >
                            Warning Toast
                        </Button>
                        <Button
                            colorPalette="accent"
                            onClick={() =>
                                showToast({
                                    title: "Info",
                                    description: "New data is available.",
                                    type: "info",
                                })
                            }
                        >
                            Info Toast
                        </Button>
                    </HStack>
                </ExampleSection>
            </VStack>
        </ThemesSection>
    );
}
