// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ThemingConfig } from "@chakra-ui/react";

export const slotRecipes: NonNullable<ThemingConfig["slotRecipes"]> = {
    table: {
        slots: ["root", "header", "body", "row", "columnHeader", "cell"],

        base: {
            root: {
                bg: "table.rowBg",
            },

            header: {
                bg: "table.headerBg",
            },

            row: {
                bg: "table.rowBg",
                borderBottomWidth: "1px",
                borderColor: "table.border",
                _hover: {
                    bg: "table.rowHover",
                },
            },

            columnHeader: {
                color: "fg",
                fontWeight: "bold",
                borderBottomWidth: "1px",
                borderColor: "table.border",
                px: 4,
                py: 4,
            },

            cell: {
                color: "fg",
                borderBottomWidth: "1px",
                borderColor: "table.border",
            },
        },

        variants: {
            striped: {
                row: {
                    _odd: {
                        bg: "bg.subtle",
                    },
                },
            },
        },
    },
    numberInput: {
        slots: [
            "root",
            "label",
            "input",
            "control",
            "valueText",
            "incrementTrigger",
            "decrementTrigger",
            "scrubber",
        ],
        base: {
            input: {
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
        variants: {
            invalid: {
                true: {
                    input: {
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
    },
    select: {
        slots: [
            "root",
            "label",
            "control",
            "trigger",
            "indicator",
            "indicatorGroup",
            "clearTrigger",
            "content",
            "item",
            "itemText",
            "itemIndicator",
            "itemGroup",
            "itemGroupLabel",
            "valueText",
            "positioner",
        ],
        base: {
            trigger: {
                color: "fg",
                borderColor: "border",
                bg: "bg",
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
                _open: {
                    borderColor: "brand.focusRing",
                    boxShadow: "0 0 6px 1px var(--shadow-color)",
                    shadowColor: "brand.focusRing",
                    outline: "0",
                },
            },
            content: {
                bg: "bg",
                borderColor: "border",
                color: "fg",
            },
            item: {
                color: "fg",
                _hover: {
                    bg: "brand.muted",
                },
                _highlighted: {
                    bg: "brand.muted",
                },
            },
            itemIndicator: {
                color: "brand.fg",
            },
            valueText: {
                _placeholder: {
                    color: "fg.muted",
                },
            },
            indicator: {
                color: "fg.muted",
            },
            positioner: {
                zIndex: "popover",
            },
        },
    },
    combobox: {
        slots: [
            "root",
            "label",
            "control",
            "input",
            "trigger",
            "indicator",
            "indicatorGroup",
            "clearTrigger",
            "content",
            "item",
            "itemText",
            "itemIndicator",
            "itemGroup",
            "itemGroupLabel",
            "positioner",
            "empty",
        ],
        base: {
            input: {
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
            content: {
                bg: "bg",
                borderColor: "border",
                color: "fg",
            },
            item: {
                color: "fg",
                _hover: {
                    bg: "brand.muted",
                },
                _highlighted: {
                    bg: "brand.muted",
                },
            },
            itemIndicator: {
                color: "brand.fg",
            },
            trigger: {
                color: "fg.muted",
            },
            clearTrigger: {
                color: "fg.muted",
            },
            positioner: {
                zIndex: "popover",
            },
        },
    },
    tag: {
        slots: ["root", "label", "startElement", "endElement", "closeTrigger"],
        base: {
            root: {
                colorPalette: "secondary",
            },
            label: {
                color: "fg",
            },
        },
    },
    field: {
        slots: [
            "root",
            "label",
            "input",
            "textarea",
            "select",
            "errorText",
            "helperText",
            "requiredIndicator",
        ],

        base: {
            label: {
                color: "fg.muted",
            },
        },
    },
    steps: {
        slots: [
            "root",
            "list",
            "item",
            "trigger",
            "indicator",
            "separator",
            "content",
            "title",
            "description",
        ],
        base: {
            indicator: {
                borderRadius: "0",
            },
            title: {
                color: "colorPalette.fg",
                fontWeight: "bold",
            },
        },
    },
    checkbox: {
        slots: ["root", "control", "indicator", "label", "group"],
        base: {
            label: {
                color: "fg",
            },
            control: {
                borderColor: "slate.icon.primary",
            },
            indicator: {
                color: "active",
            },
        },
    },
    switch: {
        slots: ["root", "label", "control", "thumb", "indicator"],
        base: {
            label: {
                color: "fg.muted",
            },
        },
    },
    colorPicker: {
        slots: [
            "root",
            "label",
            "control",
            "trigger",
            "positioner",
            "content",
            "area",
            "channelInput",
        ],
        base: {
            control: {
                border: "1px solid",
                borderColor: "border",
                borderRadius: "md",
                px: "3",
                gap: "2",
                alignItems: "center",
                overflow: "hidden",
                _focusWithin: {
                    borderColor: "brand.focusRing",
                    boxShadow: "0 0 6px 1px var(--shadow-color)",
                    shadowColor: "brand.focusRing",
                    outline: "0",
                },
            },
            trigger: {
                p: "0",
                border: "none",
            },
            channelInput: {
                border: "none",
                outline: "none",
                color: "fg",
                fontSize: "sm",
                p: "0",
                _focus: {
                    outline: "none",
                    boxShadow: "none",
                    border: "none",
                },
                _focusVisible: {
                    outline: "none",
                    boxShadow: "none",
                    border: "none",
                },
            },
        },
    },
    radioGroup: {
        slots: ["root", "item", "itemText", "itemIndicator", "itemControl", "label", "indicator"],
        base: {
            itemText: {
                color: "fg",
            },
            itemControl: {
                borderColor: "border",
            },
        },
    },
    menu: {
        slots: ["content", "item"],
        base: {
            content: {
                bg: "bg.glass",
                backdropFilter: "blur(6px)",
                borderColor: "border.muted",
                borderWidth: "1px",
            },
            item: {
                fontSize: "xs",
                _highlighted: {
                    bg: "bg.subtle",
                },
            },
        },
    },
    drawer: {
        slots: ["content"],
        base: {
            content: {
                bg: "{colors.slate.surface.primary/60}",
                backdropFilter: "blur(6px)",
            },
        },
        variants: {
            size: {
                // Wider than the built-in xl (maxW 4xl / 896px); the standard width for
                // entity drawers. Merges with Chakra's default drawer sizes rather than
                // replacing them.
                lgx: {
                    content: {
                        maxW: "1040px",
                    },
                },
            },
        },
    },
    dialog: {
        slots: ["backdrop", "positioner", "content", "header", "body", "footer"],

        base: {
            backdrop: {
                bg: "glass",
                backdropFilter: "blur(6px)",
            },

            positioner: {
                alignItems: "center",
                justifyContent: "center",
            },

            content: {
                bg: "bg",
                borderColor: "dialog.border",
                borderWidth: "1px",
                boxShadow: "xl",
                display: "flex",
                flexDirection: "column",
                maxH: "80vh",
            },

            header: {
                px: 6,
                py: 4,
                fontSize: "lg",
                fontWeight: "bold",
                color: "dialog.title",
                flexShrink: 0,
                bg: "bg.glass",
            },

            body: {
                px: 6,
                py: 6,
                flex: 1,
                minHeight: "80px",
                overflowY: "auto",
                color: "fg",
            },

            footer: {
                px: 6,
                py: 4,
                display: "flex",
                justifyContent: "flex-end",
                gap: 3,
                flexShrink: 0,
                bg: "bg.glass",
            },
        },

        variants: {
            variant: {
                admin: {
                    content: {
                        width: "560px",
                    },
                },

                view: {
                    header: {
                        color: "fg.muted",
                    },
                },

                destructive: {
                    content: {
                        width: "420px",
                    },
                },

                content: {
                    body: {
                        px: 0,
                        py: 0,
                    },
                },
            },

            size: {
                sm: {
                    content: { width: "420px" },
                },
                md: {
                    content: { width: "560px" },
                },
                lg: {
                    content: { width: "720px" },
                },
                xl: {
                    content: { width: "800px" },
                },
                "2xl": {
                    content: { width: "1100px" },
                },
            },
        },

        // defaultVariants not supported when extending built-in recipes inline.
        // Components should specify variant/size explicitly.
    },
    alert: {
        slots: ["root", "content", "indicator", "title", "description"],
        variants: {
            variant: {
                subtle: {
                    root: {
                        bg: "bg.subtle",
                        borderWidth: "1px",
                        borderColor: "border",
                        borderInlineStartWidth: "3px",
                        borderInlineStartColor: "colorPalette.solid",
                    },
                },
            },
        },
    },
};
