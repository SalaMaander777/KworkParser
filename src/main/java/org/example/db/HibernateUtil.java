package org.example.db;

import org.example.KworkService;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateUtil {
    private static final SessionFactory sessionFactory = buildSessionFactory();
    private static final Logger log = LoggerFactory.getLogger(KworkService.class);

    public static SessionFactory buildSessionFactory(){
        try {
            return new Configuration()
                    .configure()
                    .addAnnotatedClass(KworkEntity.class)
                    .buildSessionFactory();
        }catch (Throwable ex){
            log.warn(String.valueOf(ex));
            throw new ExceptionInInitializerError(ex);
        }
    }
    public static SessionFactory getSessionFactory(){
        return sessionFactory;
    }

    public static void shutdown(){
        sessionFactory.close();
    }
}
