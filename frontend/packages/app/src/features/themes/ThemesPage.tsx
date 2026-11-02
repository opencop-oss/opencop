// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { VStack } from "@chakra-ui/react";
import { ColorBrowserSection } from "./sections/ColorBrowserSection";
import { TypographySection } from "./sections/TypographySection";
import { ButtonSection } from "./sections/ButtonSection";
import { DialogSection } from "./sections/DialogSection";
import { TableSection } from "./sections/TableSection";
import { NotificationSection } from "./sections/NotificationSection";
import { InputSection } from "./sections/InputSection";
import { FeedbackSection } from "./sections/FeedbackSection";
import { LayoutSection } from "./sections/LayoutSection";

export function ThemesPage() {
    return (
        <VStack align="start" gap="12" w="full" py={6}>
            <ColorBrowserSection />
            <TypographySection />
            <ButtonSection />
            <DialogSection />
            <TableSection />
            <NotificationSection />
            <InputSection />
            <FeedbackSection />
            <LayoutSection />
        </VStack>
    );
}
