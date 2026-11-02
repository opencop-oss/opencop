// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { z } from "zod";

export const mapperMetadataSchema = z.object({
    name: z.string().min(3, "mapperBuilder.validation.nameMin"),
    description: z.string().optional(),
});

export type MapperMetadataFormValues = z.infer<typeof mapperMetadataSchema>;
