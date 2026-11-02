// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQuery } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { GoToCoordinate } from "~/features/coordinates/GoToCoordinate";
import { locationSearchQueries } from "./location-search.queries";
import { LocationSearch } from "./LocationSearch";

/**
 * Falls back to coordinate-only search until the deployment reports a search-capable geocoder;
 * the coordinate box needs no network, so it holds the slot while the list loads.
 *
 * Gated by data, not a runtime feature: every user sees place search as soon as an administrator
 * activates a geocoder integration, and deactivating it is the off switch.
 *
 * No picker yet: the first search-capable geocoder in the server's order (integration label,
 * then geocoder label) is used, so a deployment with several only exposes that one.
 */
export function MapSearch(): ReactElement {
    const { data: geocoders } = useQuery(locationSearchQueries.geocoders());
    const geocoder = geocoders?.find((g) => g.operations.includes("SEARCH"));

    return geocoder ? <LocationSearch geocoder={geocoder} /> : <GoToCoordinate />;
}
