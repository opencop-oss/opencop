// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useQueries, useQuery } from "@tanstack/react-query";
import type { Banner } from "@opencop/api";
import { bannerQueries } from "./banners.queries";

function isActiveBanner(banner: Banner): boolean {
    const now = new Date();
    return banner.enabled && new Date(banner.startsAt) <= now && new Date(banner.endsAt) >= now;
}

export function useActiveBanners(): Banner[] {
    const { data: allBanners = [] } = useQuery(bannerQueries.list());

    const activeIds = allBanners.filter(isActiveBanner).map((b) => b.id);

    const detailResults = useQueries({
        queries: activeIds.map((id) => bannerQueries.detail(id)),
    });

    return detailResults
        .map((r) => r.data)
        .filter((b): b is Banner => !!b)
        .sort((a, b) => a.priority - b.priority);
}
