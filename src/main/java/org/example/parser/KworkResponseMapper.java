package org.example.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.db.KworkEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Превращает JSON из window.stateData в KworkEntity.
 * ObjectMapper без кастомных модулей потокобезопасен для чтения,
 * так что один инстанс можно спокойно шарить.
 *
 * В отличие от версии с DTO (Kwork), тут числовые и датовые поля
 * приходится действительно парсить, а не просто копировать как String:
 * priceLimit -> BigDecimal, category_id/max_days -> Long/Integer,
 * date_create -> LocalDateTime.
 *
 * Форматы подтверждены реальным ответом Kwork: priceLimit="50000.00"
 * (простое число без валюты), categoryId=84 и maxDays=10 (обычные целые),
 * dateCreate="2026-08-29 18:33:39" ("yyyy-MM-dd HH:mm:ss"). ISO и
 * unix-timestamp оставлены как запасные варианты на случай другого
 * эндпоинта; при полном несовпадении формата пишется warning и поле
 * остаётся null, а не роняет всю страницу.
 */
public class KworkResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(KworkResponseMapper.class);

    private static final DateTimeFormatter SQL_STYLE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper mapper = new ObjectMapper();

    public PageResult map(String html, int requestedPage) throws KworkParsingException {
        String json = EmbeddedJsonExtractor.extract(html, "window.stateData");

        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new KworkParsingException("Не удалось разобрать JSON stateData", e);
        }

        JsonNode pagination = root.path("pagination");
        JsonNode wantsArray = pagination.path("data");
        if (wantsArray.isMissingNode() || !wantsArray.isArray()) {
            wantsArray = root.path("wants");
        }

        List<KworkEntity> kworks = new ArrayList<>();
        for (JsonNode node : wantsArray) {
            // без id (asLong() дал бы 0) или названия это не заказ, а мусор в ответе — в базу не пускаем
            if (node.path("id").asLong() <= 0 || node.path("name").asText("").isBlank()) {
                log.warn("Страница {}: пропущен элемент без id/названия", requestedPage);
                continue;
            }
            kworks.add(toEntity(node, requestedPage));
        }

        int lastPage = pagination.path("last_page").asInt(requestedPage);
        return new PageResult(kworks, requestedPage, lastPage);
    }

    private KworkEntity toEntity(JsonNode node, int page) {
        JsonNode user = node.path("user");

        KworkEntity entity = new KworkEntity();
        entity.setId(node.path("id").asLong());
        entity.setName(node.path("name").asText(null));
        entity.setDescription(node.path("description").asText(null));
        entity.setPriceLimit(parseBigDecimal(node.path("priceLimit"), page, node));
        entity.setCategoryId(parseLong(node.path("category_id"), page, node));
        entity.setMaxDays(parseInteger(node.path("max_days"), page, node));
        entity.setDateCreate(parseDateTime(node.path("date_create"), page, node));
        entity.setUsername(user.path("username").asText(null));
        entity.setUserProfileUrl(node.path("wantUserGetProfileUrl").asText(null));
        entity.setKworkCount(node.path("kwork_count").asInt());
        return entity;
    }

    private BigDecimal parseBigDecimal(JsonNode field, int page, JsonNode node) {
        String raw = field.isMissingNode() || field.isNull() ? null : field.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            // На случай если приходит что-то вроде "10 000 ₽" или с пробелами-разделителями.
            String cleaned = raw.replaceAll("[^0-9.,-]", "").replace(",", ".");
            return cleaned.isBlank() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Страница {}: не удалось распарсить priceLimit='{}' (id={})",
                    page, raw, node.path("id").asText());
            return null;
        }
    }

    private Long parseLong(JsonNode field, int page, JsonNode node) {
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        if (field.isNumber()) {
            return field.asLong();
        }
        String raw = field.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Страница {}: не удалось распарсить category_id='{}' (id={})",
                    page, raw, node.path("id").asText());
            return null;
        }
    }

    private Integer parseInteger(JsonNode field, int page, JsonNode node) {
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }
        if (field.isNumber()) {
            return field.asInt();
        }
        String raw = field.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Страница {}: не удалось распарсить max_days='{}' (id={})",
                    page, raw, node.path("id").asText());
            return null;
        }
    }

    private LocalDateTime parseDateTime(JsonNode field, int page, JsonNode node) {
        if (field.isMissingNode() || field.isNull()) {
            return null;
        }

        // Unix timestamp (секунды) - JSON-числом.
        if (field.isNumber()) {
            try {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(field.asLong()), ZoneId.systemDefault());
            } catch (Exception e) {
                log.warn("Страница {}: не удалось распарсить date_create как timestamp='{}' (id={})",
                        page, field.asText(), node.path("id").asText());
                return null;
            }
        }

        String raw = field.asText(null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();

        // 1) Подтверждено реальным ответом Kwork: "2026-08-29 18:33:39".
        try {
            return LocalDateTime.parse(raw, SQL_STYLE);
        } catch (DateTimeParseException ignored) {
            // пробуем следующий формат
        }

        // 2) ISO-8601 на случай другого эндпоинта: "2024-06-01T10:23:45"
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // пробуем следующий формат
        }

        // 3) unix timestamp строкой, напр. "1717233825"
        try {
            long epochSeconds = Long.parseLong(raw);
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
        } catch (NumberFormatException ignored) {
            // не timestamp
        }

        log.warn("Страница {}: неизвестный формат date_create='{}' (id={})",
                page, raw, node.path("id").asText());
        return null;
    }
}