package org.example;

import org.example.db.HibernateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SCRAPE_INTERVAL_MINUTES не задан или 0 — один проход и выход;
 * иначе проходы повторяются с этой паузой между окончанием одного и стартом следующего.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    static void main(String[] args) {
        KworkService service = KworkService.withDefaults();
        long intervalMinutes = Env.getLong("SCRAPE_INTERVAL_MINUTES", 1);

        if (intervalMinutes <= 0) {
            try {
                service.fetchAllKworks();
            } finally {
                HibernateUtil.shutdown();
            }
            return;
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            HibernateUtil.shutdown();
        }));
        log.info("Запуск по расписанию: каждые {} мин.", intervalMinutes);
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                service.fetchAllKworks();
            } catch (Exception e) {
                // исключение, вылетевшее из задачи, остановило бы все последующие запуски
                log.error("Сбой при обходе каталога, следующая попытка по расписанию", e);
            }
        }, 0, intervalMinutes, TimeUnit.MINUTES);
    }
}
