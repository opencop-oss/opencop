// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { PointGeometry, type Element } from "@opencop/elements";
import { useDetailsEditForms } from "~/features/contributions/details-registry";
import type { Editability } from "~/features/map/editability";
import { PointInput } from "./PointInput";

function hasPointGeometry(element: Element): element is Element<PointGeometry> {
    return element.geometry instanceof PointGeometry;
}

/**
 * The edit form's geometry inputs: the forms contributed for the element's
 * kind, by order. When none applies to a point geometry, the host's position
 * input. Every input is read-only when the operator may not change the geometry.
 */
export function GeometryEditForms({
    element,
    editability,
}: {
    element: Element;
    editability: Editability;
}): ReactElement | null {
    const forms = useDetailsEditForms(element);
    if (forms.length === 0) {
        return hasPointGeometry(element) ? (
            <PointInput element={element} readOnly={!editability.canEditGeometry} />
        ) : null;
    }
    return (
        <>
            {forms.map(({ id, Component }) => (
                <Component
                    key={id}
                    element={element}
                    canEditGeometry={editability.canEditGeometry}
                    canEditProperties={editability.canEditProperties}
                />
            ))}
        </>
    );
}
