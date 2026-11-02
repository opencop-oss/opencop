// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createContext, use, type ReactNode } from "react";
import type { Predicate } from "./predicate.model";
import type { FilterField } from "./filter-field.model";

/**
 * What a filter can be about, as the host provides it: the fields, and the operator's
 * own custom ones. Builders and read-only views read it here instead of importing the
 * host or a field's owner.
 */
export interface FilterFields {
    /** Every field, in picker order. */
    readonly fields: readonly FilterField[];
    /** The field a newly added row starts on. */
    readonly defaultFieldId?: string;
    /**
     * The field for a saved predicate no listed field holds — an attribute this browser
     * has never seen — or `undefined`, leaving a fallback row.
     */
    readonly fieldFor?: (predicate: Predicate) => FilterField | undefined;
    /** Adds a custom field the operator named and returns its id. Absent: no custom fields. */
    readonly createField?: (name: string) => string;
    /** Removes a field marked `removable`. */
    readonly removeField?: (id: string) => void;
    /** Whether `createField` accepts a name. */
    readonly isValidFieldName?: (name: string) => boolean;
}

const FilterFieldsContext = createContext<FilterFields | null>(null);

/** Provides the filter fields to everything below it. */
export function FilterFieldsProvider({
    value,
    children,
}: {
    value: FilterFields;
    children: ReactNode;
}) {
    return <FilterFieldsContext value={value}>{children}</FilterFieldsContext>;
}

/** The filter fields. Throws outside a `FilterFieldsProvider`. */
export function useFilterFields(): FilterFields {
    const fields = use(FilterFieldsContext);
    if (!fields) throw new Error("useFilterFields must be used within a FilterFieldsProvider");
    return fields;
}
