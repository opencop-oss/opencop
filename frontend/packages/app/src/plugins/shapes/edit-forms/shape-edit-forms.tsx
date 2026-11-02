// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { Angle, Arrow, Circle } from "@opencop/elements";
import type { DetailsEditFormProps } from "@opencop/contributions";
import { AngleInput } from "./AngleInput";
import { ArrowInput } from "./ArrowInput";
import { CircleInput } from "./CircleInput";

/**
 * The shapes' edit forms, each registered for its own kind. The guard narrows
 * the element the panel hands over; read-only when the operator may not change
 * the geometry.
 */

export function CircleEditForm({
    element,
    canEditGeometry,
}: DetailsEditFormProps): ReactElement | null {
    return Circle.is(element) ? (
        <CircleInput element={element} readOnly={!canEditGeometry} />
    ) : null;
}

export function ArrowEditForm({
    element,
    canEditGeometry,
}: DetailsEditFormProps): ReactElement | null {
    return Arrow.is(element) ? <ArrowInput element={element} readOnly={!canEditGeometry} /> : null;
}

export function AngleEditForm({
    element,
    canEditGeometry,
}: DetailsEditFormProps): ReactElement | null {
    return Angle.is(element) ? <AngleInput element={element} readOnly={!canEditGeometry} /> : null;
}
