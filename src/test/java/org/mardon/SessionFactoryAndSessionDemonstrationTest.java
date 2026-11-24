package org.mardon;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.mardon.configuration.HibernateConfigurationExample;
import org.mardon.database.Ticket;

public class SessionFactoryAndSessionDemonstrationTest {
    @Test
    void demonstrationTest(){
        // SessionFactory создается один раз на всё приложение и владеет общим L2-кешем
        SessionFactory sessionFactory = HibernateConfigurationExample.buildSessionFactory();

        Ticket t1, t2, t3;

        // --- ТРАНЗАКЦИЯ / СЕССИЯ 1 ---
        try (Session session1 = sessionFactory.openSession()) {
            // 1. Обращение к БД (L1 и L2 пусты). Запись загружается в L2 и L1.
            t1 = session1.find(Ticket.class, 10L);

            // 2. Попадание в L1-кеш (Persistence Context). SQL-запрос НЕ выполняется.
            t2 = session1.find(Ticket.class, 10L);

            // Identity Map гарантирует возврат той же самой ссылки в памяти внутри одной Session:
            assert t1 == t2; // true
        } // Здесь session1 закрывается -> L1-кеш полностью уничтожается.

        // --- ТРАНЗАКЦИЯ / СЕССИЯ 2 (другой поток / запрос) ---
        try (Session session2 = sessionFactory.openSession()) {
            // L1 пуст, но данные берутся из общего L2-кеша. SQL-запрос к БД НЕ выполняется.
            t3 = session2.find(Ticket.class, 10L);

            // L2 хранит не сам объект, а его данные, поэтому для новой сессии создается новый экземпляр:
            assert t1 != t3; // true (разные ссылки, но одинаковые значения полей)
        }
    }
}
