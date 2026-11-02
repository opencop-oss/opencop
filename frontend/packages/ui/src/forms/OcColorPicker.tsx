// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Button,
    ColorPicker,
    HStack,
    Input,
    Portal,
    Slider,
    Tabs,
    Text,
    VStack,
    parseColor,
    type ColorPickerRootProps,
    type Color,
} from "@chakra-ui/react";
import { useState, type ReactElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";

// 8×7 swatch grid — organized by hue with light-to-dark gradients per column.
// prettier-ignore
const SWATCHES = [
    "#000000", "#4A1A2E", "#6B1B1B", "#7B3F00", "#5C4B00", "#1B5E20", "#1A237E", "#4A148C",
    "#333333", "#880E4F", "#B71C1C", "#E65100", "#827717", "#2E7D32", "#283593", "#6A1B9A",
    "#666666", "#C2185B", "#D32F2F", "#EF6C00", "#9E9D24", "#388E3C", "#3949AB", "#7B1FA2",
    "#999999", "#E91E63", "#F44336", "#FF9800", "#CDDC39", "#4CAF50", "#5C6BC0", "#9C27B0",
    "#BBBBBB", "#F06292", "#E57373", "#FFB74D", "#E6EE9C", "#81C784", "#9FA8DA", "#CE93D8",
    "#DDDDDD", "#F8BBD0", "#FFCDD2", "#FFE0B2", "#F0F4C3", "#C8E6C9", "#C5CAE9", "#E1BEE7",
    "#FFFFFF", "#FCE4EC", "#FFEBEE", "#FFF3E0", "#F9FBE7", "#E8F5E9", "#E8EAF6", "#F3E5F5",
];

/**
 * Output color format. The component's contract is to emit `#RRGGBBAA`, so the
 * format is named once and used for every `toString` that feeds `onChange` or
 * the preview swatch. (The hex text field below uses bare `"hex"` — no alpha —
 * deliberately, so it is left as-is.)
 */
const COLOR_OUTPUT_FORMAT = "hexa";

/** Fallback color for an empty / unparseable value (also the hex placeholder). */
const DEFAULT_COLOR = "#000000";

/** Parse to a Color, falling back to opaque black if the string is unparseable. */
function safeParse(value: string): Color {
    try {
        return parseColor(value);
    } catch {
        return parseColor(DEFAULT_COLOR);
    }
}

/** Parse a Color into RGBA components (0–255 for RGB, 0–100 for A). */
function toRgba(color: Color): [number, number, number, number] {
    const hex = color.toString(COLOR_OUTPUT_FORMAT);
    const r = parseInt(hex.slice(1, 3), 16);
    const g = parseInt(hex.slice(3, 5), 16);
    const b = parseInt(hex.slice(5, 7), 16);
    const a = hex.length >= 9 ? Math.round((parseInt(hex.slice(7, 9), 16) / 255) * 100) : 100;
    return [r, g, b, a];
}

/** Build a hexa string from RGBA components. */
function fromRgba(r: number, g: number, b: number, a: number): string {
    const hex = (n: number) =>
        Math.max(0, Math.min(255, Math.round(n)))
            .toString(16)
            .padStart(2, "0");
    const alphaHex = hex(Math.round((a / 100) * 255));
    return `#${hex(r)}${hex(g)}${hex(b)}${alphaHex}`;
}

export interface OcColorPickerProps {
    /** Current color — any CSS color string (hex, hexa, rgb, rgba). */
    value: string;
    /** Called with a hexa string (`#RRGGBBAA`) whenever the color changes. */
    onChange: (hexa: string) => void;
    /**
     * The clickable trigger element. Rendered via `ColorPicker.Trigger asChild`,
     * so callers control its appearance (a tool icon, a small legend swatch, …).
     */
    children: ReactNode;
    /** Popover placement, forwarded to the underlying `ColorPicker.Root`. */
    positioning?: ColorPickerRootProps["positioning"];
    /**
     * When provided, a "Reset" button appears in the picker tab. Use it to clear
     * an override / revert to a default; disable it via `resetDisabled` when
     * there's nothing to reset.
     */
    onReset?: () => void;
    resetDisabled?: boolean;
}

/**
 * Shared tabbed color picker: a Picker tab (area + sliders + hex + eyedropper),
 * a curated Swatches grid, and an RGBA channel tab. Controlled: pass `value` and
 * an `onChange` that receives a hexa string; supply the clickable trigger as
 * `children`.
 */
export function OcColorPicker({
    value,
    onChange,
    children,
    positioning,
    onReset,
    resetDisabled,
}: OcColorPickerProps): ReactElement {
    const { t } = useTranslation();
    const color = safeParse(value);
    // Local editing buffer for the hex field — re-synced whenever the controlled
    // value changes (drag, slider, swatch), but left alone while the user types
    // a partial hex (no `onChange` fires until blur/Enter). Syncing on the prop
    // change is done during render via the "store previous value" pattern rather
    // than an effect (no extra commit; React re-renders immediately).
    const [hexInput, setHexInput] = useState(() => color.toString("hex"));
    const [lastValue, setLastValue] = useState(value);
    if (value !== lastValue) {
        setLastValue(value);
        setHexInput(color.toString("hex"));
    }

    const applyHexa = (hexa: string) => {
        try {
            onChange(parseColor(hexa).toString(COLOR_OUTPUT_FORMAT));
        } catch {
            // invalid color — ignore
        }
    };

    const handleHexSubmit = () => {
        const hex = hexInput.startsWith("#") ? hexInput : `#${hexInput}`;
        try {
            onChange(parseColor(hex).toString(COLOR_OUTPUT_FORMAT));
        } catch {
            setHexInput(color.toString("hex"));
        }
    };

    const [r, g, b, a] = toRgba(color);
    const setChannel = (channel: "r" | "g" | "b" | "a", val: number) => {
        applyHexa(
            fromRgba(
                channel === "r" ? val : r,
                channel === "g" ? val : g,
                channel === "b" ? val : b,
                channel === "a" ? val : a,
            ),
        );
    };

    return (
        <ColorPicker.Root
            value={color}
            format="rgba"
            positioning={positioning}
            css={{ "& > div": { border: "none", p: 0 } }}
            onValueChange={(v) => onChange(v.value.toString(COLOR_OUTPUT_FORMAT))}
        >
            <ColorPicker.HiddenInput />
            <ColorPicker.Trigger asChild>{children}</ColorPicker.Trigger>
            <Portal>
                <ColorPicker.Positioner>
                    <ColorPicker.Content bg="slate.popover" p="0">
                        <Tabs.Root defaultValue="picker" size="sm" variant="line">
                            <Tabs.List borderBottomColor="border.muted">
                                <Tabs.Trigger value="picker" fontSize="xs" px="3">
                                    {t("colorPicker.tabPicker", "Picker")}
                                </Tabs.Trigger>
                                <Tabs.Trigger value="swatches" fontSize="xs" px="3">
                                    {t("colorPicker.tabSwatches", "Swatches")}
                                </Tabs.Trigger>
                                <Tabs.Trigger value="rgba" fontSize="xs" px="3">
                                    RGBA
                                </Tabs.Trigger>
                            </Tabs.List>

                            <Box p="3">
                                {/* Tab 1: Color area + sliders + hex input */}
                                <Tabs.Content value="picker" p="0">
                                    <VStack gap="3" align="stretch">
                                        <ColorPicker.Area>
                                            <ColorPicker.AreaBackground />
                                            {/* Small white box marking the selected
                                                color's spot in the gradient; the dark
                                                ring keeps it visible on light colors. */}
                                            <ColorPicker.AreaThumb
                                                boxSize="3"
                                                borderRadius="2px"
                                                border="2px solid white"
                                                boxShadow="0 0 0 1px rgba(0, 0, 0, 0.6)"
                                            />
                                        </ColorPicker.Area>
                                        <ColorPicker.Sliders />
                                        <HStack gap="2">
                                            <Box
                                                boxSize="6"
                                                borderRadius="sm"
                                                bg={color.toString(COLOR_OUTPUT_FORMAT)}
                                                border="1px solid"
                                                borderColor="border.muted"
                                                flexShrink={0}
                                            />
                                            <Input
                                                size="xs"
                                                fontFamily="mono"
                                                flex="1"
                                                minW="0"
                                                value={hexInput}
                                                placeholder={DEFAULT_COLOR}
                                                aria-label={t(
                                                    "colorPicker.hexInput",
                                                    "Hex color value",
                                                )}
                                                onChange={(e) => setHexInput(e.target.value)}
                                                onBlur={handleHexSubmit}
                                                onKeyDown={(e) => {
                                                    if (e.key === "Enter") handleHexSubmit();
                                                }}
                                            />
                                            <ColorPicker.EyeDropper size="xs" variant="outline" />
                                            {onReset && (
                                                <Button
                                                    size="xs"
                                                    variant="ghost"
                                                    px="2"
                                                    flexShrink={0}
                                                    disabled={resetDisabled}
                                                    onClick={onReset}
                                                >
                                                    {t("colorPicker.reset", "Reset")}
                                                </Button>
                                            )}
                                        </HStack>
                                    </VStack>
                                </Tabs.Content>

                                {/* Tab 2: Swatch grid */}
                                <Tabs.Content value="swatches" p="0">
                                    <ColorPicker.SwatchGroup
                                        css={{
                                            display: "grid",
                                            gridTemplateColumns: "repeat(8, 1fr)",
                                            gap: "2px",
                                        }}
                                    >
                                        {SWATCHES.map((item) => {
                                            const isSelected =
                                                hexInput.toLowerCase() === item.toLowerCase();
                                            return (
                                                <ColorPicker.SwatchTrigger key={item} value={item}>
                                                    <ColorPicker.Swatch
                                                        boxSize="4"
                                                        value={item}
                                                        borderRadius="sm"
                                                        outline={
                                                            isSelected ? "2px solid" : undefined
                                                        }
                                                        outlineColor="active"
                                                        outlineOffset="1px"
                                                        zIndex={isSelected ? 1 : undefined}
                                                    />
                                                </ColorPicker.SwatchTrigger>
                                            );
                                        })}
                                    </ColorPicker.SwatchGroup>
                                </Tabs.Content>

                                {/* Tab 3: RGBA channel sliders + number inputs */}
                                <Tabs.Content value="rgba" p="0">
                                    <VStack gap="2" align="stretch">
                                        <ChannelRow
                                            label="R"
                                            value={r}
                                            max={255}
                                            color="red.400"
                                            onChange={(v) => setChannel("r", v)}
                                        />
                                        <ChannelRow
                                            label="G"
                                            value={g}
                                            max={255}
                                            color="green.400"
                                            onChange={(v) => setChannel("g", v)}
                                        />
                                        <ChannelRow
                                            label="B"
                                            value={b}
                                            max={255}
                                            color="blue.400"
                                            onChange={(v) => setChannel("b", v)}
                                        />
                                        <ChannelRow
                                            label="A"
                                            value={a}
                                            max={100}
                                            color="fg.muted"
                                            onChange={(v) => setChannel("a", v)}
                                        />
                                    </VStack>
                                </Tabs.Content>
                            </Box>
                        </Tabs.Root>
                    </ColorPicker.Content>
                </ColorPicker.Positioner>
            </Portal>
        </ColorPicker.Root>
    );
}

/** Single RGBA channel row: label + slider + number input. */
function ChannelRow({
    label,
    value,
    max,
    color,
    onChange,
}: {
    label: string;
    value: number;
    max: number;
    color: string;
    onChange: (v: number) => void;
}): ReactElement {
    return (
        <HStack gap="2">
            <Text fontSize="xs" fontFamily="mono" fontWeight="bold" w="14px" flexShrink={0}>
                {label}
            </Text>
            <Slider.Root
                flex="1"
                min={0}
                max={max}
                step={1}
                value={[value]}
                aria-label={[label]}
                onValueChange={(v) => onChange(v.value[0])}
            >
                <Slider.Control>
                    <Slider.Track>
                        <Slider.Range bg={color} />
                    </Slider.Track>
                    <Slider.Thumb index={0} boxSize="3" />
                </Slider.Control>
            </Slider.Root>
            <Input
                size="2xs"
                fontFamily="mono"
                fontSize="xs"
                w="44px"
                textAlign="center"
                flexShrink={0}
                aria-label={label}
                value={value}
                onChange={(e) => {
                    const n = parseInt(e.target.value, 10);
                    if (!isNaN(n) && n >= 0 && n <= max) onChange(n);
                }}
            />
        </HStack>
    );
}
