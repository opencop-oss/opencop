// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { FaArrowsUpDown, FaArrowDownLong, FaArrowUpLong } from "react-icons/fa6";
import type { SortOrder } from "./filter-panel.store";

export function SortIcon({ sort }: { sort: SortOrder }) {
    if (sort === "name-asc") return <FaArrowDownLong size={12} />;
    if (sort === "name-desc") return <FaArrowUpLong size={12} />;
    return <FaArrowsUpDown size={12} />;
}
