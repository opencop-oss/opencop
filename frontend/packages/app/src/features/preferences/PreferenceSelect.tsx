// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { OcSelect } from "@opencop/ui";
import { PreferenceRow } from "./PreferenceRow";

interface SelectItem {
    label: string;
    value: string;
}

interface PreferenceSelectProps {
    label: string;
    description?: string;
    items: SelectItem[];
    value: string;
    onValueChange: (value: string) => void;
    alignItems?: "center" | "start";
    disabled?: boolean;
}

export function PreferenceSelect({
    label,
    description,
    items,
    value,
    onValueChange,
    alignItems = "center",
    disabled = false,
}: PreferenceSelectProps) {
    return (
        <PreferenceRow label={label} description={description} alignItems={alignItems}>
            <OcSelect
                items={items}
                value={value}
                onValueChange={onValueChange}
                size="sm"
                variant="outline"
                disabled={disabled}
            />
        </PreferenceRow>
    );
}
