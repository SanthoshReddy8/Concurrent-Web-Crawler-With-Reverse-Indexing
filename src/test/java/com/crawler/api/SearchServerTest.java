package com.crawler.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchServerTest {

    @Test
    void parsesLimitValuesWithADefault() {
        assertEquals(5, SearchServer.parseLimit("5"));
        assertEquals(10, SearchServer.parseLimit(null));
        assertEquals(10, SearchServer.parseLimit(""));
        assertEquals(10, SearchServer.parseLimit("abc"));
    }
}
