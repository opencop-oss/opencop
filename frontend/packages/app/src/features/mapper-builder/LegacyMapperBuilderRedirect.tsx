// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { Navigate, useLocation, useParams } from "react-router";
import {
    ENTITY_ADMIN_PATH,
    MAPPER_BUILDER_PATH,
} from "~/features/data-sources/data-resources.util";

/** Where the builder lived before it moved under the mappers admin page. */
export const LEGACY_MAPPER_BUILDER_PATH = "/admin/data-sources/mappers";

/** The mappers admin page's path while it also listed delimiters. */
export const LEGACY_MAPPERS_PAGE_PATH = "/admin/mappers-delimiters";

/**
 * Forwards an old builder URL (`/new` or `/:id/edit`) to the same builder route under
 * {@link MAPPER_BUILDER_PATH}, keeping the query string (e.g. `?from=` for duplicates) so saved
 * links keep working.
 */
export function LegacyMapperBuilderRedirect(): ReactElement {
    const { id } = useParams();
    const { search } = useLocation();
    const pathname = id ? `${MAPPER_BUILDER_PATH}/${id}/edit` : `${MAPPER_BUILDER_PATH}/new`;
    return <Navigate replace to={{ pathname, search }} />;
}

/**
 * Forwards a URL under {@link LEGACY_MAPPERS_PAGE_PATH} to the mappers page, keeping the query
 * string so mapper drawer links survive. A drawer link to anything else (an old delimiter link)
 * has no drawer to open here, so its `id` / `type` are dropped and it lands on the plain list.
 * Its old builder URLs go through {@link LegacyMapperBuilderRedirect} instead.
 */
export function LegacyMappersPageRedirect(): ReactElement {
    const { search } = useLocation();
    const params = new URLSearchParams(search);
    if (params.get("type") !== "mapper") {
        params.delete("id");
        params.delete("type");
    }
    const next = params.toString();
    return (
        <Navigate
            replace
            to={{ pathname: ENTITY_ADMIN_PATH.mapper, search: next ? `?${next}` : "" }}
        />
    );
}
