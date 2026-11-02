// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, IconButton, Menu, Portal } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { LuChevronDown } from "react-icons/lu";
import type { CoordFormat } from "@opencop/utils";

const FORMATS: CoordFormat[] = ["DD", "DMS", "DDM", "MGRS", "UTM"];

export interface CoordFormatSelectProps {
    /** The selected format. */
    value: CoordFormat;
    onChange: (format: CoordFormat) => void;
    /** `statusBar`: a compact icon button; `inline`: an outlined button beside an input. */
    variant?: "statusBar" | "inline";
}

/** A menu for choosing a coordinate format (DD, DMS, DDM, MGRS, UTM). */
export function CoordFormatSelect({
    value: format,
    onChange: setFormat,
    variant = "statusBar",
}: CoordFormatSelectProps): ReactElement {
    const { t } = useTranslation();
    const isInline = variant === "inline";

    if (isInline) {
        return (
            <Menu.Root>
                <Menu.Trigger asChild>
                    <Button
                        type="button"
                        variant="outline"
                        borderColor="border"
                        size="md"
                        minW="16"
                        px="3"
                        fontFamily="mono"
                        fontWeight="semibold"
                        justifyContent="space-between"
                    >
                        {format}
                        <LuChevronDown />
                    </Button>
                </Menu.Trigger>
                <Portal>
                    <Menu.Positioner>
                        <Menu.Content>
                            {FORMATS.map((f) => (
                                <Menu.Item
                                    key={f}
                                    value={f}
                                    onClick={() => setFormat(f)}
                                    fontFamily="mono"
                                    fontWeight={f === format ? "bold" : "normal"}
                                    color={f === format ? "active" : "fg"}
                                >
                                    {f}
                                </Menu.Item>
                            ))}
                        </Menu.Content>
                    </Menu.Positioner>
                </Portal>
            </Menu.Root>
        );
    }

    return (
        <Menu.Root>
            <Menu.Trigger asChild>
                <IconButton
                    active="iconHover"
                    aria-label={t("coordinates.format")}
                    fontFamily="mono"
                    fontSize="2xs"
                    fontWeight="bold"
                    px="2"
                    minW="0"
                >
                    {format}
                </IconButton>
            </Menu.Trigger>
            <Portal>
                <Menu.Positioner>
                    <Menu.Content>
                        {FORMATS.map((f) => (
                            <Menu.Item
                                key={f}
                                value={f}
                                onClick={() => setFormat(f)}
                                fontWeight={f === format ? "bold" : "normal"}
                                color={f === format ? "active" : "fg"}
                            >
                                {f}
                            </Menu.Item>
                        ))}
                    </Menu.Content>
                </Menu.Positioner>
            </Portal>
        </Menu.Root>
    );
}
