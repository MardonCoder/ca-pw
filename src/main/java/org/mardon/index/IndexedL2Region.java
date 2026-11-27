package org.mardon.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IndexedL2Region implements L2RegionIndex {
    private final Map<Long, IndexedCacheEntry> primaryStore;
    private final AugmentedRBTree secondaryIndex;
    private final Map<Long, ParentMeta> parentMeta;

    public IndexedL2Region(int expectedCapacity) {
        this.primaryStore = new HashMap<>(expectedCapacity);
        this.secondaryIndex = new AugmentedRBTree();
        this.parentMeta = new HashMap<>();
    }

    @Override
    public IndexedCacheEntry get(long entityId, long now) {
        IndexedCacheEntry entry = primaryStore.get(entityId);
        if (entry == null || entry.expiryTime() <= now) {
            return null;
        }
        return entry;
    }

    @Override
    public void put(IndexedCacheEntry entry) {
        IndexedCacheEntry existing = primaryStore.put(entry.entityId(), entry);
        if (existing != null && existing.parentId() != entry.parentId()) {
            secondaryIndex.delete(existing.parentId(), existing.entityId());
            invalidateParentMeta(existing.parentId());
        }
        secondaryIndex.insertOrUpdate(entry);
    }

    @Override
    public boolean remove(long entityId) {
        IndexedCacheEntry removed = primaryStore.remove(entityId);
        if (removed == null) {
            return false;
        }
        secondaryIndex.delete(removed.parentId(), removed.entityId());
        invalidateParentMeta(removed.parentId());
        return true;
    }

    @Override
    public List<IndexedCacheEntry> batchResolve(long parentId, long now) {
        List<IndexedCacheEntry> result = new ArrayList<>();
        secondaryIndex.batchResolve(parentId, now, result);
        return result;
    }

    @Override
    public long nextExpiration() {
        return secondaryIndex.nextExpiration();
    }

    @Override
    public int evictExpired(long now, int limit) {
        int evicted = 0;
        while (evicted < limit && secondaryIndex.nextExpiration() <= now) {
            IndexedCacheEntry removed = secondaryIndex.deleteEarliestExpired(now);
            if (removed == null) break;
            primaryStore.remove(removed.entityId());
            invalidateParentMeta(removed.parentId());
            evicted++;
        }
        return evicted;
    }

    @Override
    public int countParentEntries(long parentId) {
        return secondaryIndex.countParentEntries(parentId);
    }

    @Override
    public int size() {
        return primaryStore.size();
    }

    public void markParentComplete(long parentId) {
        parentMeta.computeIfAbsent(parentId, id -> new ParentMeta(true)).markComplete();
    }

    public boolean isParentComplete(long parentId) {
        ParentMeta meta = parentMeta.get(parentId);
        return meta != null && meta.isComplete();
    }

    private void invalidateParentMeta(long parentId) {
        ParentMeta meta = parentMeta.get(parentId);
        if (meta != null) {
            meta.invalidate();
        }
    }

    public void verifyInvariants() {
        secondaryIndex.verifyInvariants();
        if (primaryStore.size() != secondaryIndex.size()) {
            throw new AssertionError("Primary store and secondary index size mismatch");
        }
    }
}