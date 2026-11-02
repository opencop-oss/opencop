// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineTokens } from "@chakra-ui/react";

export const tokens = defineTokens({
    fontSizes: {
        "2xs": { value: "0.625rem" }, // 10px
        xs: { value: "0.75rem" }, // 12px
        sm: { value: "0.875rem" }, // 14px
        md: { value: "1rem" }, // 16px
        lg: { value: "1.125rem" }, // 18px
        xl: { value: "1.25rem" }, // 20px
        "2xl": { value: "1.5rem" }, // 24px
        "3xl": { value: "1.875rem" }, // 30px
        "4xl": { value: "2.25rem" }, // 36px
        "5xl": { value: "3rem" }, // 48px
        "6xl": { value: "3.75rem" }, // 60px
        "7xl": { value: "4.5rem" }, // 72px
        "8xl": { value: "6rem" }, // 96px
        "9xl": { value: "8rem" }, // 128px
    },
    radii: {
        none: { value: "0" },
        sm: { value: "0" },
        md: { value: "0" },
        lg: { value: "0" },
        xl: { value: "0" },
        "2xl": { value: "0" },
        "3xl": { value: "0" },
        full: { value: "9999px" },
    },
    shadows: {
        active: { value: "0px 0px 7px .5px {colors.active}" },
    },
    fonts: {
        body: {
            value: "Inter Variable, sans-serif",
        },
        heading: {
            value: "Inter Variable, sans-serif",
        },
        mono: {
            value: "JetBrains Mono Variable",
        },
    },
    zIndex: {
        banner: { value: 1450 },
    },
    colors: {
        slate: {
            bg: {
                value: "#1f2530",
            },
            surface: {
                primary: {
                    value: "#242E3A",
                },
                secondary: {
                    value: "#1D2531",
                },
            },
            popover: {
                value: "#1e2e46ff",
            },
            border: {
                primary: {
                    value: "#3f4e64",
                },
                muted: {
                    value: "#394658ff",
                },
            },
            borderBottomColor: {
                primary: {
                    value: "#384662",
                },
                muted: {
                    value: "#394658ff",
                },
            },
            elevated: {
                value: "#2C3748",
            },
            elevatedLight: {
                value: "#2D3949",
            },
            text: {
                primary: {
                    value: "#f1f5f9",
                },
                muted: {
                    value: "#94a3b8",
                },
            },
            icon: {
                primary: {
                    value: "#7c91aeff",
                },
            },
        },
        // Brand colors - primary blue-grey
        brand: {
            50: { value: "#dce4ec" },
            100: { value: "#b9c9d8" },
            200: { value: "#89a4be" },
            300: { value: "#5a7fa3" },
            400: { value: "#405a74" },
            500: { value: "#2c3e50" },
            600: { value: "#273746" },
            700: { value: "#202d3a" },
            800: { value: "#19232d" },
            900: { value: "#121920" },
            950: { value: "#0d1116" },
        },
        accent: {
            50: { value: "#e0f2ff" },
            100: { value: "#b9e4ff" },
            200: { value: "#8cd3ff" },
            300: { value: "#5ebfff" },
            400: { value: "#32a8ff" },
            500: { value: "#0d8fff" }, //primary accent
            600: { value: "#0074e6" },
            700: { value: "#005cb4" },
            800: { value: "#004382" },
            900: { value: "#002e59" },
            950: { value: "#001a33" },
        },
        // Success colors - green
        success: {
            50: { value: "#bbf0d1" },
            100: { value: "#9ee9be" },
            200: { value: "#77e1a4" },
            300: { value: "#50d88a" },
            400: { value: "#2ecb70" },
            500: { value: "#27ae60" },
            600: { value: "#229954" },
            700: { value: "#1c7d45" },
            800: { value: "#166136" },
            900: { value: "#104626" },
        },
        // Warning colors - amber/orange
        warning: {
            50: { value: "#fffbeb" },
            100: { value: "#fef3c7" },
            200: { value: "#fde68a" },
            300: { value: "#fcd34d" },
            400: { value: "#fbbf24" },
            500: { value: "#f59e0b" },
            600: { value: "#d97706" },
            700: { value: "#b45309" },
            800: { value: "#92400e" },
            900: { value: "#78350f" },
        },
        // Dark theme backgrounds - very dark
        background: {
            dark: { value: "#0b1014" },
            emphasized: { value: "#141d25" },
            subtle: { value: "#19232d" },
        },
        // Secondary colors
        secondary: {
            50: { value: "#d5dfe8" },
            100: { value: "#b5c6d6" },
            200: { value: "#8aa4bf" },
            300: { value: "#6083a7" },
            400: { value: "#46627e" },
            500: { value: "#34495e" },
            600: { value: "#2e4053" },
            700: { value: "#253544" },
            800: { value: "#1d2935" },
            900: { value: "#151d26" },
        },
        red: {
            50: { value: "#FFE5E8" },
            100: { value: "#FFC5C9" },
            200: { value: "#FF9AA1" },
            300: { value: "#FF6E79" },
            400: { value: "#FF4B5A" },
            500: { value: "#FF2B3D" },
            600: { value: "#E01F30" },
            700: { value: "#B81828" },
            800: { value: "#8F121F" },
            900: { value: "#660C16" },
        },
        purple: {
            50: { value: "#f3e8ff" },
            100: { value: "#e9d5ff" },
            200: { value: "#d8b4fe" },
            300: { value: "#c084fc" },
            400: { value: "#a855f7" },
            500: { value: "#9333ea" }, // primary purple
            600: { value: "#7e22ce" },
            700: { value: "#6b21a8" },
            800: { value: "#581c87" },
            900: { value: "#3b0764" },
            950: { value: "#24023f" },
        },
        pagination: {
            active: { value: "{colors.accent.500}" },
            hover: { value: "{colors.brand.600}" },
        },
    },
});
