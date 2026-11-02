// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Checkbox, HStack, Text, VStack } from "@chakra-ui/react";
import { type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { InfoTooltip, SeriesSwatch, type SeriesSwatchVariant } from "@opencop/ui";
import {
    useHistoricalActions,
    useShowActiveEvents,
    useShowActiveEventUpdates,
} from "../data-plane/historical.store";

interface HistogramSettingsProps {
    /**
     * Floor for the settings panel height — guarantees the panel doesn't
     * collapse below the histogram height the user has chosen. Content sets
     * its own size; this just ensures parity with the chart view.
     */
    minHeight: number;
}

interface ToggleRowProps {
    label: string;
    /** Hover-help text explaining what the series shows. */
    description: string;
    /** Selects the preview glyph drawn next to the label. */
    variant: SeriesSwatchVariant;
    checked: boolean;
    onChange: (next: boolean) => void;
}

function ToggleRow({ label, description, variant, checked, onChange }: ToggleRowProps) {
    return (
        <HStack gap="1.5">
            <Checkbox.Root
                checked={checked}
                onCheckedChange={(d) => onChange(d.checked === true)}
                colorPalette="cyan"
                size="sm"
                cursor="pointer"
                w="fit-content"
            >
                <Checkbox.HiddenInput />
                <Checkbox.Control>
                    <Checkbox.Indicator />
                </Checkbox.Control>
                <SeriesSwatch variant={variant} />
                <Checkbox.Label fontSize="sm" fontWeight="normal" color="fg.muted">
                    {label}
                </Checkbox.Label>
            </Checkbox.Root>
            <InfoTooltip content={<Box maxW="240px">{description}</Box>} aria-label={description} />
        </HStack>
    );
}

/**
 * In-panel settings view. Replaces the histogram chart when
 * `settingsOpen === true`, giving the toggles full panel width with their
 * own background treatment to read as a distinct mode rather than an empty
 * panel. Pattern mirrors Chrome DevTools' settings page that takes over the
 * main content area.
 */
export function HistogramSettings({ minHeight }: HistogramSettingsProps): ReactElement {
    const { t } = useTranslation();
    const { setShowActiveEvents, setShowActiveEventUpdates } = useHistoricalActions();
    const showActiveEvents = useShowActiveEvents();
    const showActiveEventUpdates = useShowActiveEventUpdates();

    return (
        <Box h={`${minHeight}px`} w="full" display="flex" flexDir="column" gap="2" pl="2" pb="2">
            <Text
                fontSize="xs"
                fontWeight="medium"
                color="fg.muted"
                textTransform="uppercase"
                letterSpacing="0.06em"
            >
                {t("historical.settings.seriesHeading", "Chart series")}
            </Text>
            <VStack align="start" gap="1.5">
                <ToggleRow
                    label={t("historical.settings.chunksToggle", "Active events")}
                    description={t(
                        "historical.settings.chunksDescription",
                        "The active events loaded for each slice of the timeline, stacked and colored by source.",
                    )}
                    variant="active-events-stacked"
                    checked={showActiveEvents}
                    onChange={setShowActiveEvents}
                />
                <ToggleRow
                    label={t("historical.settings.sparklineToggle", "Active event updates")}
                    description={t(
                        "historical.settings.sparklineDescription",
                        "How many active-event updates occurred during each slice of the timeline.",
                    )}
                    variant="active-event-updates"
                    checked={showActiveEventUpdates}
                    onChange={setShowActiveEventUpdates}
                />
            </VStack>
        </Box>
    );
}
