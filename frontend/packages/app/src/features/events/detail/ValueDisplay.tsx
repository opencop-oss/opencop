// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Editable } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useEventsEditingStore, useEventsEditingActions } from "../events-editing.store";
import { formatNumber } from "../helpers.util";

const editablePaths = [["attributes"]];

export function ValueDisplay({ path, value }: { path: string[]; value: unknown }) {
    const { t } = useTranslation();
    const overlay = useEventsEditingStore((s) => s.overlay);
    const { setField } = useEventsEditingActions();

    let defaultValue: string = t("events.notApplicable");
    const type: string = typeof value;

    const canEdit =
        overlay &&
        editablePaths.some((p) => {
            let editable: boolean = true;

            p.forEach((str, i) => {
                if (str !== path[i]) editable = false;
            });

            return editable;
        });

    switch (type) {
        case "boolean":
            defaultValue = value ? t("events.yes") : t("events.no");
            break;
        case "number":
            defaultValue = formatNumber(value as number);
            break;
        case "string":
            defaultValue = value as string;
    }

    return (
        <Editable.Root
            size={"xs" as "sm"}
            textAlign="end"
            w="full"
            colorPalette="fg"
            borderRadius="sm"
            color={canEdit ? "orange.300" : "fg"}
            value={defaultValue}
            disabled={!canEdit}
            activationMode="click"
            justifyContent="end"
            onValueChange={(e) => {
                setField(path, e.value);
            }}
        >
            <Editable.Preview color="white" textStyle="xs" />
            <Editable.Input focusRing="none" textStyle="xs" />
        </Editable.Root>
    );
}
