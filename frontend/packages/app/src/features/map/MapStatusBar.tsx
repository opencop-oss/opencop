// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Grid, IconButton, Text, useToken } from "@chakra-ui/react";
import { type ReactElement, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { MdCheck, MdContentCopy } from "react-icons/md";
import { Tooltip, CoordFormatSelect } from "@opencop/ui";
import { formatCoord } from "@opencop/utils";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";
import { useMeasurementSystem } from "~/features/measurements/use-measurement-system.hook";
import { useToast } from "~/features/notifications/use-toast.hook";
import { useMapStore } from "./core/map.store";
import { useViewActions } from "./core/slices/view/view.hook";
import { MapAttribution } from "~/features/map/MapAttribution";

export const MAP_STATUS_BAR_HEIGHT = 32;

const EARTH_RADIUS_KM = 6378.8137;
const SCALE_MAX_WIDTH = 200;
const TILT_PERSPECTIVE_STYLE = { perspective: "60px" } as const;

function getCardinalDirection(bearing: number): string {
    const normalized = ((bearing % 360) + 360) % 360;
    const directions = [
        "N",
        "NNE",
        "NE",
        "ENE",
        "E",
        "ESE",
        "SE",
        "SSE",
        "S",
        "SSW",
        "SW",
        "WSW",
        "W",
        "WNW",
        "NW",
        "NNW",
    ];
    const index = Math.round(normalized / 22.5) % 16;
    return directions[index];
}

function formatScaleNumber(number: number, mode: "mile" | "norm") {
    const digits: number =
        mode === "mile" ? Math.floor(Math.log10(number / 5280)) : Math.floor(Math.log10(number));
    const frac: number =
        mode === "mile" ? number / (5280 * Math.pow(10, digits)) : number / Math.pow(10, digits);
    const formattedFrac = frac < 5 ? Math.floor(frac) : 5;
    return mode === "mile"
        ? formattedFrac * 5280 * Math.pow(10, digits)
        : formattedFrac * Math.pow(10, digits);
}

function formatScaleLabel(meters: number, feet: number, isImperial: boolean): string {
    if (isImperial) {
        return feet >= 5280 ? `${Math.floor(feet) / 5280}mi` : `${Math.floor(feet)}ft`;
    }
    return meters >= 1000 ? `${Math.floor(meters) / 1000}km` : `${Math.floor(meters)}m`;
}

function Scalebar() {
    const { system } = useMeasurementSystem();
    const boxRef = useRef<HTMLDivElement | null>(null);
    const textRef = useRef<HTMLParagraphElement | null>(null);

    useEffect(() => {
        return useMapStore.subscribe(
            (state) => ({ lat: state.latitude, zoom: state.zoom }),
            ({ lat, zoom }) => {
                const boxEl = boxRef.current;
                const textEl = textRef.current;
                if (!boxEl || !textEl) return;

                const isImperial = system === "IMPERIAL";
                const earthCircumKm =
                    2 * Math.PI * EARTH_RADIUS_KM * Math.cos((lat * Math.PI) / 180);
                const metersPerPixel = (earthCircumKm * 1000) / (512 * Math.pow(2, zoom));
                const feetPerPixel = metersPerPixel * 3.2804;

                const maxMeters = SCALE_MAX_WIDTH * metersPerPixel;
                const maxFeet = SCALE_MAX_WIDTH * feetPerPixel;

                const meters = formatScaleNumber(maxMeters, "norm");
                const feet =
                    maxFeet > 5280
                        ? formatScaleNumber(maxFeet, "mile")
                        : formatScaleNumber(maxFeet, "norm");

                const width = Math.round(
                    isImperial ? feet / feetPerPixel : meters / metersPerPixel,
                );
                const label = formatScaleLabel(meters, feet, isImperial);

                boxEl.style.width = width + "px";
                textEl.textContent = label;
            },
            {
                equalityFn: (a, b) => a.lat === b.lat && a.zoom === b.zoom,
                fireImmediately: true,
            },
        );
    }, [system]);

    return (
        <Flex flexDir="column" alignItems="flex-end" gap="0.5" flex="none">
            <Text
                ref={textRef}
                color="fg.muted"
                fontWeight={600}
                fontSize="xs"
                lineHeight="1"
                pr="2"
            >
                -
            </Text>
            <Box
                ref={boxRef}
                height="6px"
                borderLeft="2px solid"
                borderRight="2px solid"
                borderBottom="2px solid"
                borderColor="fg.muted"
            />
        </Flex>
    );
}

function Compass() {
    const { t } = useTranslation();
    const [activeColor] = useToken("colors", ["active"]);
    const svgRef = useRef<SVGSVGElement>(null);
    const textRef = useRef<HTMLParagraphElement>(null);
    const cardinalRef = useRef<HTMLParagraphElement>(null);
    const { flyTo } = useViewActions();

    const handleResetView = (overrides: Partial<{ bearing: number; pitch: number }>) => {
        const { longitude, latitude, zoom, pitch, bearing } = useMapStore.getState();
        flyTo({
            bearing: bearing ?? 0,
            pitch: pitch ?? 0,
            zoom: zoom ?? 1,
            center: [longitude, latitude],
            duration: 500,
            ...overrides,
        });
    };

    useEffect(() => {
        return useMapStore.subscribe(
            (state) => state.bearing ?? 0,
            (bearing) => {
                const svgEl = svgRef.current;
                const textEl = textRef.current;
                const cardinalEl = cardinalRef.current;
                if (!svgEl || !textEl || !cardinalEl) return;

                svgEl.style.transform = `rotate(${-bearing}deg)`;
                textEl.textContent = (Math.round(bearing) % 360).toString().padStart(3, "0") + "°";
                cardinalEl.textContent = getCardinalDirection(bearing);
            },
            { fireImmediately: true },
        );
    }, []);

    return (
        <Flex alignItems="center" gap="1.5" flex="none">
            <Tooltip content={t("map.resetNorth", "Double-click to reset to north")}>
                <Box
                    as="button"
                    aria-label={t("map.resetNorth", "Double-click to reset to north")}
                    cursor="pointer"
                    userSelect="none"
                    onDoubleClick={() => handleResetView({ bearing: 0 })}
                    onKeyDown={(e: React.KeyboardEvent) => {
                        if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            handleResetView({ bearing: 0 });
                        }
                    }}
                    lineHeight="0"
                    bg="transparent"
                    border="none"
                    p="0"
                >
                    <svg width="28" height="28" viewBox="0 0 20 20" ref={svgRef}>
                        <path
                            d="M 10 3 L 14 14 L 10 12 L 6 14 Z"
                            fill={activeColor}
                            stroke={activeColor}
                            strokeWidth="0.5"
                        />
                    </svg>
                </Box>
            </Tooltip>
            <Text
                fontFamily="mono"
                fontSize="xs"
                fontWeight="bold"
                color="active"
                lineHeight="1"
                textWrap="nowrap"
                filter="drop-shadow(0px 0px 3px currentColor)"
                ref={textRef}
            >
                -
            </Text>
            <Text
                fontFamily="mono"
                fontSize="xs"
                fontWeight="bold"
                color="active"
                lineHeight="1"
                textWrap="nowrap"
                filter="drop-shadow(0px 0px 3px currentColor)"
                w="3ch"
                textAlign="center"
                ref={cardinalRef}
            >
                -
            </Text>
            <Box width="1px" alignSelf="stretch" my="1.5" bg="border" />
            <Tooltip content={t("map.resetPitch", "Double-click to reset tilt")}>
                <Box
                    as="button"
                    aria-label={t("map.resetPitch", "Double-click to reset tilt")}
                    lineHeight="0"
                    cursor="pointer"
                    onDoubleClick={() => handleResetView({ pitch: 0 })}
                    onKeyDown={(e: React.KeyboardEvent) => {
                        if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            handleResetView({ pitch: 0 });
                        }
                    }}
                    bg="transparent"
                    border="none"
                    p="0"
                    style={TILT_PERSPECTIVE_STYLE}
                >
                    <TiltPlane />
                </Box>
            </Tooltip>
            <PitchText />
        </Flex>
    );
}

function TiltPlane() {
    const svgRef = useRef<SVGSVGElement>(null);
    const [activeColor] = useToken("colors", ["active"]);

    useEffect(() => {
        return useMapStore.subscribe(
            (state) => state.pitch ?? 0,
            (pitch) => {
                const el = svgRef.current;
                if (!el) return;

                el.style.transform = `rotateX(${pitch}deg)`;
            },
            {
                fireImmediately: true,
            },
        );
    }, []);

    return (
        <svg ref={svgRef} width="16" height="16" viewBox="0 0 16 16">
            {/* Outer border */}
            <rect
                x="1"
                y="1"
                width="14"
                height="14"
                fill="none"
                stroke={activeColor}
                strokeWidth="1"
            />
            {/* Vertical grid lines */}
            <line x1="5.5" y1="1" x2="5.5" y2="15" stroke={activeColor} strokeWidth="0.5" />
            <line x1="10.5" y1="1" x2="10.5" y2="15" stroke={activeColor} strokeWidth="0.5" />
            {/* Horizontal grid lines */}
            <line x1="1" y1="5.5" x2="15" y2="5.5" stroke={activeColor} strokeWidth="0.5" />
            <line x1="1" y1="10.5" x2="15" y2="10.5" stroke={activeColor} strokeWidth="0.5" />
        </svg>
    );
}

function PitchText() {
    const textRef = useRef<HTMLParagraphElement>(null);

    useEffect(() => {
        return useMapStore.subscribe(
            (state) => state.pitch ?? 0,
            (pitch) => {
                const el = textRef.current;
                if (!el) return;

                el.textContent = pitch.toFixed(2) + "°";
            },
            { fireImmediately: true },
        );
    }, []);

    return (
        <Text
            ref={textRef}
            fontFamily="mono"
            fontSize="xs"
            fontWeight="bold"
            color="active"
            lineHeight="1"
            textWrap="nowrap"
            filter="drop-shadow(0px 0px 3px currentColor)"
        >
            -
        </Text>
    );
}

function Coordinates() {
    const { t } = useTranslation();
    const textRef = useRef<HTMLParagraphElement>(null);
    const { format, setFormat } = useCoordFormat();
    const [copied, setCopied] = useState(false);
    const copyTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
    const { showToast } = useToast();

    useEffect(() => () => clearTimeout(copyTimerRef.current), []);

    async function handleCopy() {
        try {
            const lng = useMapStore.getState().longitude;
            const lat = useMapStore.getState().latitude;
            await navigator.clipboard.writeText(formatCoord(lat, lng, format));
            setCopied(true);
            clearTimeout(copyTimerRef.current);
            copyTimerRef.current = setTimeout(() => setCopied(false), 2000);
        } catch {
            showToast({
                type: "error",
                title: t("coordinates.copyError", "Failed to copy coordinates"),
            });
        }
    }

    useEffect(() => {
        return useMapStore.subscribe(
            (state) => ({ lng: state.longitude, lat: state.latitude }),
            ({ lng, lat }) => {
                const el = textRef.current;
                if (!el) return;

                el.textContent = formatCoord(lat, lng, format);
            },
            { equalityFn: (a, b) => a.lng === b.lng && a.lat === b.lat, fireImmediately: true },
        );
    }, [format]);

    return (
        <Flex alignItems="center" gap="1">
            <CoordFormatSelect value={format} onChange={setFormat} />
            <Text
                ref={textRef}
                fontFamily="mono"
                fontSize="xs"
                fontWeight="600"
                color="active"
                lineHeight="1"
                letterSpacing="wide"
                textWrap="nowrap"
                filter="drop-shadow(0px 0px 3px currentColor)"
            />
            <Tooltip content={t("coordinates.copy")}>
                <IconButton
                    aria-label={t("coordinates.copy")}
                    size="2xs"
                    variant="ghost"
                    onClick={handleCopy}
                    color={copied ? "type.success" : "fg.icon"}
                >
                    {copied ? <MdCheck /> : <MdContentCopy />}
                </IconButton>
            </Tooltip>
        </Flex>
    );
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function MapStatusBar(): ReactElement | null {
    const { t } = useTranslation();

    // Two equal outer columns keep the coordinates centred whatever the scale width or the
    // credits do, and give the credits exactly the room left of the scale.
    return (
        <Grid
            aria-label={t("coordinates.mapCenter")}
            h={`${MAP_STATUS_BAR_HEIGHT}px`}
            bg="bg.glass"
            backdropFilter="blur(4px)"
            templateColumns="minmax(0, 1fr) auto minmax(0, 1fr)"
            alignItems="center"
            columnGap="3"
            px="3"
            flex="none"
            pointerEvents="auto"
            position="relative"
        >
            <Flex minW="0">
                <Compass />
            </Flex>

            <Coordinates />
            <Flex minW="0" justify="flex-end" align="center" gap="3">
                <MapAttribution />
                <Scalebar />
            </Flex>
        </Grid>
    );
}
