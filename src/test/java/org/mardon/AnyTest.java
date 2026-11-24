package org.mardon;

public class AnyTest {
    // 1. Entity storage structure in the Entity Cache (a flat array instead of an object graph)
    record EntityKey(Class<?> entityClass, Long entityId) {}

    record CacheEntry(
            Object[] decomposedState // e.g., ["VIP-12", 1L] -> the 'seat' field value and only the parent ID (event_id)
    ) {}

    // 2. Storage structure for a @OneToMany association in the Collection Region
    record CollectionKey(
            String role,   // Fully qualified role name: "com.example.Event.tickets"
            Long parentId  // Parent ID (event_id)
    ) {}

    record CollectionCacheEntry(
            Long[] childIds // Only an array of child primary keys: [10L, 11L, 12L]
    ) {}
}
