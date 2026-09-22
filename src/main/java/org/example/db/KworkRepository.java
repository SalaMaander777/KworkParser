package org.example.db;

import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;


public class KworkRepository {

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
                int batchSize = 12;
                for (int i = 0; i < allKworks.size(); i++){
                    session.persist(allKworks.get(i));
                    if (i % batchSize == 0 && i > 0){
                        session.flush();
                        session.clear();
                    }
                }
            } catch (Exception e){
                tx.rollback();
                throw e;
            }
        }
    }



}
