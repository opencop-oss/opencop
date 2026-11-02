// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Input, type InputProps } from "@chakra-ui/react";
import { LabeledInput } from "./LabeledInput";
import { SIDC, Validator } from "@opencop/symbology";
import { useState, type ReactElement, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import type { SymbologyTFunction } from "./symbology-i18n";
import { useSidc } from "./SidcContext";

type ErrorState = { type: "none" } | { type: "invalid" } | { type: "unsupported" };

interface InputState {
    value: string;
    error: ErrorState;
    lastValidSidc: string;
    baseSidc: string;
}

function createInitialState(sidcString: string): InputState {
    return {
        value: sidcString,
        error: { type: "none" },
        lastValidSidc: sidcString,
        baseSidc: sidcString,
    };
}

export type SIDCInputProps = InputProps;

export function SIDCInput(props: SIDCInputProps): ReactElement {
    const { t } = useTranslation(["translation", "symbology"]);
    const { sidc, setSidc } = useSidc();
    const sidcString = sidc.toString();

    return <SIDCInputInner sidcString={sidcString} setSidc={setSidc} t={t} {...props} />;
}

interface SIDCInputInnerProps extends InputProps {
    sidcString: string;
    setSidc: (sidc: SIDC) => void;
    t: SymbologyTFunction;
}

/**
 * Reconciliation policy A:
 * - Preserve local draft while focused (typing in progress)
 * - Once blurred, adopt external SIDC changes from other controls
 * - If there is an active validation error, keep the user's draft/error visible
 *   until they edit, commit (Enter), or dismiss (Escape) it
 */
function SIDCInputInner({ sidcString, setSidc, t, ...props }: SIDCInputInnerProps): ReactElement {
    const [inputState, setInputState] = useState<InputState>(() => createInitialState(sidcString));
    const [isFocused, setIsFocused] = useState(false);
    // Track the last sidcString seen during render so we can detect external
    // SIDC changes without using an effect (getDerivedStateFromProps pattern).
    const [prevSidcString, setPrevSidcString] = useState(sidcString);

    if (
        prevSidcString !== sidcString &&
        inputState.baseSidc !== sidcString &&
        inputState.lastValidSidc !== sidcString &&
        inputState.error.type === "none" &&
        !isFocused
    ) {
        setPrevSidcString(sidcString);
        setInputState(createInitialState(sidcString));
    } else if (prevSidcString !== sidcString) {
        setPrevSidcString(sidcString);
    }

    const { value: inputValue, error } = inputState;

    const validateAndApply = (value: string) => {
        const paddedValue = value.padEnd(20, "0");
        const result = Validator.validateSidc(paddedValue);

        if (!result.ok) {
            const nextError: ErrorState =
                result.kind === "invalid" ? { type: "invalid" } : { type: "unsupported" };
            setInputState((prev) => ({ ...prev, value, error: nextError }));
            return;
        }

        // Valid for this editor — commit.
        setInputState({
            value: paddedValue,
            error: { type: "none" },
            lastValidSidc: paddedValue,
            baseSidc: paddedValue,
        });
        if (paddedValue !== inputState.lastValidSidc) {
            setSidc(SIDC.fromString(paddedValue));
        }
    };

    const handleBlur = () => {
        setIsFocused(false);
        validateAndApply(inputValue);
    };

    const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Enter") {
            e.preventDefault();
            validateAndApply(inputValue);
            return;
        }

        if (e.key === "Escape") {
            e.preventDefault();
            // Revert to the last SIDC this input successfully committed.
            setInputState((prev) => ({
                ...prev,
                value: prev.lastValidSidc,
                error: { type: "none" },
            }));
            return;
        }

        if (
            error.type !== "none" &&
            (e.key.length === 1 || e.key === "Backspace" || e.key === "Delete")
        ) {
            setInputState((prev) =>
                prev.error.type === "none"
                    ? prev
                    : {
                          ...prev,
                          error: { type: "none" },
                      },
            );
        }
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        // Only allow digits
        const value = e.target.value.replace(/\D/g, "").slice(0, 20);
        setInputState((prev) => ({
            ...prev,
            value,
            error: { type: "none" },
        }));
    };

    const isInvalid = error.type !== "none";

    const renderErrorContent = () => {
        if (error.type === "none") return null;

        if (error.type === "invalid") {
            return t("symbology:symbolWizard.validation.invalid");
        }

        if (error.type === "unsupported") {
            return t("symbology:symbolWizard.validation.unsupported");
        }
    };

    return (
        <LabeledInput
            label={t("symbology:symbolWizard.sidc")}
            invalid={isInvalid}
            errorText={renderErrorContent()}
        >
            <Input
                value={inputValue}
                onChange={handleChange}
                onFocus={() => setIsFocused(true)}
                onBlur={handleBlur}
                onKeyDown={handleKeyDown}
                borderWidth="1px"
                borderRadius="2px"
                fontFamily="mono"
                bg="bg"
                size="xs"
                maxLength={20}
                placeholder="00000000000000000000"
                {...props}
            />
        </LabeledInput>
    );
}
