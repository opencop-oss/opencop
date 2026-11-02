// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useMemo, type ReactNode } from "react";
import { sortBy } from "lodash";
import { FilterFieldsProvider, type FilterFields } from "@opencop/ui";
import {
    attributeFieldId,
    attributeNameOf,
    attributeNameOfId,
    isAttributeName,
} from "@opencop/contributions/validation";
import { useRegisteredFilterFields } from "~/features/contributions/filter-field-registry";
import { useCustomAttributesStore } from "./custom-attributes.store";
import { attributeField, DISPLAY_NAME_FIELD, KIND_FIELD } from "./host-fields.util";

/**
 * Provides what filter rules can be about: the host's own fields, the fields
 * owners contributed (`registerFilterField`), and the operator's custom attributes.
 */
export function HostFilterFieldsProvider({ children }: { children: ReactNode }) {
    const registered = useRegisteredFilterFields();
    const names = useCustomAttributesStore((s) => s.names);
    const { addAttribute, removeAttribute } = useCustomAttributesStore((s) => s.actions);

    const value = useMemo<FilterFields>(
        () => ({
            fields: sortBy(
                [KIND_FIELD, DISPLAY_NAME_FIELD, ...registered, ...names.map(attributeField)],
                (field) => field.order,
            ),
            defaultFieldId: DISPLAY_NAME_FIELD.id,
            // A saved filter can name an attribute this browser never saw: custom
            // attributes live in local storage, not on the filter.
            fieldFor: (predicate) => {
                const name = attributeNameOf(predicate.path);
                return name === undefined ? undefined : attributeField(name);
            },
            createField: (name) => {
                addAttribute(name);
                return attributeFieldId(name);
            },
            removeField: (id) => {
                const name = attributeNameOfId(id);
                if (name !== undefined) removeAttribute(name);
            },
            isValidFieldName: isAttributeName,
        }),
        [registered, names, addAttribute, removeAttribute],
    );

    return <FilterFieldsProvider value={value}>{children}</FilterFieldsProvider>;
}
