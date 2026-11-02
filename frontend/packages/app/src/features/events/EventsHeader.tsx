// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, Heading, Text, useToken } from "@chakra-ui/react";
import { PointGeometry, type Element, type PartialOCFeature } from "@opencop/elements";
import type { DetailsTypeIconProps } from "@opencop/contributions";
import { resolveElementPreview } from "~/features/contributions/element-mapper-registry";
import { useDetailsHeader } from "~/features/contributions/details-registry";
import { EntityIconBox, TacticalText, Tooltip } from "@opencop/ui";
import type { ComponentType, ReactElement, ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { LayerIndicator } from "./detail/LayerIndicator";
import { PreviewImage } from "./PreviewImage";
import { RTSP_KLV_EVENT_KIND } from "./events.queries";

interface ObjectHeaderProps {
    details: PartialOCFeature["properties"]["opencop"];
    /** The live element: its kind and geometry choose the icon. */
    element: Element;
}

export function EventsHeader({ details, element }: ObjectHeaderProps) {
    const { t } = useTranslation();
    const header = useDetailsHeader(element);
    const displayName =
        details.displayName ||
        (details.kind === RTSP_KLV_EVENT_KIND
            ? t("events.cameraFeed")
            : details.objectId.slice(0, 8));
    const eventType = element.type;
    const symbol = element.getCode();
    // The subject whose renderer draws this element produces the thumbnail — the
    // same one the event list shows — and declares its glyph and frame, so the
    // header names no element type. The glyph stands in when there is no thumbnail.
    const preview = resolveElementPreview(element);
    const TypeIcon =
        header.typeIcon ??
        (element.geometry instanceof PointGeometry ? PointDefaultIcon : ShapeDefaultIcon);

    return (
        <Flex direction="column" gap="3" pb={3}>
            {/* Icon and Name */}
            <Flex align="center" gap={3} pr="32">
                <ElementIcon
                    type={eventType}
                    symbol={symbol}
                    preview={preview}
                    framed={(header.iconFrame ?? "active") === "active"}
                    TypeIcon={TypeIcon}
                />
                <Flex direction="column" gap="0.5" flex={1} minWidth={0}>
                    <Flex align="center" gap={2} minWidth={0}>
                        <Tooltip content={displayName} uppercase={false}>
                            <Heading
                                size="xl"
                                letterSpacing="1px"
                                fontWeight="extrabold"
                                color="bg.contrast"
                                truncate
                                minWidth={0}
                            >
                                {displayName.toUpperCase()}
                            </Heading>
                        </Tooltip>
                        <LayerIndicator />
                    </Flex>
                    <TacticalText>{(symbol ?? eventType).toUpperCase()}</TacticalText>
                </Flex>
            </Flex>
        </Flex>
    );
}

/** The thumbnail's side inside the 48px icon box, less its padding. */
const PREVIEW_SIZE = 40;

function ElementIcon({
    type,
    symbol,
    preview,
    framed,
    TypeIcon,
}: {
    type: string;
    symbol?: string;
    preview: CanvasImageSource | null;
    framed: boolean;
    TypeIcon: ComponentType<DetailsTypeIconProps>;
}) {
    const { t } = useTranslation();
    const [active] = useToken("colors", "cyan.400");
    const light = "#8a8a8a";
    const dark = "#6e6e6e";
    // An image that fails to load gives way to the type's glyph, labelled as unavailable.
    const unavailable = <TypeIcon color={active} ariaLabel={t("events.iconUnavailable")} />;
    return (
        <EntityIconBox borderColor={framed ? "active" : "transparent"}>
            {preview ? (
                <PreviewImage
                    source={preview}
                    size={PREVIEW_SIZE}
                    label={symbol || type}
                    fallback={unavailable}
                    style={{
                        padding: 4,
                        boxSizing: "content-box",
                        backgroundColor: dark,
                        backgroundImage: `linear-gradient(45deg, ${light} 25%, transparent 25%), linear-gradient(-45deg, ${light} 25%, transparent 25%), linear-gradient(45deg, transparent 75%, ${light} 75%), linear-gradient(-45deg, transparent 75%, ${light} 75%)`,
                        backgroundSize: "10px 10px",
                        backgroundPosition: "0 0, 0 5px, 5px -5px, -5px 0",
                    }}
                />
            ) : symbol ? (
                <Text fontSize="xs" fontFamily="mono" fontWeight={600} color={active}>
                    {symbol.slice(0, 4)}
                </Text>
            ) : (
                <TypeIcon color={active} />
            )}
        </EntityIconBox>
    );
}

/** The host's glyphs when no subject declares one: a circle for a point geometry, else a rectangle. */
function DefaultIcon({
    color,
    ariaLabel,
    children,
}: DetailsTypeIconProps & { children: ReactNode }): ReactElement {
    const a11y = ariaLabel ? { role: "img", "aria-label": ariaLabel } : {};
    return (
        <svg
            width={20}
            height={20}
            viewBox="0 0 24 24"
            fill={color}
            filter={`drop-shadow(0px 0px 4px ${color})`}
            {...a11y}
        >
            {children}
        </svg>
    );
}

function PointDefaultIcon(props: DetailsTypeIconProps): ReactElement {
    return (
        <DefaultIcon {...props}>
            <circle cx="12" cy="12" r="8" />
        </DefaultIcon>
    );
}

function ShapeDefaultIcon(props: DetailsTypeIconProps): ReactElement {
    return (
        <DefaultIcon {...props}>
            <rect x="4" y="4" width="16" height="16" rx="2" />
        </DefaultIcon>
    );
}
