// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    IconButton,
    Input,
    Popover,
    Portal,
    Text,
    type BoxProps,
    type InputProps,
} from "@chakra-ui/react";
import { DateTime } from "luxon";
import {
    useCallback,
    useId,
    useMemo,
    useRef,
    useState,
    type ClipboardEvent,
    type KeyboardEvent,
    type ReactElement,
    type ReactNode,
} from "react";
import { MdCalendarMonth } from "react-icons/md";
import { useTranslation } from "react-i18next";
import { DateTimePanel } from "./OcDateTimePickerPanel";
import { DateTimeContext, useDateTimeContext } from "./OcDateTimePickerContext";
import {
    TYPEABLE_FORMAT,
    TYPEABLE_FORMAT_LENGTH,
    computeOvertypeAction,
    incrementTypeableSlot,
    padTypeableValue,
    parseTypeablePaste,
} from "./oc-date-time-picker.util";

const DATE_PICKER_Z_INDEX = 2000;

// --- Types ---

export interface OcDateTimePickerProps {
    min: DateTime;
    max: DateTime;
    startTime?: DateTime;
    onChange?: (date: DateTime) => void;
    inputProps?: InputProps;
    bodyProps?: BoxProps;
    disabled?: boolean;
    /** IANA timezone string (e.g. "America/New_York"). Defaults to browser local time. */
    timezone?: string;
    /**
     * When true the input accepts free-form keyboard entry in
     * `yyyy-MM-dd HH:mm` form. Parses on blur or Enter; reverts on invalid
     * or out-of-range input. The calendar icon button still opens the
     * graphical picker. Default `false` preserves the read-only behavior
     * existing callers rely on.
     */
    typeable?: boolean;
    /**
     * Override the helper text shown for typeable validation failures.
     * Each key maps to the corresponding error kind; omitted keys fall
     * through to the generic library defaults
     * ("Date must not be earlier than the allowed range." etc.). Use the
     * overrides when the consumer has domain-specific wording — e.g.
     * `HistoricalRangePicker` overrides `afterMax` on Start with
     * "Start must be before end time." for the cross-field case.
     */
    errorMessages?: {
        format?: string;
        beforeMin?: string;
        afterMax?: string;
    };
    /**
     * Externally driven validation message. When set (non-empty), the picker
     * shows it as the helper text with red border + `aria-invalid` + the
     * polite live region — identical to the picker's own validation
     * presentation. Use for cross-field or range-level constraints the
     * picker can't detect from its own `min`/`max` (e.g. "Date range must
     * not exceed 30 days" when the *pair* of pickers exceeds a cap that
     * neither alone violates). Takes precedence over the picker's own
     * `bufferError` so the cross-field message wins when both apply.
     */
    customError?: string;
}

// --- Exported Component ---

export function OcDateTimePicker({
    min,
    max,
    startTime,
    onChange,
    inputProps,
    bodyProps,
    disabled,
    timezone,
    typeable,
    errorMessages,
    customError,
}: OcDateTimePickerProps): ReactElement {
    return (
        <DateTimeProvider
            min={min}
            max={max}
            startTime={startTime}
            onChange={onChange}
            timezone={timezone}
        >
            <DateTimePickerContent
                inputProps={inputProps}
                bodyProps={bodyProps}
                disabled={disabled}
                typeable={typeable}
                errorMessages={errorMessages}
                customError={customError}
            />
        </DateTimeProvider>
    );
}

// --- Internal Components ---

function DateTimeProvider({
    children,
    min,
    max,
    startTime,
    onChange,
    timezone,
}: {
    min: DateTime;
    max: DateTime;
    startTime?: DateTime;
    onChange?: (date: DateTime) => void;
    children?: ReactNode;
    timezone?: string;
}): ReactElement {
    const zone = timezone ?? "local";
    const minDt = useMemo(() => min.setZone(zone).startOf("minute"), [min, zone]);
    const maxDt = useMemo(() => max.setZone(zone), [max, zone]);
    const startTimeDt = startTime ? startTime.setZone(zone) : minDt;

    const [internalDate, setInternalDate] = useState<DateTime>(startTimeDt);

    // Sync internal state when `startTime` is updated externally (e.g., a
    // quick-select shortcut in HistoricalRangePicker writes new from/to to
    // the store). Derive-during-render keeps us out of an effect so the
    // no-direct-setState-in-useEffect rule stays satisfied; the next render
    // sees the new value in `internalDate` and `syncedStartIso` matches the
    // new prop, so the comparison is stable.
    const startIso = startTimeDt.toMillis().toString();
    const [syncedStartIso, setSyncedStartIso] = useState<string>(startIso);
    if (startIso !== syncedStartIso) {
        setSyncedStartIso(startIso);
        setInternalDate(startTimeDt);
    }

    const setDate = useCallback(
        (arg: DateTime | ((prev: DateTime) => DateTime)) => {
            const newDate = typeof arg === "function" ? arg(internalDate) : arg;
            if (!newDate.equals(internalDate)) onChange?.(newDate);
            setInternalDate(newDate);
        },
        [internalDate, onChange],
    );

    const contextValue = useMemo(
        () => ({ date: internalDate, setDate, min: minDt, max: maxDt }),
        [internalDate, setDate, minDt, maxDt],
    );

    return <DateTimeContext value={contextValue}>{children}</DateTimeContext>;
}

function DateTimePickerContent({
    inputProps,
    bodyProps,
    disabled,
    typeable,
    errorMessages,
    customError,
}: {
    inputProps?: InputProps;
    bodyProps?: BoxProps;
    disabled?: boolean;
    typeable?: boolean;
    errorMessages?: OcDateTimePickerProps["errorMessages"];
    customError?: string;
}): ReactElement {
    const { t, i18n } = useTranslation();
    const { date, setDate, min, max } = useDateTimeContext();
    const [open, setOpen] = useState<boolean>(false);
    const inputRef = useRef<HTMLInputElement>(null);
    const errorId = useId();

    type IconSize = "2xs" | "xs" | "sm" | "md" | "lg" | "xl" | "2xl";
    const iconSize = (inputProps?.size as IconSize | undefined) ?? "sm";

    const canonical = typeable
        ? date.toFormat(TYPEABLE_FORMAT)
        : date.setLocale(i18n.language).toLocaleString(DateTime.DATETIME_SHORT);

    // `buffer` mirrors the typed value while the user is editing. When the
    // underlying `date` shifts because of an external change (calendar
    // selection, updated `startTime` prop, quick-select preset), we reset
    // the buffer to match — using the React-recommended "derive state
    // during render" pattern so the rule against direct setState in
    // effects is satisfied.
    //
    // The sync key is `date.toMillis()`, not the formatted `canonical`
    // string. Two timestamps in the same minute format to identical
    // canonical strings ("yyyy-MM-dd HH:mm" has no seconds), which would
    // otherwise mask a real external change — e.g. clicking "Last 24
    // hours" a few seconds after the previous render leaves canonical
    // unchanged but `date` is genuinely new, and a stale invalid buffer
    // needs to be replaced.
    const [buffer, setBuffer] = useState<string>(canonical);
    const dateKey = date.toMillis();
    const [syncedDateKey, setSyncedDateKey] = useState<number>(dateKey);
    // Validation only runs (and renders) on blur / Enter — never while the
    // user is still typing. Cleared on any keystroke after a validation
    // attempt so the red border + helper text don't linger as the user fixes
    // the value.
    const [showErrors, setShowErrors] = useState<boolean>(false);
    if (dateKey !== syncedDateKey) {
        setSyncedDateKey(dateKey);
        setBuffer(canonical);
        setShowErrors(false);
    }

    // `bufferError` resolves to the specific error key when the user has
    // typed a complete 16-character buffer (a full `yyyy-MM-dd HH:mm`) that
    // fails to parse or lands outside [min, max]. Partial / in-progress
    // buffers stay `null` so we don't flicker a red border as the user types.
    const bufferError: "format" | "beforeMin" | "afterMax" | null = (() => {
        if (!typeable || buffer.length !== TYPEABLE_FORMAT_LENGTH) return null;
        const parsed = DateTime.fromFormat(buffer, TYPEABLE_FORMAT, { zone: date.zone });
        if (!parsed.isValid) return "format";
        if (parsed < min) return "beforeMin";
        if (parsed > max) return "afterMax";
        return null;
    })();
    const bufferIsInvalid = bufferError !== null;

    const errorMessage = (() => {
        if (!bufferError) return null;
        if (bufferError === "format") {
            return errorMessages?.format ?? t("datePicker.errorFormat", "Invalid date");
        }
        if (bufferError === "beforeMin") {
            return (
                errorMessages?.beforeMin ??
                t("datePicker.errorBeforeMin", "Date must not be earlier than the allowed range.")
            );
        }
        return (
            errorMessages?.afterMax ??
            t("datePicker.errorAfterMax", "Date must not be later than the allowed range.")
        );
    })();

    // `customError` is consumer-driven and visible regardless of typing
    // state (no blur gating). When it's set it takes precedence over the
    // picker's own per-field bufferError so cross-field range messages
    // ("Date range must not exceed 30 days") win over per-field ones the
    // user can already see from context (a valid date that violates the
    // range cap).
    const hasCustomError = !!customError;
    const visibleError = hasCustomError ? customError : showErrors ? errorMessage : null;
    const inputIsInvalid = hasCustomError || (showErrors && bufferIsInvalid);

    const commit = useCallback(() => {
        if (!typeable) return;
        // Empty / too-short buffer can't form a date — silently restore the
        // last canonical value so blur leaves the input in a valid state.
        const padded = padTypeableValue(buffer);
        if (padded === null) {
            setBuffer(canonical);
            setShowErrors(false);
            return;
        }
        if (padded !== buffer) setBuffer(padded);
        const parsed = DateTime.fromFormat(padded, TYPEABLE_FORMAT, { zone: date.zone });
        if (!parsed.isValid || parsed < min || parsed > max) {
            // Padded value is well-formed but out of range — surface the
            // validation error and leave the user's value so they can see it.
            setShowErrors(true);
            return;
        }
        setShowErrors(false);
        setDate(parsed);
    }, [buffer, canonical, date.zone, min, max, setDate, typeable]);

    const handleKeyDown = useCallback(
        (e: KeyboardEvent<HTMLInputElement>) => {
            if (e.key === "ArrowUp" || e.key === "ArrowDown") {
                const input = e.currentTarget;
                const cursor = input.selectionStart ?? 0;
                const next = incrementTypeableSlot(
                    buffer,
                    cursor,
                    e.key === "ArrowUp" ? 1 : -1,
                    min,
                    max,
                    date.zone,
                );
                // Buffer isn't a complete parseable value — leave the user's
                // in-progress typing alone instead of fighting the cursor.
                if (next === null) return;
                e.preventDefault();
                setBuffer(next);
                setShowErrors(false);
                // Restore caret to the same character index after re-render.
                // `e.currentTarget` is unreliable across the rAF boundary, so
                // capture the element first.
                requestAnimationFrame(() => {
                    input.setSelectionRange(cursor, cursor);
                });
                return;
            }
            if (e.key === "Enter") {
                e.preventDefault();
                commit();
                e.currentTarget.blur();
            } else if (e.key === "Escape") {
                setBuffer(canonical);
                setShowErrors(false);
                e.currentTarget.blur();
            }
        },
        [buffer, canonical, commit, date.zone, min, max],
    );

    const handlePaste = useCallback(
        (e: ClipboardEvent<HTMLInputElement>) => {
            const raw = e.clipboardData.getData("text");
            const parsed = parseTypeablePaste(raw, date.zone);
            if (parsed === null) return; // fall through to default + onChange mask
            e.preventDefault();
            setBuffer(parsed);
            setShowErrors(false);
        },
        [date.zone],
    );

    return (
        <Popover.Root
            open={open}
            onOpenChange={(e) => {
                setOpen(e.open);
                // Return focus to the typeable input (not the icon trigger)
                // when the calendar closes, so the user can keep editing
                // without re-tabbing. The popover's own focus-restore runs
                // on a microtask/rAF after `onOpenChange`, so we queue a
                // setTimeout(0) to land on the next macrotask and win the
                // last-write.
                if (!e.open && typeable) {
                    setTimeout(() => inputRef.current?.focus(), 0);
                }
            }}
        >
            <Box w="full">
                <Box pos="relative" w="full">
                    {typeable ? (
                        <Input
                            ref={inputRef}
                            pr="10"
                            value={buffer}
                            placeholder={TYPEABLE_FORMAT}
                            inputMode="numeric"
                            onChange={(e) => {
                                const raw = e.target.value;
                                const cursor = e.target.selectionStart ?? raw.length;
                                const input = e.currentTarget;
                                const action = computeOvertypeAction(buffer, raw, cursor);
                                if (action.kind === "reject") {
                                    input.value = buffer;
                                    input.setSelectionRange(action.cursor, action.cursor);
                                    return;
                                }
                                setBuffer(action.buffer);
                                setShowErrors(false);
                                if (action.cursor !== null) {
                                    const targetCursor = action.cursor;
                                    requestAnimationFrame(() => {
                                        input.setSelectionRange(targetCursor, targetCursor);
                                    });
                                }
                            }}
                            onBlur={commit}
                            onKeyDown={handleKeyDown}
                            onPaste={handlePaste}
                            aria-invalid={inputIsInvalid || undefined}
                            aria-describedby={visibleError ? errorId : undefined}
                            disabled={disabled}
                            {...inputProps}
                        />
                    ) : (
                        <Popover.Trigger asChild>
                            <Input
                                readOnly
                                cursor="pointer"
                                pr="10"
                                value={canonical}
                                onClick={() => setOpen(true)}
                                disabled={disabled}
                                {...inputProps}
                            />
                        </Popover.Trigger>
                    )}
                    {typeable ? (
                        <Popover.Trigger asChild>
                            <IconButton
                                variant="ghost"
                                size={iconSize}
                                pos="absolute"
                                right="1"
                                top="50%"
                                transform="translateY(-50%)"
                                aria-label={t("datePicker.open")}
                                disabled={disabled}
                            >
                                <MdCalendarMonth />
                            </IconButton>
                        </Popover.Trigger>
                    ) : (
                        <IconButton
                            variant="ghost"
                            size={iconSize}
                            pos="absolute"
                            right="1"
                            top="50%"
                            transform="translateY(-50%)"
                            onClick={() => setOpen(true)}
                            aria-label={t("datePicker.open")}
                            disabled={disabled}
                        >
                            <MdCalendarMonth />
                        </IconButton>
                    )}
                </Box>
                {visibleError && (
                    <Text
                        id={errorId}
                        color="red.fg"
                        fontSize="xs"
                        mt="1"
                        aria-live="polite"
                        aria-atomic="true"
                    >
                        {visibleError}
                    </Text>
                )}
            </Box>
            <Portal>
                <Popover.Positioner zIndex={DATE_PICKER_Z_INDEX}>
                    <Popover.Content w="fit-content" h="fit-content">
                        <Popover.CloseTrigger />
                        <Popover.Body bg="bg" {...bodyProps}>
                            <DateTimePanel />
                        </Popover.Body>
                    </Popover.Content>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
