// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ThemingConfig } from "@chakra-ui/react";

export const recipes: NonNullable<ThemingConfig["recipes"]> = {
    button: {
        base: {
            transition: "all .1s ease-out",
            borderRadius: "md",
            color: "fg",
        },
        variants: {
            size: {
                compact: {
                    h: "10",
                    minW: "8",
                    px: "1",
                    textStyle: "sm",
                    gap: "1",
                    _icon: { width: "5", height: "5" },
                },
            },
            variant: {
                outline: {
                    borderColor: "colorPalette.fg",
                    color: "colorPalette.fg",
                    _hover: {
                        bg: "colorPalette.subtle",
                        borderColor: "colorPalette.solid",
                        color: "colorPalette.solid",
                    },
                },
                subtle: {
                    bg: "colorPalette.emphasized",
                    _hover: {
                        bg: "colorPalette.subtle",
                    },
                },
                glass: {
                    border: "1px solid",
                    borderColor: "border.muted",
                    cursor: "pointer",
                    fontWeight: "500",
                    transition:
                        "background-color 0.2s ease-out, border-color 0.2s ease-out, opacity 0.2s ease-out",
                    _hover: {
                        borderColor: "#238fdc",
                        color: "#238fdc",
                    },
                },
                action: {
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "fg",
                    border: "1px solid",
                    borderColor: "border",
                    cursor: "pointer",
                    fontWeight: "500",
                    transition: "background-color 0.2s, border-color 0.2s, opacity 0.2s",
                    _hover: {
                        borderColor: "active",
                        color: "active",
                    },
                },
                toolbar: {
                    color: "fg.muted",
                    fontWeight: "500",
                    cursor: "pointer",
                    transition: "color 0.15s ease-out",
                    _hover: {
                        color: "active",
                    },
                    _focusVisible: {
                        outline: "2px solid",
                        outlineColor: "brand.focusRing",
                        outlineOffset: "2px",
                    },
                    _disabled: {
                        opacity: 0.4,
                        cursor: "not-allowed",
                        _hover: { color: "fg.muted" },
                    },
                },
            },
            flexy: {
                true: {
                    minWidth: 0,
                    minHeight: 0,
                    flex: 1,
                },
            },
            raised: {
                true: {
                    _hover: {
                        transform: "translate(2px, -2px)",
                    },
                },
            },
            active: {
                full: {
                    w: "32",
                    color: "bg",
                    fontWeight: "bold",
                    bg: "active",
                    _hover: { boxShadow: "active", bg: "active" },
                },
                icon: {
                    color: "active",
                    filter: "drop-shadow(1px 1px 4px currentColor)",
                },
                iconHover: {
                    color: "slate.icon.primary",
                    _hover: {
                        color: "active",
                        filter: "drop-shadow(1px 1px 4px currentColor)",
                    },
                },
                iconToggle: {
                    color: "fg.muted",
                    "&[data-active]": {
                        color: "active",
                        filter: "drop-shadow(1px 1px 4px currentColor)",
                    },
                },
            },
        },
    },
    heading: {
        base: {
            color: "fg", // force headings to match theme
        },
    },
    text: {
        base: {
            color: "fg", // force text to match theme
        },
    },
    icon: {
        base: {
            border: "none",
            minWidth: 0,
            color: "fg",
        },
        variants: {
            glow: {
                offet: {
                    filter: "drop-shadow(1px 1px 4px currentColor)",
                },
                center: {
                    filter: "drop-shadow(0px 0px 4px currentColor)",
                },
            },
        },
    },
    input: {
        base: {
            color: "fg",
            _placeholder: {
                color: "fg.muted",
            },
            _focusVisible: {
                borderColor: "brand.focusRing",
                boxShadow: "0 0 6px 1px var(--shadow-color)",
                shadowColor: "brand.focusRing",
                outline: "0",
            },
            _focus: {
                borderColor: "brand.focusRing",
                boxShadow: "0 0 6px 1px var(--shadow-color)",
                shadowColor: "brand.focusRing",
                outline: "0",
            },
        },
        variants: {
            invalid: {
                true: {
                    _focusVisible: {
                        borderColor: "red.focusRing",
                        boxShadow: "0 0 6px 1px var(--shadow-color)",
                        shadowColor: "red.focusRing",
                        outline: "0",
                    },
                    _focus: {
                        borderColor: "red.focusRing",
                        boxShadow: "0 0 6px 1px var(--shadow-color)",
                        shadowColor: "red.focusRing",
                        outline: "0",
                    },
                },
            },
        },
    },
    textarea: {
        base: {
            color: "fg",
            _placeholder: {
                color: "fg.muted",
            },
            _focusVisible: {
                borderColor: "brand.focusRing",
                boxShadow: "0 0 6px 1px var(--shadow-color)",
                shadowColor: "brand.focusRing",
                outline: "0",
            },
            _focus: {
                borderColor: "brand.focusRing",
                boxShadow: "0 0 6px 1px var(--shadow-color)",
                shadowColor: "brand.focusRing",
                outline: "0",
            },
        },
    },
};
