// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { defineSemanticTokens } from "@chakra-ui/react";

export const semanticTokens = defineSemanticTokens({
    colors: {
        // Accent color for highlights
        active: {
            value: "{colors.cyan.400}",
        },
        // Status type colors
        type: {
            success: { value: "{colors.success.500}" },
            warning: { value: "{colors.warning.500}" },
            error: { value: "{colors.red.500}" },
            info: { value: "{colors.accent.500}" },
        },
        // Connection status colors
        connection: {
            idle: { value: "{colors.fg.muted}" },
            connecting: { value: "{colors.warning.500}" },
            connected: { value: "{colors.success.500}" },
            reconnecting: { value: "{colors.warning.500}" },
            disconnected: { value: "{colors.red.500}" },
            failed: { value: "{colors.red.500}" },
        },
        // Event timeline colors
        event: {
            created: { value: "#789D7A" },
            moved: { value: "{colors.blue.400}" },
            styled: { value: "{colors.purple.400}" },
            edited: { value: "{colors.orange.400}" },
            deleted: { value: "{colors.red.400}" },
            DEFAULT: { value: "{colors.gray.400}" },
        },
        // Background colors - references slate palette for theming
        bg: {
            DEFAULT: {
                value: { _light: "{colors.white}", _dark: "{colors.slate.bg}" },
            },
            surface: {
                value: {
                    _light: "{colors.secondary.50}",
                    _dark: "{colors.slate.surface.primary}",
                },
            },
            // Subtle alternate-row tint — `surface` nudged one step
            // darker (away from the lighter `elevated` frame) so
            // zebra-striped list rows read without bleeding into the
            // surrounding card. Values match `surface` darkened ~8%.
            surfaceAlt: {
                value: {
                    _light: "#c4cdd5",
                    _dark: "#212a35",
                },
            },
            elevated: {
                value: {
                    _light: "{colors.secondary.100}",
                    _dark: "{colors.slate.elevated}",
                },
            },
            elevatedLight: {
                value: {
                    _light: "{colors.secondary.50}",
                    _dark: "{colors.slate.elevatedLight}",
                },
            },
            popover: {
                value: { _light: "{colors.white}", _dark: "{colors.slate.popover}" },
            },
            emphasized: {
                value: {
                    _light: "{colors.secondary.50}",
                    _dark: "{colors.background.dark}",
                },
            },
            subtle: {
                value: {
                    _light: "{colors.secondary.100}",
                    _dark: "{colors.background.subtle}",
                },
            },
            contrast: {
                value: { _light: "{colors.black}", _dark: "{colors.white}" },
            },
            glass: {
                value: {
                    _light: "{colors.white/90}",
                    _dark: "{colors.slate.surface.primary/90}",
                },
            },
            interactive: {
                value: {
                    _light: "{colors.brand.100}",
                    _dark: "#2F4152",
                },
            },
        },
        // Foreground/text colors - references slate palette for theming
        fg: {
            DEFAULT: {
                value: { _light: "{colors.gray.800}", _dark: "{colors.white}" },
            },
            muted: {
                value: { _light: "{colors.gray.600}", _dark: "{colors.slate.text.muted}" },
            },
            contrast: {
                value: { _light: "{colors.black}", _dark: "{colors.white}" },
            },
            icon: {
                value: {
                    _light: "{colors.gray.600}",
                    _dark: "{colors.slate.icon.primary}",
                },
            },
        },
        // Border colors - references slate palette for theming
        border: {
            DEFAULT: {
                value: {
                    _light: "{colors.gray.200}",
                    _dark: "{colors.slate.border.primary}",
                },
            },
            muted: {
                value: {
                    _light: "{colors.gray.100}",
                    _dark: "{colors.slate.border.muted}",
                },
            },
        },
        table: {
            headerBg: {
                value: {
                    _light: "{colors.brand.200}",
                    _dark: "{colors.brand.800}",
                },
            },
            rowBg: {
                value: {
                    _light: "{colors.brand.50}",
                    _dark: "{colors.slate.bg}",
                },
            },
            rowHover: {
                value: {
                    _light: "{colors.brand.100}",
                    _dark: "{colors.brand.700}",
                },
            },
            border: {
                value: {
                    _light: "{colors.secondary.300}",
                    _dark: "{colors.secondary.300}",
                },
            },
        },
        // Dialog semantic tokens
        dialog: {
            title: {
                value: {
                    _light: "{colors.gray.800}",
                    _dark: "{colors.white}",
                },
            },
            border: {
                value: {
                    _light: "{colors.secondary.300}",
                    _dark: "{colors.secondary.300}",
                },
            },
        },
        // Secondary semantic tokens — powers colorPalette="secondary"
        secondary: {
            solid: {
                value: "{colors.secondary.500}",
            },
            contrast: { value: "white" },
            fg: {
                value: {
                    _light: "{colors.secondary.700}",
                    _dark: "{colors.secondary.200}",
                },
            },
            muted: {
                value: {
                    _light: "{colors.secondary.200}",
                    _dark: "{colors.secondary.700}",
                },
            },
            subtle: {
                value: {
                    _light: "{colors.secondary.100}",
                    _dark: "{colors.secondary.800}",
                },
            },
            emphasized: {
                value: {
                    _light: "{colors.secondary.200}",
                    _dark: "{colors.secondary.700}",
                },
            },
            focusRing: { value: "{colors.secondary.400}" },
        },
        // Brand semantic tokens — powers colorPalette="brand"
        // Uses cyan as the interactive/action color while the brand
        // raw scale (50-950) remains blue-grey for table/layout use.
        brand: {
            solid: { value: "{colors.cyan.400}" },
            contrast: { value: "{colors.gray.900}" },
            fg: {
                value: {
                    _light: "{colors.cyan.700}",
                    _dark: "{colors.cyan.300}",
                },
            },
            muted: {
                value: {
                    _light: "{colors.cyan.200}",
                    _dark: "{colors.cyan.800}",
                },
            },
            subtle: {
                value: {
                    _light: "{colors.cyan.100}",
                    _dark: "{colors.cyan.900}",
                },
            },
            emphasized: {
                value: {
                    _light: "{colors.cyan.300}",
                    _dark: "{colors.cyan.700}",
                },
            },
            focusRing: { value: "{colors.cyan.400}" },
        },
        // Red semantic tokens — powers colorPalette="red"
        red: {
            solid: { value: "{colors.red.500}" },
            contrast: { value: "white" },
            fg: {
                value: {
                    _light: "{colors.red.700}",
                    _dark: "{colors.red.300}",
                },
            },
            muted: {
                value: {
                    _light: "{colors.red.200}",
                    _dark: "{colors.red.800}",
                },
            },
            subtle: {
                value: {
                    _light: "{colors.red.100}",
                    _dark: "{colors.red.900}",
                },
            },
            emphasized: {
                value: {
                    _light: "{colors.red.300}",
                    _dark: "{colors.red.700}",
                },
            },
            focusRing: { value: "{colors.red.500}" },
        },
        glass: {
            value: {
                _light: "rgba(255, 255, 255, 0.6)",
                _dark: "rgba(0, 0, 0, 0.6)",
            },
        },
        "glass.subtle": {
            value: {
                _light: "colors.secondary.100",
                _dark: "rgba(0, 0, 0, 0.3)",
            },
        },
        "glass.blue": {
            value: {
                _light: "rgba(35, 143, 220, 0.25)",
                _dark: "rgba(35, 143, 220, 0.33)",
            },
        },
        "glass.red": {
            value: {
                _light: "rgba(255, 3, 3, 0.25)",
                _dark: "rgba(249, 86, 86, 0.33)",
            },
        },
        icon: {
            active: {
                value: "{colors.cyan.400}",
            },
        },
    },
});
