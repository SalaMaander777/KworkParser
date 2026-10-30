package org.example.parser;

import org.example.db.KworkEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KworkResponseMapperTest {

    private static String page(String wants) {
        return "<script>window.stateData = {\"pagination\":{\"last_page\":7,\"data\":[" + wants + "]}};</script>";
    }

    @Test
    void mapsFieldsAndPagination() {
        String html = page("{\"id\":42,\"name\":\"Лендинг\",\"description\":\"d\",\"priceLimit\":\"50000.00\","
                + "\"category_id\":84,\"max_days\":10,\"date_create\":\"2026-08-29 18:33:39\","
                + "\"user\":{\"username\":\"bob\"},\"wantUserGetProfileUrl\":\"/user/bob\",\"kwork_count\":3}");

        PageResult result = new KworkResponseMapper().map(html, 1);

        assertEquals(7, result.lastPage());
        assertEquals(1, result.kworks().size());
        KworkEntity e = result.kworks().get(0);
        assertEquals(42L, e.getId());
        assertEquals(new BigDecimal("50000.00"), e.getPriceLimit());
        assertEquals(84L, e.getCategoryId());
        assertEquals(10, e.getMaxDays());
        assertEquals(LocalDateTime.of(2026, 8, 29, 18, 33, 39), e.getDateCreate());
        assertEquals("bob", e.getUsername());
        assertEquals(3, e.getKworkCount());
    }

    @Test
    void skipsOrdersWithoutId() {
        PageResult result = new KworkResponseMapper().map(page("{\"name\":\"x\"},{\"id\":5}"), 1);
        assertEquals(1, result.kworks().size());
        assertEquals(5L, result.kworks().get(0).getId());
    }

    @Test
    void badNumbersBecomeNullInsteadOfFailingPage() {
        PageResult result = new KworkResponseMapper().map(
                page("{\"id\":1,\"category_id\":\"abc\",\"max_days\":\"x\",\"date_create\":\"nope\"}"), 1);
        KworkEntity e = result.kworks().get(0);
        assertEquals(null, e.getCategoryId());
        assertEquals(null, e.getMaxDays());
        assertEquals(null, e.getDateCreate());
    }
}
