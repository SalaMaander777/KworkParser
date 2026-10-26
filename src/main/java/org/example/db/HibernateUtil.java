package org.example.db;

import org.example.Env;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HibernateUtil {
    private static final Logger log = LoggerFactory.getLogger(HibernateUtil.class);
    private static final SessionFactory sessionFactory = buildSessionFactory();

    public static SessionFactory buildSessionFactory(){
        try {
            Configuration configuration = new Configuration().configure();
            // Значения из hibernate.cfg.xml служат дефолтами, переменные окружения их переопределяют.
            override(configuration, "hibernate.connection.url", "DB_URL");
            override(configuration, "hibernate.connection.username", "DB_USER");
            override(configuration, "hibernate.connection.password", "DB_PASSWORD");
            return configuration
                    .addAnnotatedClass(KworkEntity.class)
                    .buildSessionFactory();
        }catch (Throwable ex){
            log.error("Не удалось создать SessionFactory", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static void override(Configuration configuration, String property, String envName) {
        String value = Env.get(envName, null);
        if (value != null) {
            configuration.setProperty(property, value);
        }
    }

    public static SessionFactory getSessionFactory(){
        return sessionFactory;
    }

    public static void shutdown(){
        if (sessionFactory.isOpen()) {
            sessionFactory.close();
        }
    }
}
