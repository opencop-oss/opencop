// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ButtonGroup, Flex, Pagination as ChakraPagination, useToken } from "@chakra-ui/react";
import { FaEllipsisH } from "react-icons/fa";
import { LuChevronLeft, LuChevronRight } from "react-icons/lu";

export function Pagination({
    count,
    pageOpen,
    setPageOpen,
}: {
    count: number;
    pageOpen: number;
    setPageOpen: (value: number) => void;
}) {
    const [fg, hover] = useToken("colors", ["fg", "pagination.hover"]);
    return (
        <ChakraPagination.Root
            mt={2}
            count={count}
            pageSize={1}
            page={pageOpen}
            onPageChange={(e) => setPageOpen(e.page)}
        >
            <ButtonGroup size="sm" variant="ghost" _hover={{ outline: "none", border: "none" }}>
                <ChakraPagination.PrevTrigger asChild>
                    <Flex
                        width="35px"
                        height="35px"
                        color={pageOpen === 1 ? hover : fg}
                        alignItems="center"
                        justifyContent="center"
                        _hover={{
                            cursor: "pointer",
                            bgColor: "bg.elevatedLight",
                        }}
                        onClick={(e) => e.preventDefault()}
                    >
                        <LuChevronLeft />
                    </Flex>
                </ChakraPagination.PrevTrigger>

                <ChakraPagination.Context>
                    {({ pages }) =>
                        pages.map((page, i) =>
                            page.type === "page" ? (
                                <Flex
                                    key={page.value}
                                    width="35px"
                                    height="35px"
                                    color={page.value === pageOpen ? "active" : fg}
                                    alignItems="center"
                                    justifyContent="center"
                                    _hover={{
                                        cursor: "pointer",
                                        bgColor: "bg.elevatedLight",
                                    }}
                                    onClick={() => {
                                        setPageOpen(page.value);
                                    }}
                                >
                                    {page.value}
                                </Flex>
                            ) : (
                                // eslint-disable-next-line @eslint-react/no-array-index-key -- ellipsis markers
                                <Flex key={`ellipsis-${i}`}>
                                    <FaEllipsisH size={10} />
                                </Flex>
                            ),
                        )
                    }
                </ChakraPagination.Context>

                <ChakraPagination.NextTrigger asChild>
                    <Flex
                        width="35px"
                        height="35px"
                        alignItems="center"
                        justifyContent="center"
                        _hover={{
                            cursor: "pointer",

                            bgColor: "bg.elevatedLight",
                        }}
                        onClick={(e) => e.preventDefault()}
                    >
                        <LuChevronRight />
                    </Flex>
                </ChakraPagination.NextTrigger>
            </ButtonGroup>
        </ChakraPagination.Root>
    );
}
