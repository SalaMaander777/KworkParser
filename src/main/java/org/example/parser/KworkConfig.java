package org.example.parser;

import java.time.Duration;

/**
 * Настройки клиента. Раньше URL, user-agent и число попыток были
 * зашиты прямо в тело методов — это делало код не переиспользуемым
 * и не тестируемым. Теперь всё выносится сюда, с разумными дефолтами.
 */
public record KworkConfig(
        String baseUrl,
        String userAgent,
        Duration requestTimeout,
        Duration delayBetweenRequests,
        int maxRetries,
        Duration retryBackoff
) {
    public static KworkConfig defaultConfig() {
        return new KworkConfig(
                "https://kwork.ru/projects",
                "Mozilla/5.0 (compatible; KworkParserBot/1.0)",
                Duration.ofSeconds(10),
                Duration.ofMillis(800),
                3,
                Duration.ofSeconds(2)
        );
    }
}
