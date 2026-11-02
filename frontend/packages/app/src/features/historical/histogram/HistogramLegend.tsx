// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Popover, Portal, Text } from "@chakra-ui/react";
import { OcColorPicker } from "@opencop/ui";
import { memo, useMemo, type ReactElement } from "react";
import { useTranslation } from "react-i18next";

import {
    useHiddenSourceIds,
    useHistoricalActions,
    useSourceColorOverrides,
} from "../data-plane/historical.store";
import { useSubjectNames } from "./use-subject-names.hook";

interface HistogramLegendProps {
    /** Selected source ids, in selection order (matches the stack order). */
    sourceIds: readonly string[];
    /** `sourceId → color`, the same (override-aware) map the chunk bars use. */
    sourceColors: ReadonlyMap<string, string>;
}

/**
 * How many sources show inline before the rest collapse behind a "+N more"
 * popover. Keeps the legend to a single compact row regardless of how many
 * sources are selected (there is no source cap).
 */
const MAX_INLINE = 6;

/** Fallback so `parseColor` always receives a valid hex. */
const FALLBACK_COLOR = "#888888";

interface LegendItemProps {
    id: string;
    name: string;
    color: string;
    hidden: boolean;
    /** Whether this source has a non-default (overridden) color. */
    overridden: boolean;
    onToggle: (id: string) => void;
    onColor: (id: string, hex: string) => void;
    onResetColor: (id: string) => void;
}

/**
 * One legend entry: a color-picker swatch + a toggle label.
 *   - Click the swatch → a color picker opens; the chosen color persists and
 *     recolors that source everywhere (bars, sparkline, tooltip, legend).
 *   - Click the name → hide/show that source's series (the data stays loaded);
 *     a hidden series is struck through and dimmed.
 */
const LegendItem = memo(function LegendItem({
    id,
    name,
    color,
    hidden,
    overridden,
    onToggle,
    onColor,
    onResetColor,
}: LegendItemProps): ReactElement {
    const { t } = useTranslation();

    return (
        <Box display="flex" alignItems="center" gap="1.5" minW="0">
            <OcColorPicker
                value={color}
                onChange={(hexa) => onColor(id, hexa)}
                positioning={{ placement: "top-start" }}
                onReset={() => onResetColor(id)}
                resetDisabled={!overridden}
            >
                <Box
                    as="button"
                    flexShrink={0}
                    // The ColorPicker.Trigger recipe defaults to a 40px min size;
                    // zero it out so the swatch stays the original 10px.
                    boxSize="10px"
                    minW="0"
                    minH="0"
                    p="0"
                    borderRadius="0"
                    bg={color}
                    cursor="pointer"
                    opacity={hidden ? 0.4 : 1}
                    aria-label={t("historical.legendChangeColor", "Change color for {{name}}", {
                        name,
                    })}
                    _focusVisible={{
                        outline: "2px solid",
                        outlineColor: "brand.solid",
                        outlineOffset: "1px",
                    }}
                />
            </OcColorPicker>
            <Text
                as="button"
                onClick={() => onToggle(id)}
                fontSize="xs"
                color={hidden ? "fg.subtle" : "fg.muted"}
                textDecoration={hidden ? "line-through" : "none"}
                cursor="pointer"
                truncate
                maxW="40"
                _hover={{ color: hidden ? "fg.muted" : "fg" }}
                aria-pressed={!hidden}
            >
                {name}
            </Text>
        </Box>
    );
});

/**
 * Per-source legend for the multi-source historical histogram: one color-picker
 * swatch + toggle label per selected source, color-matched to the stacked chunk
 * bars. Names resolve from the user's source lists (active + archived). Shows
 * the first {@link MAX_INLINE} inline; any beyond that collapse into a "+N more"
 * popover so the legend never overflows its row.
 */
export function HistogramLegend({ sourceIds, sourceColors }: HistogramLegendProps): ReactElement {
    const { t } = useTranslation();
    const { nameOf } = useSubjectNames();
    const hiddenSourceIds = useHiddenSourceIds();
    const overrides = useSourceColorOverrides();
    const { toggleSeriesHidden, setSourceColor, resetSourceColor } = useHistoricalActions();

    const hidden = useMemo(() => new Set(hiddenSourceIds), [hiddenSourceIds]);

    const colorOf = (id: string) => sourceColors.get(id) ?? FALLBACK_COLOR;

    const renderItem = (id: string) => (
        <LegendItem
            key={id}
            id={id}
            name={nameOf(id)}
            color={colorOf(id)}
            hidden={hidden.has(id)}
            overridden={Object.hasOwn(overrides, id)}
            onToggle={toggleSeriesHidden}
            onColor={setSourceColor}
            onResetColor={resetSourceColor}
        />
    );

    const inline = sourceIds.slice(0, MAX_INLINE);
    const overflow = sourceIds.slice(MAX_INLINE);

    return (
        <Box
            display="flex"
            alignItems="center"
            gap="3"
            px="2"
            py="1"
            data-testid="histogram-legend"
        >
            {inline.map(renderItem)}
            {overflow.length > 0 && (
                <Popover.Root positioning={{ placement: "top-end" }}>
                    <Popover.Trigger asChild>
                        <Box
                            as="button"
                            fontSize="xs"
                            color="fg.muted"
                            cursor="pointer"
                            flexShrink={0}
                            _hover={{ color: "fg" }}
                            textDecoration="underline"
                            aria-label={t(
                                "historical.legendMoreLabel",
                                "Show {{count}} more sources",
                                {
                                    count: overflow.length,
                                },
                            )}
                        >
                            {t("historical.legendMore", "+{{count}} more", {
                                count: overflow.length,
                            })}
                        </Box>
                    </Popover.Trigger>
                    <Portal>
                        <Popover.Positioner>
                            <Popover.Content
                                // Match the app's glass popovers (range picker,
                                // SettingsPopover): translucent blurred surface,
                                // hairline border, rounded, soft shadow.
                                bg="bg.glass"
                                backdropFilter="blur(4px)"
                                borderColor="border"
                                borderWidth="1px"
                                borderRadius="md"
                                boxShadow="md"
                                w="fit-content"
                                minW="0"
                                maxH="240px"
                                overflowY="auto"
                                p="0"
                            >
                                <Popover.Body p="3" display="flex" flexDir="column" gap="2">
                                    {overflow.map(renderItem)}
                                </Popover.Body>
                            </Popover.Content>
                        </Popover.Positioner>
                    </Portal>
                </Popover.Root>
            )}
        </Box>
    );
}
