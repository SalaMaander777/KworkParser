package org.example.parser;

import org.example.Env;

import java.time.Duration;

/**
 * Настройки клиента. Раньше URL, user-agent и число попыток были
 * зашиты прямо в тело методов — это делало код не переиспользуемым
 * и не тестируемым. Теперь всё выносится сюда, с разумными дефолтами,
 * которые можно переопределить переменными окружения.
 */
public record KworkConfig(
        String baseUrl,
        String userAgent,
        Duration requestTimeout,
        Duration delayBetweenRequests,
        int maxRetries,
        Duration retryBackoff,
        int concurrency
) {
    public static KworkConfig defaultConfig() {
        return new KworkConfig(
                Env.get("KWORK_BASE_URL", "https://kwork.ru/projects"),
                Env.get("KWORK_USER_AGENT", "Mozilla/5.0 (compatible; KworkParserBot/1.0)"),
                Duration.ofSeconds(Env.getInt("KWORK_TIMEOUT_SEC", 10)),
                Duration.ofMillis(Env.getLong("KWORK_DELAY_MS", 800)),
                Env.getInt("KWORK_RETRIES", 3),
                Duration.ofSeconds(2),
                Math.max(1, Env.getInt("KWORK_CONCURRENCY", 4))
        );
    }
}
