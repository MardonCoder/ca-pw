package org.mardon;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.mardon.configuration.HibernateConfigurationExample;
import org.mardon.database.Ticket;

public class SessionFactoryAndSessionDemonstrationTest {
    @Test
    void demonstrationTest(){
        // SessionFactory is created once per application and owns the shared L2 cache
        SessionFactory sessionFactory = HibernateConfigurationExample.buildSessionFactory();

        Ticket t1, t2, t3;

        // --- TRANSACTION / SESSION 1 ---
        try (Session session1 = sessionFactory.openSession()) {
            // 1. Database lookup (both L1 and L2 are empty). The record is loaded into L2 and L1.
            t1 = session1.find(Ticket.class, 10L);

            // 2. L1 cache (Persistence Context) hit. No SQL query is executed.
            t2 = session1.find(Ticket.class, 10L);

            // Identity Map guarantees returning the exact same object reference within a single Session:
            assert t1 == t2; // true
        } // session1 closes here -> L1 cache is completely destroyed.

        // --- TRANSACTION / SESSION 2 (another thread / request) ---
        try (Session session2 = sessionFactory.openSession()) {
            // L1 is empty, so data is fetched from the shared L2 cache. No SQL query is executed.
            t3 = session2.find(Ticket.class, 10L);

            // L2 stores disassembled state rather than the object itself, so a new instance is hydrated for the new session:
            assert t1 != t3; // true (different references, identical field values)
        }
    }
}
