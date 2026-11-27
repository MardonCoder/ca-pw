package org.mardon.index;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AugmentedRBTreeTest {

    @Test
    void boundaryAndPruningTest() {
        IndexedL2Region region = new IndexedL2Region(16);

        // Matches Scheme 2 from Section 2.2
        region.put(new IndexedCacheEntry(1L, 10L, 110L, new Object[]{"A1", 1L}));
        region.put(new IndexedCacheEntry(1L, 11L, 130L, new Object[]{"A2", 1L}));
        region.put(new IndexedCacheEntry(1L, 12L, 140L, new Object[]{"A3", 1L}));
        region.put(new IndexedCacheEntry(1L, 13L, 210L, new Object[]{"A4", 1L}));
        region.put(new IndexedCacheEntry(2L, 20L, 180L, new Object[]{"B1", 2L}));
        region.put(new IndexedCacheEntry(2L, 21L, 240L, new Object[]{"B2", 2L}));
        region.put(new IndexedCacheEntry(2L, 22L, 260L, new Object[]{"B3", 2L}));
        region.markParentComplete(1L);
        region.verifyInvariants();

        assertEquals(110L, region.nextExpiration());
        assertEquals(4, region.countParentEntries(1L));
        assertEquals(3, region.countParentEntries(2L));

        List<IndexedCacheEntry> resolved = region.batchResolve(1L, 150L);
        assertEquals(1, resolved.size());
        assertEquals(13L, resolved.get(0).entityId());

        // Evict earliest 2 expired entries: (1, 10) at 110 and (1, 11) at 130
        assertEquals(2, region.evictExpired(150L, 2));
        region.verifyInvariants();
        assertFalse(region.isParentComplete(1L));
        assertEquals(140L, region.nextExpiration());
        assertEquals(2, region.countParentEntries(1L));
        assertNull(region.get(10L, 0L));
    }

    @Test
    void differentialFuzzTestAgainstOracle() {
        Random rnd = new Random(42L);
        IndexedL2Region subject = new IndexedL2Region(1024);
        HashOnlyRegion oracle = new HashOnlyRegion(1024);

        final int parentSpace = 40;
        final int entitySpace = 400;
        long now = 500L;

        for (int step = 1; step <= 25_000; step++) {
            int op = rnd.nextInt(100);
            if (op < 40) {
                // Insert / update TTL / reassign parent
                long p = rnd.nextInt(parentSpace);
                long c = rnd.nextInt(entitySpace);
                long exp = now - 200 + rnd.nextInt(800);
                IndexedCacheEntry entry = new IndexedCacheEntry(p, c, exp, new Object[]{"S-" + c, p});
                subject.put(entry);
                oracle.put(entry);
            } else if (op < 60) {
                // Selective remove
                long c = rnd.nextInt(entitySpace);
                assertEquals(oracle.remove(c), subject.remove(c));
            } else if (op < 75) {
                // BatchResolve comparison
                long p = rnd.nextInt(parentSpace);
                List<IndexedCacheEntry> actual = subject.batchResolve(p, now);
                List<IndexedCacheEntry> expected = oracle.batchResolve(p, now);
                expected.sort(Comparator.comparingLong(IndexedCacheEntry::entityId));
                assertEquals(expected, actual, "BatchResolve mismatch at step " + step);
            } else if (op < 85) {
                // CountParentEntries & NextExpiration
                long p = rnd.nextInt(parentSpace);
                assertEquals(oracle.countParentEntries(p), subject.countParentEntries(p));
                assertEquals(oracle.nextExpiration(), subject.nextExpiration());
            } else if (op < 95) {
                // EvictExpired: both must evict the same count when limit covers all expired
                // (for partial limit, oracle evicts in hash order while subject evicts oldest first,
                //  so test full eviction here to keep both states identical)
                int actualEvicted = subject.evictExpired(now, Integer.MAX_VALUE);
                int expectedEvicted = oracle.evictExpired(now, Integer.MAX_VALUE);
                assertEquals(expectedEvicted, actualEvicted);
            } else {
                now += rnd.nextInt(50);
            }

            assertEquals(oracle.size(), subject.size());
            if (step % 25 == 0) {
                subject.verifyInvariants();
            }
        }
        subject.verifyInvariants();
    }
}