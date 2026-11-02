// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { parseDate } from "@ark-ui/react/date-picker";
import { DatePicker, Input } from "@chakra-ui/react";
import type { IntlTranslations, ValueChangeDetails } from "@zag-js/date-picker";
import { DateTime } from "luxon";
import { useCallback, useMemo, type ChangeEvent, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { useDateTimeContext } from "./OcDateTimePickerContext";
import { createInclusiveInterval } from "./oc-date-time-picker.util";

function dtToCalendarDate(dt: DateTime) {
    const safe = dt.isValid ? dt : DateTime.now();
    return parseDate(safe.toFormat("yyyy-MM-dd"));
}

export function DateTimePanel(): ReactElement {
    const { t, i18n } = useTranslation();
    const { date, setDate, min, max } = useDateTimeContext();
    const validRange = useMemo(() => createInclusiveInterval(min, max), [min, max]);
    const calendarValue = useMemo(() => [dtToCalendarDate(date)], [date]);
    const timeValue = date.toFormat("HH:mm");

    const onTimeChange = useCallback(
        (e: ChangeEvent<HTMLInputElement>) => {
            // DST gap: Luxon silently advances a nonexistent local time by one hour.
            const parsed = DateTime.fromFormat(e.currentTarget.value, "HH:mm", { zone: date.zone });
            if (!parsed.isValid) return;
            const newDate = date.set({
                hour: parsed.hour,
                minute: parsed.minute,
                second: 0,
                millisecond: 0,
            });
            if (validRange.contains(newDate)) {
                setDate(newDate);
            }
        },
        [date, validRange, setDate],
    );

    const onValueChange = useCallback(
        ({ value }: ValueChangeDetails) => {
            const picked = value[0];
            if (!picked) return;
            // DST gap: Luxon silently advances a nonexistent local time by one hour.
            let newDate = date.set({ year: picked.year, month: picked.month, day: picked.day });
            if (newDate < min) newDate = min.startOf("minute");
            else if (newDate > max) newDate = max.startOf("minute");
            setDate(newDate);
        },
        [date, min, max, setDate],
    );

    return (
        <DatePicker.Root
            value={calendarValue}
            min={dtToCalendarDate(min)}
            max={dtToCalendarDate(max)}
            onValueChange={onValueChange}
            inline
            size="xs"
            colorPalette="cyan"
            locale={i18n.language}
            timeZone={date.zoneName ?? undefined}
            // zag-js merges these with its built-in defaults at runtime; cast
            // silences the incomplete-type error from the stricter static type.
            translations={
                {
                    prevTrigger: (view) =>
                        view === "day"
                            ? t("datePicker.previousMonth")
                            : t("datePicker.previousYear"),
                    nextTrigger: (view) =>
                        view === "day" ? t("datePicker.nextMonth") : t("datePicker.nextYear"),
                    monthSelect: t("datePicker.selectMonth"),
                    yearSelect: t("datePicker.selectYear"),
                } as IntlTranslations
            }
        >
            <DatePicker.View view="day">
                <DatePicker.Header />
                <DatePicker.DayTable />
                <Input
                    type="time"
                    size="sm"
                    aria-label={t("datePicker.time")}
                    value={timeValue}
                    onChange={onTimeChange}
                />
            </DatePicker.View>
            <DatePicker.View view="month">
                <DatePicker.Header />
                <DatePicker.MonthTable />
            </DatePicker.View>
            <DatePicker.View view="year">
                <DatePicker.Header />
                <DatePicker.YearTable />
            </DatePicker.View>
        </DatePicker.Root>
    );
}
