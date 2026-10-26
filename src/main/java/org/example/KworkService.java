package org.example;

import org.example.db.KworkEntity;
import org.example.db.KworkRepository;
import org.example.parser.KworkConfig;
import org.example.parser.KworkParsingException;
import org.example.parser.KworkResponseMapper;
import org.example.parser.PageResult;
import org.example.parser.fetcher.JsoupKworkHtmlFetcher;
import org.example.parser.fetcher.KworkHtmlFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Публичная точка входа модуля.
 *
 * Обходит все страницы каталога заказов: первая страница определяет число
 * страниц, остальные скачиваются в ограниченном пуле потоков
 * ({@link KworkConfig#concurrency()}), причём каждый поток выдерживает паузу
 * {@link KworkConfig#delayBetweenRequests()} перед запросом, чтобы не улететь
 * в бан по частоте запросов. Результат сохраняется в БД.
 */
public class KworkService {

    private static final Logger log = LoggerFactory.getLogger(KworkService.class);

    private final KworkHtmlFetcher fetcher;
    private final KworkResponseMapper mapper;
    private final KworkConfig config;
    /** Если задан, HTML первой страницы сохраняется в эту папку (для отладки). */
    private final Path debugDir;

    public KworkService(KworkHtmlFetcher fetcher, KworkResponseMapper mapper, KworkConfig config) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.config = config;
        String dir = Env.get("KWORK_DEBUG_DIR", null);
        this.debugDir = dir == null ? null : Path.of(dir);
    }

    public static KworkService withDefaults() {
        KworkConfig config = KworkConfig.defaultConfig();
        return new KworkService(new JsoupKworkHtmlFetcher(config), new KworkResponseMapper(), config);
    }

    /**
     * Обходит все страницы каталога, сохраняет заказы в БД и возвращает их.
     * Страницы, которые не удалось получить, логируются и пропускаются.
     */
    public List<KworkEntity> fetchAllKworks() {
        PageResult firstPage = getFirstPageResult();
        int lastPage = firstPage.lastPage();
        log.info("Всего страниц пагинации: {}", lastPage);
        List<KworkEntity> kworks = new ArrayList<>(firstPage.kworks());

        List<CompletableFuture<PageResult>> futures = new ArrayList<>();
        int failed = 0;
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency());
        try {
            for (int page = 2; page <= lastPage; page++) {
                int currentPage = page;
                futures.add(CompletableFuture
                        .supplyAsync(() -> {
                            sleepBetweenRequests();
                            return mapper.map(fetcher.fetchPage(currentPage), currentPage);
                        }, executor)
                        .exceptionally(ex -> {
                            log.error("Ошибка на странице {}: {}", currentPage, ex.toString());
                            return null;
                        }));
            }
            for (CompletableFuture<PageResult> future : futures) {
                PageResult result = future.join();
                if (result == null) {
                    failed++;
                    continue;
                }
                kworks.addAll(result.kworks());
            }
        } finally {
            executor.shutdown();
        }

        if (failed > 0) {
            log.warn("Не удалось получить {} из {} страниц", failed, lastPage - 1);
        }
        log.info("Получено заказов: {}", kworks.size());
        KworkRepository.saveAll(kworks);
        return kworks;
    }

    private PageResult getFirstPageResult() {
        String firstPageHtml = fetcher.fetchPage(1);
        if (debugDir != null) {
            saveHtml(firstPageHtml, debugDir.resolve("kwork-page-1.html"));
        }
        return mapper.map(firstPageHtml, 1);
    }

    private void saveHtml(String html, Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Не удалось сохранить HTML в {}", path, e);
        }
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(config.delayBetweenRequests().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
