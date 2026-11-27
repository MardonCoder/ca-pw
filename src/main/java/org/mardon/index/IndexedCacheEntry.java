package org.mardon.index;

public record IndexedCacheEntry(
        long parentId,
        long entityId,
        long expiryTime,
        Object[] decomposedState
) {}