// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { SIDC } from "@opencop/symbology";
import {
    createContext,
    use,
    useCallback,
    useMemo,
    useRef,
    useState,
    type Dispatch,
    type PropsWithChildren,
    type SetStateAction,
} from "react";

export interface SidcContext {
    sidc: SIDC;
    setSidc: (sidc: SIDC) => void;
    setSidcWithModifiers: (sidc: SIDC, modifiers: Record<string, string>) => void;
    modifiers: Record<string, string>;
    setModifiers: Dispatch<SetStateAction<Record<string, string>>>;
}

const SidcContext = createContext<SidcContext | null>(null);

function modifiersSignature(modifiers: Record<string, string>): string {
    return Object.entries(modifiers)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, value]) => `${key}:${value}`)
        .join("|");
}

export const useSidc = () => {
    const ctx = use(SidcContext);
    if (!ctx) throw new Error();
    return ctx;
};

export interface SidcProviderProps extends PropsWithChildren {
    defaultValue?: SIDC;
    defaultModifiers?: Record<string, string>;
    onChange?: (sidc: SIDC, modifiers?: Record<string, string>) => void;
}

export function SidcProvider({
    children,
    defaultValue,
    defaultModifiers,
    onChange,
}: SidcProviderProps) {
    const resolvedDefaultSidc = defaultValue ?? SIDC.default();
    const defaultModifiersKey = modifiersSignature(defaultModifiers ?? {});
    const resolvedDefaultModifiers = useMemo(
        () => defaultModifiers ?? {},
        // Re-memo on content change, not identity — callers may pass a fresh
        // object with identical content each render.
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [defaultModifiersKey],
    );
    const defaultSidcString = resolvedDefaultSidc.toString();

    const [sidcValue, setSidcValue] = useState(() => resolvedDefaultSidc);
    const [modifiers, setModifiers] = useState(() => resolvedDefaultModifiers);
    const sidcRef = useRef(sidcValue);
    const modifiersRef = useRef(modifiers);
    // Track previous default keys in state so derived-state updates happen
    // during render without touching refs (getDerivedStateFromProps pattern).
    const [prevDefaultSidcString, setPrevDefaultSidcString] = useState(defaultSidcString);
    const [prevDefaultModifiersKey, setPrevDefaultModifiersKey] = useState(defaultModifiersKey);

    if (
        prevDefaultSidcString !== defaultSidcString ||
        prevDefaultModifiersKey !== defaultModifiersKey
    ) {
        setPrevDefaultSidcString(defaultSidcString);
        setPrevDefaultModifiersKey(defaultModifiersKey);
        sidcRef.current = resolvedDefaultSidc;
        modifiersRef.current = resolvedDefaultModifiers;
        setSidcValue(resolvedDefaultSidc);
        setModifiers(resolvedDefaultModifiers);
    }

    const setSidc = useCallback(
        (sidc: SIDC) => {
            const newModifiers = {};
            sidcRef.current = sidc;
            modifiersRef.current = newModifiers;
            setModifiers(newModifiers);
            setSidcValue(sidc);
            onChange?.(sidc, newModifiers);
        },
        [onChange],
    );

    const setSidcWithModifiers = useCallback(
        (sidc: SIDC, newModifiers: Record<string, string>) => {
            sidcRef.current = sidc;
            modifiersRef.current = newModifiers;
            setModifiers(newModifiers);
            setSidcValue(sidc);
            onChange?.(sidc, newModifiers);
        },
        [onChange],
    );

    const updateModifiers: Dispatch<SetStateAction<Record<string, string>>> = useCallback(
        (action) => {
            const newModifiers =
                typeof action === "function" ? action(modifiersRef.current) : action;
            modifiersRef.current = newModifiers;
            setModifiers(newModifiers);
            onChange?.(sidcRef.current, newModifiers);
        },
        [onChange],
    );

    const value = useMemo(
        () => ({
            sidc: sidcValue,
            setSidc,
            setSidcWithModifiers,
            modifiers,
            setModifiers: updateModifiers,
        }),
        [sidcValue, modifiers, setSidc, setSidcWithModifiers, updateModifiers],
    );

    return <SidcContext value={value}>{children}</SidcContext>;
}
