// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Textarea, VStack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "@opencop/ui";
import { useToast } from "~/features/notifications/use-toast.hook";

export function CreateRemark({
    addRemark,
}: {
    addRemark: ({ content }: { content: string }) => void;
}) {
    const { t } = useTranslation();
    const { showToast } = useToast();
    const [text, setText] = useState("");

    return (
        <VStack align="end" w="full">
            <Textarea
                borderRadius="4px"
                placeholder={t("events.addRemarkPlaceholder")}
                minLength={1}
                value={text}
                onChange={(v) => setText(v.currentTarget.value)}
            />
            <Tooltip
                content={t("events.addRemarkDisabledTooltip", {
                    defaultValue: "enter text to add a remark",
                })}
                disabled={text.length > 0}
            >
                <Box as="span" display="inline-block">
                    <Button
                        active={"full"}
                        onClick={async () => {
                            try {
                                await addRemark({ content: text });
                                showToast({
                                    type: "success",
                                    title: t("events.addRemarkPosted"),
                                });
                            } catch {
                                showToast({
                                    type: "error",
                                    title: t("events.addRemarkFailed"),
                                });
                            }
                            setText("");
                        }}
                        w="fit-content"
                        disabled={text.length === 0}
                    >
                        {t("events.addRemark").toUpperCase()}
                    </Button>
                </Box>
            </Tooltip>
        </VStack>
    );
}
