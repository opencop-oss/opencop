// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ComponentType } from "react";
import type { Element } from "@opencop/elements";

/*
 * Every details-panel contribution names the one element type it is for
 * (`Element.type`, e.g. `"circle"`). Two types that share a component register
 * it twice.
 */

/** What a header type icon receives. */
export interface DetailsTypeIconProps {
    readonly color: string;
    /** Set when the icon stands in for an image that failed to load. */
    readonly ariaLabel?: string;
}

/**
 * How the details panel's header presents one element type. One value per type:
 * each field resolves from the type's registration, else the host's default (a
 * circle for a point geometry, a rectangle otherwise; an `"active"` frame). A
 * second registration for a type is refused.
 */
export interface DetailsHeaderContribution {
    /** The element type. */
    readonly type: string;
    /** The glyph shown when the element has no thumbnail. */
    readonly typeIcon?: ComponentType<DetailsTypeIconProps>;
    /** The icon box's frame: `"active"` (the accent border) or `"none"`. */
    readonly iconFrame?: "active" | "none";
}

/** A position the details panel shows in its Coordinates row, with the row's label. */
export interface DetailsPosition {
    /** `[lng, lat]`. */
    readonly position: readonly [number, number];
    /** Translation key of the row's label. */
    readonly labelKey: string;
}

/**
 * The position the details panel shows for one element type. One value per type;
 * a second registration is refused. The host's default shows a point geometry's
 * position as "Position", and no row otherwise.
 */
export interface DetailsPositionContribution {
    /** The element type. */
    readonly type: string;
    /** The element's position, or `null` for no row. Called on every render: keep it cheap. */
    position(element: Element): DetailsPosition | null;
}

/** What an edit form receives. */
export interface DetailsEditFormProps {
    /** The live element being edited; changes to it show on the map at once. */
    readonly element: Element;
    /** Whether the operator may change the geometry; inputs are read-only when not. */
    readonly canEditGeometry: boolean;
    /** Whether the operator may change the properties. */
    readonly canEditProperties: boolean;
}

/**
 * Inputs the details panel's edit form shows for one element type, at the
 * geometry position. Many may apply; they are listed by `order`. When none
 * applies to a point geometry, the host shows its position input.
 *
 * A form writes the live element, as the host's inputs do. Cancel restores the
 * geometry the host knows how to restore; there is no validation or dirty state
 * a form joins.
 */
export interface DetailsEditFormContribution {
    /** Unique among the type's edit forms. */
    readonly id: string;
    /** The element type. */
    readonly type: string;
    readonly order: number;
    readonly Component: ComponentType<DetailsEditFormProps>;
}

/** What an information section receives. */
export interface DetailsInfoSectionProps {
    /**
     * The live element. It changes in place as updates arrive; subscribe to its
     * geometry (`useGeometrySubscription` in `@opencop/ingest`) to show changes
     * between the panel's own refreshes.
     */
    readonly element: Element;
}

/**
 * A section in the details panel's Information tab, for one element type.
 * Many may apply; the tab lists them among its own by `order`: Identity 10,
 * Coordinates 20, Timestamps 40, Motion 50, Contacts 60, Attributes 70. The
 * component draws its own heading, and may render nothing.
 *
 * In historical mode the Information tab is the only tab shown.
 */
export interface DetailsInfoSectionContribution {
    /** Unique among the type's information sections. */
    readonly id: string;
    /** The element type. */
    readonly type: string;
    readonly order: number;
    readonly Component: ComponentType<DetailsInfoSectionProps>;
}
