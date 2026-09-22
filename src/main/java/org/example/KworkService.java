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

import java.util.ArrayList;
import java.util.List;

/**
 * Публичная точка входа модуля.
 *
 * Главный баг исходного parseAllWorks: страница скачивалась ОДИН раз
 * (sendRequest(1)), а затем в цикле pagesAmount раз парсился один и тот же
 * HTML — то есть реально обходилась только первая страница, а результат
 * даже не возвращался из метода. Здесь это исправлено: на каждой итерации
 * действительно запрашивается своя страница, между запросами есть пауза
 * (чтобы не улететь в бан по частоте запросов), а метод возвращает
 * агрегированный список.
 */
public class KworkService {

    private static final Logger log = LoggerFactory.getLogger(KworkService.class);

    private final KworkHtmlFetcher fetcher;
    private final KworkResponseMapper mapper;
    private final KworkConfig config;


    public KworkService(KworkHtmlFetcher fetcher, KworkResponseMapper mapper, KworkConfig config) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.config = config;

    }

    public static KworkService withDefaults() {
        KworkConfig config = KworkConfig.defaultConfig();
        return new KworkService(new JsoupKworkHtmlFetcher(config), new KworkResponseMapper(), config);
    }

    /**
     * Обходит все страницы каталога заказов и возвращает объединённый список.
     */
    public List<KworkEntity> fetchAllKworks() throws KworkParsingException {

        String firstPageHtml = fetcher.fetchPage(1);
        PageResult firstPage = mapper.map(firstPageHtml, 1);
        List<KworkEntity> allKworks = new ArrayList<>(firstPage.kworks());

        int lastPage = firstPage.lastPage();
        log.info("Всего страниц пагинации: {}", lastPage);

        for (int page = 2; page <= lastPage; page++) {
            sleepBetweenRequests();
            String html = fetcher.fetchPage(page);
            PageResult result = mapper.map(html, page);
            allKworks.addAll(result.kworks());
            log.info("Страница {}/{}: получено {} заказов", page, lastPage, result.kworks().size());
        }

        KworkRepository.saveAll(allKworks);
        return allKworks;
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(config.delayBetweenRequests().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
