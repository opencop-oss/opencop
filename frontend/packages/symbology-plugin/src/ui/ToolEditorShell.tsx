// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Heading, IconButton, Spacer, VStack } from "@chakra-ui/react";
import { Panel, Tooltip } from "@opencop/ui";
import { usePluginContext } from "@opencop/contributions/plugin";
import { type ReactElement, type ReactNode, useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuChevronDown, LuChevronRight, LuPin, LuPinOff } from "react-icons/lu";
import { Preview } from "./Preview";

interface ToolEditorShellProps {
    title: string;
    children: ReactNode;
    headerActions?: ReactNode;
    showPreview?: boolean;
}

const FADE_MS = 150;
const ROLL_MS = 300;

/**
 * Auto-hide preference, persisted through the plugin's host-provided storage
 * (host-namespaced; the raw name needs no prefix of its own).
 */
export const AUTO_HIDE_STORAGE_KEY = "tool-editor-auto-hide";
/** Pre-plugin raw-localStorage location; read only by history-migration.ts. */
export const LEGACY_KEY_AUTO_HIDE = "tool-editor-auto-hide";

// Collapse phases: "open" → "fading" → "closing" → "closed"
// Expand: "closed" → "open" (instant)
type Phase = "open" | "fading" | "closing" | "closed";

/**
 * Shared collapsible panel shell for symbol and graphic tool editors.
 *
 * Collapses when mouse leaves the panel, expands when mouse enters the title bar.
 * Manual toggle via title bar click or chevron button.
 *
 * Collapse animation: opacity fade out, then panel height transition to title bar.
 * Expand: instant.
 */
export function ToolEditorShell({
    title,
    children,
    headerActions,
    showPreview = true,
}: ToolEditorShellProps): ReactElement {
    const { t } = useTranslation(["translation", "symbology"]);
    const { storage } = usePluginContext();
    const [phase, setPhase] = useState<Phase>("open");
    const [autoHide, setAutoHide] = useState(
        () => storage.get<boolean>(AUTO_HIDE_STORAGE_KEY) ?? false,
    );

    const isOpen = phase === "open";

    const toggleAutoHide = useCallback(() => {
        setAutoHide((prev) => {
            const next = !prev;
            storage.set(AUTO_HIDE_STORAGE_KEY, next);
            return next;
        });
    }, [storage]);

    const startCollapse = useCallback(() => {
        if (phase !== "open") return;
        setPhase("fading");
        setTimeout(() => {
            setPhase("closing");
            setTimeout(() => setPhase("closed"), ROLL_MS);
        }, FADE_MS);
    }, [phase]);

    const expand = useCallback(() => {
        if (phase !== "closed") return;
        setPhase("open");
    }, [phase]);

    const toggle = useCallback(() => {
        if (phase === "open") {
            startCollapse();
        } else if (phase === "closed") {
            expand();
        }
    }, [phase, startCollapse, expand]);

    return (
        <Panel
            w="md"
            h={phase === "closing" || phase === "closed" ? "8" : "full"}
            alignSelf={isOpen ? undefined : "start"}
            overflow="hidden"
            pointerEvents="auto"
            transition={phase === "closing" ? `height ${ROLL_MS}ms ease-out` : undefined}
            onMouseLeave={autoHide ? startCollapse : undefined}
        >
            {/* Title bar */}
            <Flex
                bg="bg.elevated"
                h="8"
                w="full"
                alignItems="center"
                px="3"
                flexShrink={0}
                borderBottomWidth={isOpen ? "1px" : "0"}
                cursor="pointer"
                onClick={toggle}
                onMouseEnter={autoHide ? expand : undefined}
            >
                <Heading size="sm" letterSpacing="1px">
                    {title}
                </Heading>
                <Spacer />
                {headerActions}
                <Tooltip
                    content={
                        autoHide ? t("symbology:editorPanel.pin") : t("symbology:editorPanel.unpin")
                    }
                >
                    <IconButton
                        aria-label={
                            autoHide
                                ? t("symbology:editorPanel.pin")
                                : t("symbology:editorPanel.unpin")
                        }
                        variant="plain"
                        size="xs"
                        onClick={(e) => {
                            e.stopPropagation();
                            toggleAutoHide();
                        }}
                    >
                        {autoHide ? <LuPinOff /> : <LuPin />}
                    </IconButton>
                </Tooltip>
                <IconButton
                    aria-label={
                        isOpen
                            ? t("symbology:editorPanel.collapse")
                            : t("symbology:editorPanel.expand")
                    }
                    variant="plain"
                    size="xs"
                    onClick={(e) => {
                        e.stopPropagation();
                        toggle();
                    }}
                >
                    {isOpen ? <LuChevronDown /> : <LuChevronRight />}
                </IconButton>
            </Flex>

            {/* Body */}
            <Box
                display="flex"
                flexDirection="column"
                flex="1"
                minH="0"
                opacity={phase === "fading" || phase === "closing" || phase === "closed" ? 0 : 1}
                transition={phase === "fading" ? `opacity ${FADE_MS}ms ease-out` : undefined}
            >
                <VStack
                    align="start"
                    w="full"
                    flex="1"
                    minH="0"
                    gap="6"
                    p="4"
                    overflowY="scroll"
                    scrollbar="hidden"
                >
                    {children}
                </VStack>
                {showPreview && <Preview />}
            </Box>
        </Panel>
    );
}
