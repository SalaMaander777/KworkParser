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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Публичная точка входа модуля.
 *
 * Обходит страницы каталога заказов: первая страница определяет число
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
    private final int fastPages = Math.max(1, Env.getInt("KWORK_FAST_PAGES", 3));
    private final Duration fullSyncEvery = Duration.ofMinutes(Env.getLong("KWORK_FULL_SYNC_MINUTES", 15));
    /** Заказ закрывается, если его не видели дольше этого срока (считая от начала обхода): переживает сдвиг пагинации. */
    private final Duration missGrace = Duration.ofMinutes(Env.getLong("KWORK_MISS_GRACE_MINUTES", 20));
    private final int retentionDays = Env.getInt("KWORK_RETENTION_DAYS", 7);
    private final double minSeenRatio = Env.getInt("KWORK_MIN_SEEN_PERCENT", 50) / 100.0;
    private static final int MIN_ACTIVE_FOR_GUARD = 20;
    private volatile long lastFullSyncAt;

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
     * Один тик планировщика. Быстрый проход (первые {@code KWORK_FAST_PAGES} страниц, новые заказы
     * всегда сверху) идёт каждый раз, чтобы бот получал свежее без задержки. Раз в
     * {@code KWORK_FULL_SYNC_MINUTES} делается полный обход, и только он закрывает пропавшие с сайта заказы.
     */
    public void sync() {
        long now = System.currentTimeMillis();
        boolean full = lastFullSyncAt == 0 || now - lastFullSyncAt >= fullSyncEvery.toMillis();
        if (full && crawl(Integer.MAX_VALUE)) {
            lastFullSyncAt = now;
        } else if (!full) {
            crawl(fastPages);
        }
    }

    /** Полный обход каталога с закрытием пропавших заказов (разовый запуск). */
    public List<KworkEntity> fetchAllKworks() {
        return crawl(Integer.MAX_VALUE) ? lastCrawl : List.of();
    }

    private volatile List<KworkEntity> lastCrawl = List.of();

    /**
     * Обходит страницы 1..maxPages, сохраняет заказы в БД.
     * Страницы, которые не удалось получить, логируются и пропускаются.
     * @return true, если обход был полным (все страницы каталога получены без ошибок)
     */
    private boolean crawl(int maxPages) {
        LocalDateTime crawlStart = KworkRepository.dbNow();
        PageResult firstPage = getFirstPageResult();
        int lastPage = firstPage.lastPage();
        int pages = Math.min(lastPage, maxPages);
        boolean complete = pages == lastPage;
        log.info("Страниц в каталоге: {}, обходим: {}", lastPage, pages);
        List<KworkEntity> kworks = new ArrayList<>(firstPage.kworks());

        List<CompletableFuture<PageResult>> futures = new ArrayList<>();
        int failed = 0;
        ExecutorService executor = Executors.newFixedThreadPool(config.concurrency());
        try {
            for (int page = 2; page <= pages; page++) {
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
            log.warn("Не удалось получить {} из {} страниц", failed, pages - 1);
        }
        // пагинация сдвигается, пока идёт обход: один заказ может прийти дважды — оставляем один
        kworks = new ArrayList<>(kworks.stream()
                .collect(Collectors.toMap(KworkEntity::getId, k -> k, (a, b) -> a, LinkedHashMap::new))
                .values());
        log.info("Получено заказов: {}", kworks.size());
        KworkRepository.upsertAll(kworks);
        lastCrawl = kworks;

        boolean full = complete && failed == 0;
        if (full) {
            closeMissing(kworks.size(), crawlStart);
        }
        return full;
    }

    /**
     * Закрывает заказы, пропавшие с сайта. Защита от ложного массового закрытия: если обход вернул
     * подозрительно мало заказов (сайт отдал заглушку/капчу, часть каталога не отдалась),
     * ничего не трогаем.
     */
    private void closeMissing(int seen, LocalDateTime crawlStart) {
        long activeBefore = KworkRepository.countActive();
        if (activeBefore > MIN_ACTIVE_FOR_GUARD && seen < activeBefore * minSeenRatio) {
            log.warn("Закрытие пропавших пропущено: в обходе {} заказов при {} активных в базе", seen, activeBefore);
            return;
        }
        int closed = KworkRepository.deactivateMissing(crawlStart.minus(missGrace));
        int purged = KworkRepository.purgeClosed(retentionDays);
        if (closed > 0 || purged > 0) {
            log.info("Закрыто пропавших с сайта: {}, удалено старых закрытых: {}", closed, purged);
        }
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
