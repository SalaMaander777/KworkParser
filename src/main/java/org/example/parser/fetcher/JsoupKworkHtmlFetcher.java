package org.example.parser.fetcher;

import org.example.parser.KworkConfig;
import org.example.parser.KworkParsingException;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Реальная реализация фетчера. В отличие от исходного кода:
 *  - есть таймаут на запрос (иначе зависший коннект вешает весь пайплайн);
 *  - есть ретраи с задержкой на сетевые сбои (5xx, обрывы соединения);
 *  - ошибки не проглатываются: после исчерпания попыток бросается
 *    KworkParsingException с исходной причиной внутри.
 */
public class JsoupKworkHtmlFetcher implements KworkHtmlFetcher {

    private static final Logger log = LoggerFactory.getLogger(JsoupKworkHtmlFetcher.class);

    private final KworkConfig config;

    public JsoupKworkHtmlFetcher(KworkConfig config) {
        this.config = config;
    }

    @Override
    public String fetchPage(int pageNumber) throws KworkParsingException {
        String url = config.baseUrl() + "?a=1&page=" + pageNumber;
        IOException lastError = null;

        for (int attempt = 1; attempt <= config.maxRetries(); attempt++) {
            try {
                log.debug("Запрос страницы {} (попытка {}/{})", pageNumber, attempt, config.maxRetries());
                return Jsoup.connect(url)
                        .userAgent(config.userAgent())
                        .timeout((int) config.requestTimeout().toMillis())
                        .get()
                        .toString();
            } catch (IOException e) {
                lastError = e;
                log.warn("Не удалось получить страницу {} (попытка {}/{}): {}",
                        pageNumber, attempt, config.maxRetries(), e.getMessage());
                sleepQuietly(config.retryBackoff().toMillis() * attempt);
            }
        }

        throw new KworkParsingException(
                "Не удалось получить страницу " + pageNumber + " после " + config.maxRetries() + " попыток",
                lastError
        );
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
