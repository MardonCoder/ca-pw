package org.mardon.index;

import java.util.List;

public interface L2RegionIndex {
    IndexedCacheEntry get(long entityId, long now);
    void put(IndexedCacheEntry entry);
    boolean remove(long entityId);
    List<IndexedCacheEntry> batchResolve(long parentId, long now);
    long nextExpiration();
    int evictExpired(long now, int limit);
    int countParentEntries(long parentId);
    int size();
}