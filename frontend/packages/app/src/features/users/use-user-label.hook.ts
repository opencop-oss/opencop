// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Text } from "@chakra-ui/react";
import { useQuery } from "@tanstack/react-query";
import { type ReactNode, createElement } from "react";
import { userQueries } from "./users.queries";

/**
 * Resolves a user id to that user's username for display.
 *
 * Returns `undefined` when there is no id, so a caller can drop the row entirely rather than
 * render a labelled blank. Falls back to the raw id while the lookup is in flight, or when the
 * user is no longer readable — an id on screen is more useful than an empty cell.
 *
 * Once the lookup settles without a username the id is styled, so an admin can tell an
 * unresolvable id from a real username instead of reading a bare UUID as an answer. While the
 * lookup is still in flight the id renders unstyled, so the text is never restyled mid-paint.
 *
 * `userQueries.byId` is already disabled for an empty id, so a null/undefined id costs no request.
 */
export function useUserLabel(userId: string | null | undefined): ReactNode | undefined {
    const { data, isPending } = useQuery(userQueries.byId(userId ?? ""));

    if (!userId) return undefined;
    if (data?.username) return data.username;
    if (isPending) return userId;

    return createElement(Text, { as: "span", fontFamily: "mono", color: "fg.muted" }, userId);
}
