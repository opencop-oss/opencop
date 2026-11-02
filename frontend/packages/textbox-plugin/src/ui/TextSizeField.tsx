// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Field, HStack, IconButton, Input, Menu, Portal } from "@chakra-ui/react";
import { useDisplayPreferences } from "@opencop/ui";
import { toMeters, type MeasurementSystem } from "@opencop/utils";
import { useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { LuChevronDown } from "react-icons/lu";
import { clampTextSize } from "./box-text.util";
import { fromDisplaySize, sizePresets, sizeUnit, toDisplaySize } from "./text-size.util";

interface TextSizeFieldProps {
    /** The stored size, always in metres. */
    value: number;
    onChange: (value: number) => void;
}

/**
 * A number, optionally grouped and optionally followed by the unit — so a
 * pasted `2,500 m` reads, while `2km` or `12abc` is refused rather than
 * silently read as 2 or 12.
 *
 * A comma only as a three-digit group, so an `fr` operator's `2,5` is refused
 * rather than read as 25. Each alternative is a single character class, so a
 * long run of whitespace cannot backtrack — `[\d,\s]*` took 1.4 s on 2,000
 * spaces, inside `commit` on the UI thread.
 */
const SIZE_INPUT =
    /^\s*(\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\s\d{3})*(?:\.\d+)?|\.\d+)\s*(m|ft)?\s*$/i;

/**
 * Metres for what was typed. A suffix names its own unit, so `500 ft` is 152 m
 * whichever system the field is showing; a bare number takes the field's.
 *
 * The unit is captured rather than discarded: reading `500 ft` as 500 of the
 * session's unit is a 3.28x error with nothing on screen to show for it.
 */
function parse(input: string, system: MeasurementSystem): number | null {
    const match = SIZE_INPUT.exec(input);
    if (!match) return null;
    const parsed = Number.parseFloat(match[1].replace(/[,\s]/g, ""));
    if (!Number.isFinite(parsed)) return null;

    const suffix = match[2]?.toLowerCase();
    if (suffix === "ft") return toMeters(parsed, "ft");
    if (suffix === "m") return parsed;
    return fromDisplaySize(parsed, system);
}

/**
 * Text size as a number you can type, with a list of sizes to pick from — the
 * shape a font-size control takes elsewhere.
 *
 * Shown in the operator's own units while the size stays stored in metres, so
 * the unit is a display concern and never reaches the element or the wire.
 *
 * Not a slider: the range spans five decades, so any linear track leaves every
 * usable size in the first sliver of it.
 */
export function TextSizeField({ value, onChange }: TextSizeFieldProps): ReactElement {
    const { t } = useTranslation(["translation", "textbox"]);
    const { measurementSystem } = useDisplayPreferences();

    const unit = sizeUnit(measurementSystem);
    const shown = toDisplaySize(value, measurementSystem);
    const label = t("textbox:editor.sizeLabelWithUnit", { unit });

    // Held in state rather than keyed off `value`: remounting the input on
    // every commit drops keyboard focus to the document body. Resynced during
    // render when the size changes elsewhere — the draw's auto-size, or another
    // box selected — which is the shape `OcColorPicker` uses for the same job.
    const [text, setText] = useState(String(shown));
    const [lastShown, setLastShown] = useState(shown);
    const [invalid, setInvalid] = useState(false);
    if (shown !== lastShown) {
        setLastShown(shown);
        setText(String(shown));
        setInvalid(false);
    }

    /** Read what was typed, hold it in range, and show back what was accepted. */
    const commit = () => {
        const typed = parse(text, measurementSystem);
        // Left as typed rather than reverted, so the operator can see what they
        // entered and correct it — `validation.md`, "When to render".
        if (typed === null) {
            setInvalid(true);
            return;
        }

        setInvalid(false);
        const next = clampTextSize(typed);
        const nextShown = toDisplaySize(next, measurementSystem);

        setText(String(nextShown));
        // Compared as shown, not as stored: the displayed number is rounded, so
        // reading it back differs from `value` and a focus/blur with no edit
        // would otherwise rewrite the size and dirty the element.
        if (nextShown !== shown) onChange(next);
    };

    return (
        <Field.Root invalid={invalid} w="full" gap="1">
            <Field.Label textStyle="sm">{label}</Field.Label>
            <HStack gap="1" w="full">
                <Input
                    size="xs"
                    fontFamily="mono"
                    value={text}
                    onChange={(event) => {
                        setText(event.target.value);
                        setInvalid(false);
                    }}
                    onBlur={commit}
                    onKeyDown={(event) => {
                        if (event.key === "Enter") commit();
                        if (event.key === "Escape") {
                            setText(String(shown));
                            setInvalid(false);
                        }
                    }}
                />
                <Menu.Root>
                    <Menu.Trigger asChild>
                        <IconButton
                            size="xs"
                            variant="outline"
                            aria-label={t("textbox:editor.sizePresets")}
                        >
                            <LuChevronDown />
                        </IconButton>
                    </Menu.Trigger>
                    <Portal>
                        <Menu.Positioner>
                            <Menu.Content maxH="64" overflowY="auto">
                                {sizePresets(measurementSystem).map((size) => (
                                    <Menu.Item
                                        key={size}
                                        value={String(size)}
                                        onClick={() => {
                                            // Reset here rather than leaving it
                                            // to the resync: picking the size
                                            // already stored changes nothing to
                                            // resync from, and rejected text
                                            // would stay on screen with it.
                                            const next = clampTextSize(
                                                fromDisplaySize(size, measurementSystem),
                                            );
                                            setText(String(toDisplaySize(next, measurementSystem)));
                                            setInvalid(false);
                                            onChange(next);
                                        }}
                                    >
                                        {`${size.toLocaleString()} ${unit}`}
                                    </Menu.Item>
                                ))}
                            </Menu.Content>
                        </Menu.Positioner>
                    </Portal>
                </Menu.Root>
            </HStack>
            <Field.ErrorText textStyle="xs">
                {t("textbox:editor.sizeInvalid", { unit })}
            </Field.ErrorText>
        </Field.Root>
    );
}
