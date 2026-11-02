// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

"use client";

import type { IconButtonProps, SpanProps } from "@chakra-ui/react";
import { IconButton, Span } from "@chakra-ui/react";
import { ThemeProvider, useTheme } from "next-themes";
import type { ThemeProviderProps } from "next-themes";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { LuMoon, LuSun } from "react-icons/lu";

export function ColorModeProvider(props: ThemeProviderProps) {
    return (
        <ThemeProvider attribute="class" disableTransitionOnChange defaultTheme="dark" {...props} />
    );
}

export type ColorMode = "light" | "dark";

export interface UseColorModeReturn {
    colorMode: ColorMode;
    setColorMode: (colorMode: ColorMode) => void;
    toggleColorMode: () => void;
}

export function useColorMode(): UseColorModeReturn {
    const { resolvedTheme, setTheme, forcedTheme } = useTheme();
    const colorMode = forcedTheme || resolvedTheme;
    const toggleColorMode = () => {
        setTheme(resolvedTheme === "dark" ? "light" : "dark");
    };
    return {
        colorMode: colorMode as ColorMode,
        setColorMode: setTheme,
        toggleColorMode,
    };
}

export function useColorModeValue<T>(light: T, dark: T) {
    const { colorMode } = useColorMode();
    return colorMode === "dark" ? dark : light;
}

export function ColorModeIcon() {
    const { colorMode } = useColorMode();
    return colorMode === "dark" ? <LuMoon /> : <LuSun />;
}

type ColorModeButtonProps = Omit<IconButtonProps, "aria-label">;

export function ColorModeButton({
    ref,
    ...props
}: ColorModeButtonProps & { ref?: React.Ref<HTMLButtonElement> }) {
    const { t } = useTranslation();
    const { toggleColorMode } = useColorMode();
    return (
        <IconButton
            aria-label={t("common.toggleColorMode")}
            ref={ref}
            spinner
            onClick={toggleColorMode}
            {...props}
        >
            <ColorModeIcon />
        </IconButton>
    );
}

export function LightMode({ ref, ...props }: SpanProps & { ref?: React.Ref<HTMLSpanElement> }) {
    return (
        <Span
            color="fg"
            display="contents"
            className="chakra-theme light"
            colorPalette="gray"
            colorScheme="light"
            ref={ref}
            {...props}
        />
    );
}

export function DarkMode({ ref, ...props }: SpanProps & { ref?: React.Ref<HTMLSpanElement> }) {
    return (
        <Span
            color="fg"
            display="contents"
            className="chakra-theme dark"
            colorPalette="gray"
            colorScheme="dark"
            ref={ref}
            {...props}
        />
    );
}
