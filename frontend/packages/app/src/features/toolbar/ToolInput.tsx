// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import type {
    NumberStyleSpec,
    PatternStyleSpec,
    SelectStyleSpec,
    SliderStyleSpec,
    StyleSpec,
    SwatchStyleSpec,
    WeightStyleSpec,
} from "./specs";
import { IconButton, Popover, Portal, useDisclosure } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { useTranslation } from "react-i18next";
import { ToolSwatch } from "./inputs/ToolSwatch";
import { ToolSelect } from "./inputs/ToolSelect";
import { ToolSlider } from "./inputs/ToolSlider";
import { ToolNumber } from "./inputs/ToolNumber";
import { ToolWeightSelect } from "./inputs/ToolWeightSelect";
import { ToolPatternSelect } from "./inputs/ToolPatternSelect";
import { useToolDraft } from "./tool.hooks";

export interface ToolInputProps {
    spec: StyleSpec;
    toolKey: string;
}

export function ToolInput({ spec, toolKey }: ToolInputProps): ReactElement {
    // Subscribe to drawingElement changes to re-render when element becomes available
    useToolDraft();

    if (spec.input.type === "swatch") {
        return <ToolSwatch spec={spec as SwatchStyleSpec} toolKey={toolKey} />;
    }

    if (spec.input.type === "number") {
        return <ToolNumber spec={spec as NumberStyleSpec} toolKey={toolKey} />;
    }

    return <ToolPopoverInput spec={spec} toolKey={toolKey} />;
}

/** Popover input for slider, select, weight, and pattern types. */
function ToolPopoverInput({ spec, toolKey }: { spec: StyleSpec; toolKey: string }): ReactElement {
    const { open, onClose, onToggle } = useDisclosure();
    const { t } = useTranslation();
    const displayLabel = spec.labelKey
        ? t(spec.labelKey as never, { defaultValue: spec.label })
        : spec.label;

    function renderContent() {
        switch (spec.input.type) {
            case "weight":
                return <ToolWeightSelect spec={spec as WeightStyleSpec} toolKey={toolKey} />;
            case "pattern":
                return <ToolPatternSelect spec={spec as PatternStyleSpec} toolKey={toolKey} />;
            case "select":
                return <ToolSelect spec={spec as SelectStyleSpec} toolKey={toolKey} />;
            default:
                return <ToolSlider spec={spec as SliderStyleSpec} toolKey={toolKey} />;
        }
    }

    return (
        <Popover.Root
            open={open}
            // Controlled popover: Escape reaches us via onOpenChange, not internal
            // state. The interaction runtime's Escape ladder stands down while an
            // overlay like this one is open, so the popover must dismiss itself.
            onOpenChange={(d) => {
                if (!d.open) onClose();
            }}
            onInteractOutside={onClose}
            positioning={{ placement: "bottom-start" }}
        >
            <Popover.Trigger asChild>
                <IconButton
                    active={open ? "icon" : "iconHover"}
                    border="none"
                    aria-label={displayLabel}
                    onClick={onToggle}
                >
                    <Tooltip content={displayLabel}>{spec.icon && <spec.icon />}</Tooltip>
                </IconButton>
            </Popover.Trigger>
            <Portal>
                <Popover.Positioner>
                    <Popover.Content bg="slate.popover" w="fit-content" minW="0" p="0">
                        <Popover.Body p="1">{renderContent()}</Popover.Body>
                    </Popover.Content>
                </Popover.Positioner>
            </Portal>
        </Popover.Root>
    );
}
