package org.mardon.index;

import java.util.*;

public final class HashOnlyRegion implements L2RegionIndex {
    private final Map<Long, IndexedCacheEntry> primaryStore;

    public HashOnlyRegion(int expectedCapacity) {
        this.primaryStore = new HashMap<>(expectedCapacity);
    }

    @Override
    public IndexedCacheEntry get(long entityId, long now) {
        IndexedCacheEntry entry = primaryStore.get(entityId);
        return (entry != null && entry.expiryTime() > now) ? entry : null;
    }

    @Override
    public void put(IndexedCacheEntry entry) {
        primaryStore.put(entry.entityId(), entry);
    }

    @Override
    public boolean remove(long entityId) {
        return primaryStore.remove(entityId) != null;
    }

    @Override
    public List<IndexedCacheEntry> batchResolve(long parentId, long now) {
        List<IndexedCacheEntry> result = new ArrayList<>();
        for (IndexedCacheEntry e : primaryStore.values()) {
            if (e.parentId() == parentId && e.expiryTime() > now) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public long nextExpiration() {
        long min = Long.MAX_VALUE;
        for (IndexedCacheEntry e : primaryStore.values()) {
            if (e.expiryTime() < min) {
                min = e.expiryTime();
            }
        }
        return min;
    }

    @Override
    public int evictExpired(long now, int limit) {
        if (limit <= 0) return 0;
        int evicted = 0;
        Iterator<Map.Entry<Long, IndexedCacheEntry>> it = primaryStore.entrySet().iterator();
        while (it.hasNext() && evicted < limit) {
            if (it.next().getValue().expiryTime() <= now) {
                it.remove();
                evicted++;
            }
        }
        return evicted;
    }

    @Override
    public int countParentEntries(long parentId) {
        int count = 0;
        for (IndexedCacheEntry e : primaryStore.values()) {
            if (e.parentId() == parentId) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int size() {
        return primaryStore.size();
    }
}