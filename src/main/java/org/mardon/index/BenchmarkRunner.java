package org.mardon.index;

import java.util.*;

public final class BenchmarkRunner {
    private static final int WARMUP_BATCHES = 5;
    private static final int MEASURED_BATCHES = 15;
    private static final long NOW = 1_000_000L;
    private static long sink = 0L; // Dead Code Elimination guard

    public static void main(String[] args) {
        System.out.printf("JVM: %s (%s), OS: %s (%s)%n",
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"));

        prewarmJVM();
        System.out.println("JIT pre-warmup complete.\n");

        runScenario1_BatchResolveValid();
        runScenario2_BatchResolveExpiredPruning();
        runScenario3a_EvictExpiredNone();
        runScenario3b_EvictExpiredSparse();
        runScenario4_CountParentEntries();
        runScenario5_PointOpsAndOverhead();

        System.out.println("\nBlackhole sink = " + sink);
    }

    // Triggers HotSpot C2 compilation across all 3 implementations before timing starts
    private static void prewarmJVM() {
        List<IndexedCacheEntry> data = generateDataset(2_000, 1L, 100, false, 0.0, 999L);
        List<IndexedCacheEntry> expired = generateExpiredList(50, 50_000L, 998L);

        HashOnlyRegion hash = populate(new HashOnlyRegion(3_000), data);
        StandardRBTreeRegion std = populate(new StandardRBTreeRegion(3_000), data);
        IndexedL2Region aug = populate(new IndexedL2Region(3_000), data);

        L2RegionIndex[] regions = {hash, std, aug};
        for (int round = 0; round < 300; round++) {
            for (L2RegionIndex r : regions) {
                for (int i = 0; i < 20; i++) {
                    IndexedCacheEntry e = r.get(1L + (i % 100), NOW);
                    if (e != null) sink += e.entityId();
                }
                sink += r.batchResolve(1L, NOW).size();
                sink += r.countParentEntries(1L);
                sink += r.nextExpiration();
                sink += r.evictExpired(NOW, 100);
                for (IndexedCacheEntry ex : expired) r.put(ex);
                sink += r.evictExpired(NOW, 100);
                IndexedCacheEntry tmp = new IndexedCacheEntry(2L, 900_000L + round, NOW + 500_000L, new Object[]{"W", 2L});
                r.put(tmp);
                if (r.remove(tmp.entityId())) sink++;
            }
        }
    }

    // Scenario 1: BatchResolve (k = 200 valid tickets)
    private static void runScenario1_BatchResolveValid() {
        System.out.println("=== Scenario 1: BatchResolve (k=200 valid) [median us] ===");
        System.out.println("n,HashOnly_us,StandardRB_us,AugmentedRB_us");
        int[] sizes = {1_000, 10_000, 50_000, 100_000, 500_000};

        for (int n : sizes) {
            List<IndexedCacheEntry> data = generateDataset(n, 1L, 200, false, 0.0, 101L);
            HashOnlyRegion hash = populate(new HashOnlyRegion(n), data);
            StandardRBTreeRegion std = populate(new StandardRBTreeRegion(n), data);
            IndexedL2Region aug = populate(new IndexedL2Region(n), data);

            double tHash = medianMicrosBatched(() -> sink += hash.batchResolve(1L, NOW).size());
            double tStd = medianMicrosBatched(() -> sink += std.batchResolve(1L, NOW).size());
            double tAug = medianMicrosBatched(() -> sink += aug.batchResolve(1L, NOW).size());

            System.out.printf(Locale.US, "%d,%.3f,%.3f,%.3f%n", n, tHash, tStd, tAug);
        }
        System.out.println();
    }

    // Scenario 2: BatchResolve pruning (n = 100k, all k tickets of parent 1 expired; others valid)
    private static void runScenario2_BatchResolveExpiredPruning() {
        System.out.println("=== Scenario 2: BatchResolve Pruning (n=100k, all k expired) [median us] ===");
        System.out.println("k,HashOnly_us,StandardRB_us,AugmentedRB_us");
        int[] ks = {50, 200, 1_000, 5_000};
        final int n = 100_000;

        for (int k : ks) {
            List<IndexedCacheEntry> data = generateDataset(n, 1L, k, true, 0.0, 202L);
            HashOnlyRegion hash = populate(new HashOnlyRegion(n), data);
            StandardRBTreeRegion std = populate(new StandardRBTreeRegion(n), data);
            IndexedL2Region aug = populate(new IndexedL2Region(n), data);

            double tHash = medianMicrosBatched(() -> sink += hash.batchResolve(1L, NOW).size());
            double tStd = medianMicrosBatched(() -> sink += std.batchResolve(1L, NOW).size());
            double tAug = medianMicrosBatched(() -> sink += aug.batchResolve(1L, NOW).size());

            System.out.printf(Locale.US, "%d,%.3f,%.3f,%.3f%n", k, tHash, tStd, tAug);
        }
        System.out.println();
    }

    // Scenario 3a: EvictExpired(now, limit=100) when 0 entries are expired (periodic cleanup check)
    private static void runScenario3a_EvictExpiredNone() {
        System.out.println("=== Scenario 3a: EvictExpired (0 expired, limit=100) [median us] ===");
        System.out.println("n,HashOnly_us,StandardRB_us,AugmentedRB_us");
        int[] sizes = {1_000, 10_000, 50_000, 100_000, 500_000};

        for (int n : sizes) {
            List<IndexedCacheEntry> data = generateDataset(n, -1L, 0, false, 0.0, 301L);
            HashOnlyRegion hash = populate(new HashOnlyRegion(n), data);
            StandardRBTreeRegion std = populate(new StandardRBTreeRegion(n), data);
            IndexedL2Region aug = populate(new IndexedL2Region(n), data);

            double tHash = medianMicrosBatched(() -> sink += hash.evictExpired(NOW, 100));
            double tStd = medianMicrosBatched(() -> sink += std.evictExpired(NOW, 100));
            double tAug = medianMicrosBatched(() -> sink += aug.evictExpired(NOW, 100));

            System.out.printf(Locale.US, "%d,%.4f,%.4f,%.4f%n", n, tHash, tStd, tAug);
        }
        System.out.println();
    }

    // Scenario 3b: EvictExpired(now, limit=100) with exactly m = 50 expired records in cache
    private static void runScenario3b_EvictExpiredSparse() {
        System.out.println("=== Scenario 3b: EvictExpired Sparse (m=50 expired, limit=100) [median us] ===");
        System.out.println("n,HashOnly_us,StandardRB_us,AugmentedRB_us");
        int[] sizes = {1_000, 10_000, 50_000, 100_000, 500_000};
        final int expiredCount = 50;
        final int limit = 100;

        for (int n : sizes) {
            List<IndexedCacheEntry> validData = generateDataset(n - expiredCount, -1L, 0, false, 0.0, 303L);
            List<IndexedCacheEntry> expiredData = generateExpiredList(expiredCount, n + 100_000L, 304L);

            HashOnlyRegion hash = populate(new HashOnlyRegion(n + 64), validData);
            StandardRBTreeRegion std = populate(new StandardRBTreeRegion(n + 64), validData);
            IndexedL2Region aug = populate(new IndexedL2Region(n + 64), validData);

            double tHash = medianEvictSparse(hash, expiredData, limit);
            double tStd = medianEvictSparse(std, expiredData, limit);
            double tAug = medianEvictSparse(aug, expiredData, limit);

            System.out.printf(Locale.US, "%d,%.3f,%.3f,%.3f%n", n, tHash, tStd, tAug);
        }
        System.out.println();
    }

    // Scenario 4: CountParentEntries(parentId) with k = 500
    private static void runScenario4_CountParentEntries() {
        System.out.println("=== Scenario 4: CountParentEntries (k=500) [median us] ===");
        System.out.println("n,HashOnly_us,StandardRB_us,AugmentedRB_us");
        int[] sizes = {1_000, 10_000, 50_000, 100_000, 500_000};

        for (int n : sizes) {
            List<IndexedCacheEntry> data = generateDataset(n, 1L, 500, false, 0.0, 404L);
            HashOnlyRegion hash = populate(new HashOnlyRegion(n), data);
            StandardRBTreeRegion std = populate(new StandardRBTreeRegion(n), data);
            IndexedL2Region aug = populate(new IndexedL2Region(n), data);

            double tHash = medianMicrosBatched(() -> sink += hash.countParentEntries(1L));
            double tStd = medianMicrosBatched(() -> sink += std.countParentEntries(1L));
            double tAug = medianMicrosBatched(() -> sink += aug.countParentEntries(1L));

            System.out.printf(Locale.US, "%d,%.4f,%.4f,%.4f%n", n, tHash, tStd, tAug);
        }
        System.out.println();
    }

    // Scenario 5: Maintenance overhead (get, put, remove) in ns/op
    private static void runScenario5_PointOpsAndOverhead() {
        System.out.println("=== Scenario 5: Point Ops & Index Overhead [median ns/op] ===");
        System.out.println("n,Hash_get_ns,Aug_get_ns,Hash_put_ns,Aug_put_ns,Hash_rem_ns,Aug_rem_ns");
        int[] sizes = {1_000, 10_000, 50_000, 100_000, 500_000};
        final int batch = 1_000;

        for (int n : sizes) {
            List<IndexedCacheEntry> base = generateDataset(n, -1L, 0, false, 0.0, 505L);
            List<IndexedCacheEntry> extra = new ArrayList<>(batch);
            Random rnd = new Random(606L);
            for (int i = 0; i < batch; i++) {
                long id = n + 10_000L + i;
                long p = 1L + rnd.nextInt(Math.max(10, n / 50));
                extra.add(new IndexedCacheEntry(p, id, NOW + 600_000L, new Object[]{"X", p}));
            }

            long[] lookupIds = new long[batch];
            for (int i = 0; i < batch; i++) {
                lookupIds[i] = base.get(rnd.nextInt(n)).entityId();
            }

            HashOnlyRegion hash = populate(new HashOnlyRegion(n + batch), base);
            IndexedL2Region aug = populate(new IndexedL2Region(n + batch), base);

            double hashGet = medianMicrosBatched(() -> {
                for (long id : lookupIds) {
                    IndexedCacheEntry e = hash.get(id, NOW);
                    if (e != null) sink += e.entityId();
                }
            }) * 1_000.0 / batch;

            double augGet = medianMicrosBatched(() -> {
                for (long id : lookupIds) {
                    IndexedCacheEntry e = aug.get(id, NOW);
                    if (e != null) sink += e.entityId();
                }
            }) * 1_000.0 / batch;

            double[] hashPutRem = measurePutAndRemove(hash, extra);
            double[] augPutRem = measurePutAndRemove(aug, extra);

            System.out.printf(Locale.US, "%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f%n",
                    n, hashGet, augGet, hashPutRem[0], augPutRem[0], hashPutRem[1], augPutRem[1]);
        }
    }

    private static double medianMicrosBatched(Runnable action) {
        // Calibrate inner batch size so each measured sample lasts ~2 ms (eliminates 41.67 ns clock jitter)
        long calStart = System.nanoTime();
        for (int i = 0; i < 5; i++) action.run();
        long calNs = System.nanoTime() - calStart;

        int innerBatch = (calNs < 500)
                ? 20_000
                : (int) Math.max(1L, Math.min(20_000L, (2_000_000L * 5L) / calNs));

        for (int w = 0; w < WARMUP_BATCHES; w++) {
            for (int i = 0; i < innerBatch; i++) action.run();
        }

        long[] samples = new long[MEASURED_BATCHES];
        for (int m = 0; m < MEASURED_BATCHES; m++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < innerBatch; i++) action.run();
            samples[m] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        return samples[MEASURED_BATCHES / 2] / (innerBatch * 1_000.0);
    }

    private static double medianEvictSparse(L2RegionIndex region, List<IndexedCacheEntry> expired, int limit) {
        int warmup = 8;
        int measured = 15;
        long[] samples = new long[measured];

        for (int i = 0; i < warmup + measured; i++) {
            for (IndexedCacheEntry e : expired) region.put(e);

            long t0 = System.nanoTime();
            int evicted = region.evictExpired(NOW, limit);
            long t1 = System.nanoTime();
            sink += evicted;

            if (i >= warmup) {
                samples[i - warmup] = t1 - t0;
            }
        }
        Arrays.sort(samples);
        return samples[measured / 2] / 1_000.0;
    }

    private static double[] measurePutAndRemove(L2RegionIndex region, List<IndexedCacheEntry> batch) {
        int warmup = 10;
        int measured = 15;
        long[] putTimes = new long[measured];
        long[] remTimes = new long[measured];

        for (int i = 0; i < warmup + measured; i++) {
            long t0 = System.nanoTime();
            for (IndexedCacheEntry e : batch) region.put(e);
            long t1 = System.nanoTime();
            for (IndexedCacheEntry e : batch) {
                if (region.remove(e.entityId())) sink++;
            }
            long t2 = System.nanoTime();

            if (i >= warmup) {
                putTimes[i - warmup] = t1 - t0;
                remTimes[i - warmup] = t2 - t1;
            }
        }
        Arrays.sort(putTimes);
        Arrays.sort(remTimes);
        return new double[]{
                putTimes[measured / 2] / (double) batch.size(),
                remTimes[measured / 2] / (double) batch.size()
        };
    }

    private static List<IndexedCacheEntry> generateExpiredList(int count, long startId, long seed) {
        Random rnd = new Random(seed);
        List<IndexedCacheEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long p = 2L + rnd.nextInt(500);
            long exp = NOW - 100_000L + rnd.nextInt(99_000);
            list.add(new IndexedCacheEntry(p, startId + i, exp, new Object[]{"EXP-" + i, p}));
        }
        return list;
    }

    private static List<IndexedCacheEntry> generateDataset(
            int n, long targetParent, int targetK, boolean targetExpired, double expiredFraction, long seed
    ) {
        Random rnd = new Random(seed);
        List<IndexedCacheEntry> list = new ArrayList<>(n);
        long nextId = 1L;

        for (int i = 0; i < targetK; i++) {
            long exp = targetExpired
                    ? (NOW - 10_000L + rnd.nextInt(9_000))
                    : (NOW + 60_000L + rnd.nextInt(540_000));
            list.add(new IndexedCacheEntry(targetParent, nextId++, exp, new Object[]{"T-" + nextId, targetParent}));
        }

        int otherParents = Math.max(10, (n - targetK) / 50);
        int remaining = n - targetK;
        int expiredTarget = (int) Math.round(remaining * expiredFraction);

        for (int i = 0; i < remaining; i++) {
            long p = 2L + rnd.nextInt(otherParents);
            if (p == targetParent) p++;
            long exp = (i < expiredTarget)
                    ? (NOW - 100_000L + rnd.nextInt(99_000))
                    : (NOW + 60_000L + rnd.nextInt(540_000));
            list.add(new IndexedCacheEntry(p, nextId++, exp, new Object[]{"S-" + nextId, p}));
        }

        Collections.shuffle(list, rnd);
        return list;
    }

    private static <T extends L2RegionIndex> T populate(T region, List<IndexedCacheEntry> data) {
        for (IndexedCacheEntry e : data) region.put(e);
        return region;
    }
}