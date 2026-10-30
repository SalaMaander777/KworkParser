package org.example.db;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public class KworkRepository {

    private static final Logger log = LoggerFactory.getLogger(KworkRepository.class);

    /** Должен совпадать с hibernate.jdbc.batch_size в hibernate.cfg.xml. */
    private static final int BATCH_SIZE = 30;

    public static void saveKwork(KworkEntity kwork){
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            Transaction tx = session.beginTransaction();
            try {
                session.persist(kwork);
                tx.commit();
            }catch (Exception e){
                tx.rollback();
                throw e;
            }
        }
    }
    public static KworkEntity findById(Long id){
        try(Session session = HibernateUtil.getSessionFactory().openSession()){
            return session.get(KworkEntity.class, id);
        }
    }

    /**
     * Сохраняет заказы пачками: каждая пачка в своей транзакции, чтобы одна плохая
     * запись не откатывала весь проход. Пачка, которая не сохранилась, повторяется
     * по одной записи, чтобы отбросить только виновника.
     */
    public static void saveAll(List<KworkEntity> allKworks){
        for (int from = 0; from < allKworks.size(); from += BATCH_SIZE) {
            List<KworkEntity> batch = allKworks.subList(from, Math.min(from + BATCH_SIZE, allKworks.size()));
            try {
                mergeInTransaction(batch);
            } catch (Exception batchError) {
                log.warn("Пачка с {} не сохранилась ({}), пробуем по одному", from, batchError.toString());
                for (KworkEntity kwork : batch) {
                    try {
                        mergeInTransaction(List.of(kwork));
                    } catch (Exception e) {
                        log.error("Заказ id={} не сохранён", kwork.getId(), e);
                    }
                }
            }
        }
    }

    private static void mergeInTransaction(List<KworkEntity> batch){
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                for (KworkEntity kwork : batch) {
                    session.merge(kwork);
                }
                tx.commit();
            } catch (Exception e){
                tx.rollback();
                throw e;
            }
        }
    }

}
