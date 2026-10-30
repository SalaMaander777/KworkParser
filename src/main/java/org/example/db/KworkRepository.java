package org.example.db;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Жизненный цикл заказа в таблице kwork:
 * upsert (видим на сайте) -> deactivateMissing (пропал с сайта) -> purgeClosed (удаление через retention).
 * Все отметки времени берутся из now() БД, чтобы не зависеть от часов парсера и бота.
 */
public class KworkRepository {

    private static final int BATCH_SIZE = 100;

    private static final String UPSERT = """
            INSERT INTO kwork (kwork_order_id, kwork_name, kwork_description, kwork_price_limit, category_id,
                               max_days, creation_date, username, user_profile, kwork_count,
                               first_seen_at, last_seen_at, is_active, closed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), true, NULL)
            ON CONFLICT (kwork_order_id) DO UPDATE SET
                kwork_name = EXCLUDED.kwork_name,
                kwork_description = EXCLUDED.kwork_description,
                kwork_price_limit = EXCLUDED.kwork_price_limit,
                category_id = EXCLUDED.category_id,
                max_days = EXCLUDED.max_days,
                creation_date = EXCLUDED.creation_date,
                username = EXCLUDED.username,
                user_profile = EXCLUDED.user_profile,
                kwork_count = EXCLUDED.kwork_count,
                last_seen_at = now(),
                is_active = true,
                closed_at = NULL
            """;

    public static KworkEntity findById(Long id){
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return session.get(KworkEntity.class, id);
        }
    }

    /**
     * Вставляет новые заказы и обновляет существующие, отмечая их «виденными сейчас».
     * first_seen_at сохраняется с первой вставки; вернувшийся на сайт заказ снова становится активным.
     * Один батч INSERT ... ON CONFLICT вместо SELECT+INSERT/UPDATE на каждый заказ.
     */
    public static void upsertAll(List<KworkEntity> kworks){
        if (kworks.isEmpty()) {
            return;
        }
        inTransaction(session -> session.doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(UPSERT)) {
                int n = 0;
                for (KworkEntity k : kworks) {
                    ps.setLong(1, k.getId());
                    ps.setString(2, k.getName());
                    ps.setString(3, k.getDescription());
                    ps.setBigDecimal(4, k.getPriceLimit());
                    ps.setObject(5, k.getCategoryId());
                    ps.setObject(6, k.getMaxDays());
                    ps.setTimestamp(7, k.getDateCreate() == null ? null : Timestamp.valueOf(k.getDateCreate()));
                    ps.setString(8, k.getUsername());
                    ps.setString(9, k.getUserProfileUrl());
                    ps.setInt(10, k.getKworkCount());
                    ps.addBatch();
                    if (++n % BATCH_SIZE == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
        }));
    }

    /** Число заказов, которые сейчас считаются активными. */
    public static long countActive(){
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createNativeQuery("SELECT count(*) FROM kwork WHERE is_active", Long.class).getSingleResult();
        }
    }

    /** Текущее время БД: от него отсчитывается «виден ли заказ в этом обходе». */
    public static LocalDateTime dbNow(){
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createNativeQuery("SELECT now()::timestamp", LocalDateTime.class).getSingleResult();
        }
    }

    /**
     * Помечает закрытыми активные заказы, которых не видели с момента since.
     * Вызывать только после полного успешного обхода каталога.
     * @return сколько заказов закрыто
     */
    public static int deactivateMissing(LocalDateTime since){
        int[] result = new int[1];
        inTransaction(session -> result[0] = session.createNativeMutationQuery("""
                UPDATE kwork SET is_active = false, closed_at = now()
                WHERE is_active AND last_seen_at < :since
                """).setParameter("since", since).executeUpdate());
        return result[0];
    }

    /** Физически удаляет заказы, закрытые более retentionDays дней назад. */
    public static int purgeClosed(int retentionDays){
        return executeUpdate("""
                DELETE FROM kwork
                WHERE NOT is_active AND closed_at < now() - make_interval(days => :days)
                """, "days", retentionDays);
    }

    private static int executeUpdate(String sql, String param, int value){
        int[] result = new int[1];
        inTransaction(session -> result[0] = session.createNativeMutationQuery(sql).setParameter(param, value).executeUpdate());
        return result[0];
    }

    private static void inTransaction(java.util.function.Consumer<Session> work){
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                work.accept(session);
                tx.commit();
            } catch (Exception e){
                tx.rollback();
                throw e;
            }
        }
    }
}
