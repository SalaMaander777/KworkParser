package org.example.db;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;


public class KworkRepository {

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

    public static void saveAll(List<KworkEntity> allKworks){
        try(Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                for (int i = 0; i < allKworks.size(); i++){
                    session.merge(allKworks.get(i));
                    if ((i + 1) % BATCH_SIZE == 0){
                        session.flush();
                        session.clear();
                    }
                }
                tx.commit();
            } catch (Exception e){
                tx.rollback();
                throw e;
            }
        }
    }



}
