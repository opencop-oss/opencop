// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, IconButton, Text } from "@chakra-ui/react";
import { ListPanelItem, Tooltip } from "@opencop/ui";
import type { Element } from "@opencop/elements";
import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { FaMapMarkerAlt } from "react-icons/fa";
import { MdCheck, MdContentCopy } from "react-icons/md";

export interface EventListItemContainer {
    kind: "layer" | "dataSource";
    name: string;
}

interface EventListItemProps {
    element: Element;
    index: number;
    onClick: () => void;
    titleContent?: ReactNode;
    detail?: string;
    /** The layer or data source the element is on, shown after the Copy ID button. */
    container?: EventListItemContainer;
    metadata?: ReactNode;
    /** Always-visible controls at the card's right edge, centred on both lines, before the location pin. */
    actions?: ReactNode;
    /** Always-visible control at the card's left edge, vertically centred beside both lines. */
    leading?: ReactNode;
    /**
     * Renders a location-pin button at the card's right edge, centred on both lines, after any
     * caller-supplied `actions`; invoking it should pan/zoom the map to this element and
     * highlight it, without leaving the list. Omit to hide the button.
     */
    onFocusOnMap?: () => void;
}

/**
 * Common event row used by every Events List source. Source-specific controls
 * and match metadata are slots; the row density, identity, and interaction stay
 * consistent between search and lasso results.
 */
export function EventListItem({
    element,
    index,
    onClick,
    titleContent,
    detail,
    container,
    metadata,
    actions,
    leading,
    onFocusOnMap,
}: EventListItemProps) {
    const { t } = useTranslation();
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        if (!copied) return;
        const id = setTimeout(() => setCopied(false), 2000);
        return () => clearTimeout(id);
    }, [copied]);

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(element.objectId);
            setCopied(true);
        } catch {
            // Clipboard access can fail in insecure contexts or when denied.
        }
    };

    const focusButton = onFocusOnMap && (
        <Tooltip content={t("events.focusOnMap")} uppercase={false}>
            <IconButton
                aria-label={t("events.focusOnMap")}
                size="2xs"
                variant="outline"
                colorPalette="gray"
                borderColor="border.muted"
                color="fg.icon"
                h="5"
                minW="5"
                onClick={() => onFocusOnMap()}
            >
                <FaMapMarkerAlt size={10} />
            </IconButton>
        </Tooltip>
    );

    const combinedActions = (focusButton || actions) && (
        <>
            {actions}
            {focusButton}
        </>
    );

    return (
        <ListPanelItem
            index={index}
            isSelected={false}
            onClick={onClick}
            ariaLabel={element.getDisplayName()}
            title={element.getDisplayName()}
            titleContent={titleContent}
            detail={detail}
            bottomSlot={
                <Flex align="center" gap="2" minW="0" w="full">
                    <Tooltip content={element.objectId} uppercase={false}>
                        <Button
                            size="2xs"
                            variant="ghost"
                            colorPalette="gray"
                            color={copied ? "type.success" : "fg.muted"}
                            fontSize="xs"
                            fontWeight="normal"
                            gap="1"
                            px="1"
                            ms="-1"
                            h="5"
                            flexShrink={0}
                            aria-label={t(copied ? "events.idCopiedFor" : "events.copyIdFor", {
                                name: element.getDisplayName(),
                            })}
                            onClick={handleCopy}
                        >
                            {copied ? <MdCheck size={10} /> : <MdContentCopy size={10} />}
                            {copied ? t("events.idCopied") : t("events.copyId")}
                        </Button>
                    </Tooltip>
                    {container && (
                        <>
                            <Box aria-hidden w="1px" h="3" bg="border.muted" flexShrink={0} />
                            <Tooltip content={container.name} uppercase={false}>
                                <Text
                                    fontSize="xs"
                                    color="fg.muted"
                                    minW="0"
                                    flexShrink={1}
                                    overflow="hidden"
                                    textOverflow="ellipsis"
                                    whiteSpace="nowrap"
                                >
                                    <Text as="span" color="fg.subtle">
                                        {container.kind === "layer"
                                            ? t("events.rowLayerLabel")
                                            : t("events.rowDataSourceLabel")}
                                    </Text>{" "}
                                    {container.name}
                                </Text>
                            </Tooltip>
                        </>
                    )}
                    {metadata}
                </Flex>
            }
            compact
            leftSlot={leading}
            rightSlot={combinedActions}
        />
    );
}
