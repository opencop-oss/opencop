// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, Icon } from "@chakra-ui/react";
import type { ReactNode, Ref } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { startCase } from "lodash";
import { useSidebar } from "./SidebarContext";
export const buttonHeight: number = 16;

export function ModuleButton({
    module,
    ref,
    ...rest
}: {
    module: { name: string; icon: ReactNode };
    ref?: Ref<HTMLAnchorElement>;
}) {
    const { moduleOpen } = useSidebar();
    const { t } = useTranslation();

    const label = t(`sidebar.${module.name}`, { defaultValue: startCase(module.name) });

    return (
        <Button
            asChild
            {...rest}
            aria-label={label}
            aria-current={moduleOpen === module.name ? "page" : undefined}
            padding={2}
            w="full"
            h={buttonHeight}
            flexDirection="column"
            alignItems="center"
            justifyItems="center"
            gap="1"
            variant="subtle"
            bg={"transparent"}
            color={moduleOpen === module.name ? "icon.active" : "fg"}
            _hover={{ bg: "bg.interactive" }}
        >
            <Link ref={ref} to={"/" + module.name}>
                <Icon
                    w="12"
                    height="6"
                    color={moduleOpen === module.name ? "icon.active" : "#64758B"}
                    filter={
                        moduleOpen === module.name
                            ? "drop-shadow(1px 1px 4px currentColor)"
                            : undefined
                    }
                >
                    {module.icon}
                </Icon>
            </Link>
        </Button>
    );
}
