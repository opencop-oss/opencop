// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { FilterField } from "@opencop/ui";
import {
    attributeFieldId,
    attributePath,
    HOST_FILTER_FIELDS,
} from "@opencop/contributions/validation";

/** Every object's kind. */
export const KIND_FIELD: FilterField = {
    ...HOST_FILTER_FIELDS.kind,
    labelKey: "filter.fields.kind",
    order: 10,
};

/** Every object's display name. A new filter row starts on it. */
export const DISPLAY_NAME_FIELD: FilterField = {
    ...HOST_FILTER_FIELDS.displayName,
    labelKey: "filter.fields.displayName",
    order: 20,
};

/** Custom attributes follow every other field, in the order they were added. */
const ATTRIBUTE_ORDER = 1000;

/** A custom attribute as a field, labelled by its name. */
export function attributeField(name: string, index = 0): FilterField {
    return {
        id: attributeFieldId(name),
        labelText: name,
        path: attributePath(name),
        order: ATTRIBUTE_ORDER + index,
        removable: true,
    };
}
