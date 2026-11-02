// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, HStack, Input, Popover, Portal, VStack } from "@chakra-ui/react";
import { OcDateTimePicker, OcInputLabel } from "@opencop/ui";
import { DateTime } from "luxon";
import { useMemo, useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { MdCalendarMonth } from "react-icons/md";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import {
    MAX_HISTORICAL_RANGE_DAYS,
    isRangeOverLimit,
    useHistoricalActions,
    useHistoricalState,
} from "../data-plane/historical.store";
import { formatRangeBounds } from "./range-format";

const POPOVER_Z_INDEX = 1500;

// Presets seed concrete timestamps at click time — they don't auto-refresh.
const QUICK_SELECTS: { hours: number; i18nKey: string; fallback: string }[] = [
    { hours: 1, i18nKey: "historical.preset1h", fallback: "Last hour" },
    { hours: 6, i18nKey: "historical.preset6h", fallback: "Last 6 hours" },
    { hours: 24, i18nKey: "historical.preset24h", fallback: "Last 24 hours" },
    { hours: 168, i18nKey: "historical.preset7d", fallback: "Last 7 days" },
    { hours: 720, i18nKey: "historical.preset30d", fallback: "Last 30 days" },
];

export function HistoricalRangePicker(): ReactElement {
    const { t } = useTranslation();
    const { from, to } = useHistoricalState();
    const { setTimeRange } = useHistoricalActions();
    const timezone = useUserTimezone();
    const [open, setOpen] = useState(false);
    // Route the over-cap error to only the side the operator just touched,
    // avoiding a duplicate error on the other input plus the status bar.
    const [lastEdited, setLastEdited] = useState<"start" | "end" | null>(null);

    const fromDate = useMemo(
        () => (from ? DateTime.fromISO(from, { zone: timezone }) : undefined),
        [from, timezone],
    );
    const toDate = useMemo(
        () => (to ? DateTime.fromISO(to, { zone: timezone }) : undefined),
        [to, timezone],
    );
    // Calendar UI floor only — not a domain rule. The 30-day cap is enforced
    // separately via `rangeExceedsLimit`. 10 years keeps "prev month" from
    // scrolling toward year 1900.
    const pickerLowerBound = useMemo(() => DateTime.utc().minus({ years: 10 }), []);
    // Stable for the component's lifetime to avoid invalidating
    // OcDateTimePicker's `maxDt` memo each render.
    const now = useMemo(() => DateTime.now(), []);

    // Hard min/max enforce only the calendar floor and Start ≤ End. The
    // 30-day cap is deliberately NOT clamped here — silent clamping is
    // opaque to operators. Instead it surfaces via `rangeExceedsLimit`,
    // which disables Activate and paints the trigger border red.
    const startAfterEnd = t(
        "historical.rangePicker.errorStartAfterEnd",
        "Start must be before end time.",
    );
    const endBeforeStart = t(
        "historical.rangePicker.errorEndBeforeStart",
        "End time must be after start time.",
    );
    const rangeMaxDaysMessage = t("historical.rangePicker.errorRangeMaxDays", {
        days: MAX_HISTORICAL_RANGE_DAYS,
        defaultValue: "Date range must not exceed {{days}} days.",
    });

    const rangeExceedsLimit = isRangeOverLimit(from, to);
    // Picker doesn't know about the cross-field cap; push it in via
    // `customError`, but only on the input the operator just edited.
    const startRangeCapError =
        rangeExceedsLimit && lastEdited === "start" ? rangeMaxDaysMessage : undefined;
    const endRangeCapError =
        rangeExceedsLimit && lastEdited === "end" ? rangeMaxDaysMessage : undefined;

    const { startLabel, endLabel } = useMemo(
        () => formatRangeBounds(from, to, timezone),
        [from, to, timezone],
    );

    const triggerText = useMemo(() => {
        if (startLabel && endLabel) return `${startLabel} -> ${endLabel}`;
        return t("historical.rangePicker.ariaLabel", "Select date range");
    }, [startLabel, endLabel, t]);

    const applyQuickSelect = (hours: number) => {
        const nowDt = DateTime.utc();
        const fromIso = nowDt.minus({ hours }).toISO();
        const toIso = nowDt.toISO();
        if (fromIso && toIso) setTimeRange(fromIso, toIso);
        setOpen(false);
    };

    return (
        <Popover.Root
            open={open}
            onOpenChange={(e) => setOpen(e.open)}
            positioning={{ placement: "bottom-start" }}
            modal={false}
            autoFocus={false}
            // Re-mount on each open so abandoned invalid input buffers in
            // the typeable pickers don't reappear on reopen.
            unmountOnExit
        >
            <Box pos="relative" w="17rem">
                <Popover.Trigger asChild>
                    <Input
                        readOnly
                        cursor="pointer"
                        size="xs"
                        bg="bg"
                        pl="7"
                        pr="2"
                        value={triggerText}
                        onClick={() => setOpen(true)}
                        aria-haspopup="dialog"
                        aria-expanded={open}
                        aria-label={t("historical.rangePicker.ariaLabel", "Select date range")}
                        aria-invalid={rangeExceedsLimit || undefined}
                        borderColor={rangeExceedsLimit ? "red.fg" : undefined}
                        _focus={rangeExceedsLimit ? { borderColor: "red.fg" } : undefined}
                        // While open, drop the cyan focus border so the
                        // popover (not the trigger) reads as the active
                        // surface; the glow stays via the Input recipe.
                        _open={{ borderColor: "border" }}
                    />
                </Popover.Trigger>
                <Box
                    aria-hidden="true"
                    pos="absolute"
                    left="2"
                    top="50%"
                    transform="translateY(-50%)"
                    color="fg.muted"
                    pointerEvents="none"
                    display="inline-flex"
                    alignItems="center"
                >
                    <MdCalendarMonth size={14} />
                </Box>
            </Box>
            <Portal>
                <Popover.Positioner zIndex={POPOVER_Z_INDEX}>
                    <Popover.Content
                        bg="bg.glass"
                        backdropFilter="blur(4px)"
                        borderColor="border"
                        borderWidth="1px"
                        borderRadius="md"
                        boxShadow="md"
                        w="fit-content"
                        minW="0"
                        p="0"
                    >
                        <Popover.Body p="4">
                            <HStack align="stretch" gap="5">
                                <VStack
                                    role="group"
                                    aria-label={t(
                                        "historical.rangePicker.quickSelect",
                                        "Quick select",
                                    )}
                                    align="stretch"
                                    gap="0"
                                    minW="9rem"
                                >
                                    <OcInputLabel
                                        label={t(
                                            "historical.rangePicker.quickSelect",
                                            "Quick select",
                                        )}
                                        mb="2"
                                    />
                                    {QUICK_SELECTS.map((qs) => (
                                        <Button
                                            key={qs.hours}
                                            variant="plain"
                                            size="xs"
                                            px="0"
                                            py="1"
                                            justifyContent="flex-start"
                                            fontSize="sm"
                                            fontWeight="normal"
                                            color="cyan.300"
                                            _hover={{
                                                color: "cyan.200",
                                                textDecoration: "underline",
                                            }}
                                            onClick={() => applyQuickSelect(qs.hours)}
                                        >
                                            {t(qs.i18nKey, qs.fallback)}
                                        </Button>
                                    ))}
                                </VStack>

                                <Box w="1px" bg="border" alignSelf="stretch" />

                                <VStack align="stretch" gap="3" w="11rem">
                                    <OcInputLabel
                                        label={t("historical.rangePicker.custom", "Custom")}
                                        mb="0"
                                    />
                                    <Box>
                                        <OcInputLabel
                                            label={t("historical.start", "Start")}
                                            mb="1"
                                        />
                                        <OcDateTimePicker
                                            typeable
                                            min={pickerLowerBound}
                                            max={toDate ?? now}
                                            startTime={fromDate}
                                            timezone={timezone}
                                            onChange={(date) => {
                                                setLastEdited("start");
                                                if (to) setTimeRange(date.toISO()!, to);
                                            }}
                                            inputProps={{
                                                size: "xs",
                                                bg: "bg",
                                                w: "11rem",
                                                "aria-label": t("historical.start", "Start"),
                                            }}
                                            errorMessages={{
                                                afterMax: startAfterEnd,
                                            }}
                                            customError={startRangeCapError}
                                        />
                                    </Box>
                                    <Box>
                                        <OcInputLabel label={t("historical.end", "End")} mb="1" />
                                        <OcDateTimePicker
                                            typeable
                                            min={fromDate ?? pickerLowerBound}
                                            max={now}
                                            startTime={toDate}
                                            timezone={timezone}
                                            onChange={(date) => {
                                                setLastEdited("end");
                                                if (from) setTimeRange(from, date.toISO()!);
                                            }}
                                            inputProps={{
                                                size: "xs",
                                                bg: "bg",
                                                w: "11rem",
                                                "aria-label": t("historical.end", "End"),
                                            }}
                                            errorMessages={{
                                                beforeMin: endBeforeStart,
                                            }}
                                            customError={endRangeCapError}
                                        />
                                    </Box>
                                </VStack>
                            </HStack>
                        </Popover.Body>
                    </Popover.Content>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
