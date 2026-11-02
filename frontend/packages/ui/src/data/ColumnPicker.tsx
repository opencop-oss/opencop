// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Checkbox, Popover, Stack } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { FaColumns } from "react-icons/fa";
import { SettingsPopover } from "../forms/SettingsPopover.tsx";

export interface ColumnPickerItem {
    id: string;
    label: string;
    visible: boolean;
}

export interface ColumnPickerProps {
    items: ColumnPickerItem[];
    onToggle: (id: string, visible: boolean) => void;
    /** Shows every column again; the button is omitted when absent. */
    onReset?: () => void;
}

/** Toolbar popover listing the hideable columns of a table as checkboxes. */
export function ColumnPicker({ items, onToggle, onReset }: ColumnPickerProps) {
    const { t } = useTranslation();
    return (
        <SettingsPopover
            title={t("resourceTable.columns")}
            placement="bottom-start"
            content={
                <Stack gap={2} align="flex-start">
                    {items.map((item) => (
                        <Checkbox.Root
                            key={item.id}
                            size="sm"
                            checked={item.visible}
                            onCheckedChange={(details) =>
                                onToggle(item.id, details.checked === true)
                            }
                        >
                            <Checkbox.HiddenInput />
                            <Checkbox.Control
                                borderColor="slate.icon.primary"
                                borderWidth="1.5px"
                            />
                            <Checkbox.Label fontSize="sm">{item.label}</Checkbox.Label>
                        </Checkbox.Root>
                    ))}
                    {onReset && (
                        <Button size="xs" variant="toolbar" onClick={onReset}>
                            {t("resourceTable.selectAllColumns")}
                        </Button>
                    )}
                </Stack>
            }
        >
            <Popover.Trigger asChild>
                <Button size="xs" variant="toolbar" aria-label={t("resourceTable.chooseColumns")}>
                    <FaColumns />
                    {t("resourceTable.columns")}
                </Button>
            </Popover.Trigger>
        </SettingsPopover>
    );
}
