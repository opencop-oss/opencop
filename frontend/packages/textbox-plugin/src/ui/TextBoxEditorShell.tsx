// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Heading, IconButton, Spacer, VStack } from "@chakra-ui/react";
import { Panel, Tooltip } from "@opencop/ui";
import { type ReactElement, type ReactNode, useCallback, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuChevronDown, LuChevronRight } from "react-icons/lu";

interface TextBoxEditorShellProps {
    title: string;
    children: ReactNode;
}

/** Collapsible chrome. No auto-hide-on-leave: it holds a focused text field. */
export function TextBoxEditorShell({ title, children }: TextBoxEditorShellProps): ReactElement {
    const { t } = useTranslation(["translation", "textbox"]);
    const [open, setOpen] = useState(true);
    const bodyId = useId();
    const toggle = useCallback(() => setOpen((previous) => !previous), []);

    return (
        <Panel
            w="xs"
            h={open ? "auto" : "8"}
            alignSelf="start"
            overflow="hidden"
            pointerEvents="auto"
        >
            <Flex
                bg="bg.elevated"
                h="8"
                w="full"
                alignItems="center"
                px="3"
                flexShrink={0}
                borderBottomWidth={open ? "1px" : "0"}
                cursor="pointer"
                onClick={toggle}
            >
                <Heading size="sm" letterSpacing="1px">
                    {title}
                </Heading>
                <Spacer />
                <Tooltip
                    content={
                        open ? t("textbox:editorPanel.collapse") : t("textbox:editorPanel.expand")
                    }
                >
                    <IconButton
                        aria-expanded={open}
                        aria-controls={bodyId}
                        aria-label={
                            open
                                ? t("textbox:editorPanel.collapse")
                                : t("textbox:editorPanel.expand")
                        }
                        variant="plain"
                        size="xs"
                        onClick={(event) => {
                            event.stopPropagation();
                            toggle();
                        }}
                    >
                        {open ? <LuChevronDown /> : <LuChevronRight />}
                    </IconButton>
                </Tooltip>
            </Flex>

            {open && (
                <Box id={bodyId} display="flex" flexDirection="column">
                    <VStack align="stretch" w="full" gap="4" p="4">
                        {children}
                    </VStack>
                </Box>
            )}
        </Panel>
    );
}
